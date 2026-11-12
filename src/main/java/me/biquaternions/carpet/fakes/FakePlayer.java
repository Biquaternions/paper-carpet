package me.biquaternions.carpet.fakes;

import com.mojang.authlib.GameProfile;
import io.papermc.paper.util.KeepAlive;
import lombok.Getter;
import me.biquaternions.carpet.CarpetServer;
import me.biquaternions.carpet.CarpetSettings;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.OldUsersConverter;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.Vec3;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * <p>A server side player without a client, port of fabric-carpet's {@code EntityPlayerMPFake}.</p>
 *
 * <p>Fake players are regular {@link ServerPlayer players} for Paper and plugins: they fire join and quit events, appear
 * in {@code Bukkit.getOnlinePlayers()} and load chunks.</p>
 */
@SuppressWarnings("EntityConstructor")
public class FakePlayer extends ServerPlayer {

    private static final Set<String> SPAWNING = new HashSet<>();

    @Getter
    private final PlayerActionPack actionPack;
    public final boolean isAShadow;

    /**
     * Spawns a fake player.
     *
     * @return {@code true} if it was successful, {@code false} if it couldn't spawn due to the player not existing in Mojang servers
     */
    public static boolean createFake(String username, MinecraftServer server, Vec3 pos, double yaw, double pitch, ResourceKey<Level> dimensionId, GameType gamemode, boolean flying) {
        ServerLevel worldIn = server.getLevel(dimensionId);
        if (worldIn == null) {
            return false;
        }
        server.services().nameToIdCache().resolveOfflineUsers(false);
        UUID uuid = OldUsersConverter.convertMobOwnerIfNecessary(server, username);
        if (uuid == null && CarpetSettings.allowSpawningOfflinePlayers) {
            server.services().nameToIdCache().resolveOfflineUsers(server.isDedicatedServer() && server.usesAuthentication());
            uuid = UUIDUtil.createOfflinePlayerUUID(username);
        }
        if (uuid == null) {
            return false; // no uuid, no player
        }
        GameProfile gameprofile = new GameProfile(uuid, username);

        // We need to mark this player as spawning so that we do not
        // try to spawn another player with the name while the profile
        // is being fetched - preventing multiple players spawning
        String name = gameprofile.name();
        SPAWNING.add(name);

        fetchGameProfile(server, gameprofile.id()).whenCompleteAsync((p, t) -> {
            // Always remove the name, even if exception occurs
            SPAWNING.remove(name);
            GameProfile current = (t != null || p == null || p.name().isEmpty()) ? gameprofile : p;
            try {
                spawn(server, worldIn, current, pos, yaw, pitch, gamemode, flying);
            } catch (Exception e) {
                CarpetServer.LOG.severe("Failed to spawn fake player " + name + ": " + e);
            }
        }, server);
        return true;
    }

    private static void spawn(MinecraftServer server, ServerLevel worldIn, GameProfile profile, Vec3 pos, double yaw, double pitch, GameType gamemode, boolean flying) {
        FakePlayer instance = new FakePlayer(server, worldIn, profile, ClientInformation.createDefault(), false);
        instance.snapTo(pos.x, pos.y, pos.z, (float) yaw, (float) pitch);
        CommonListenerCookie cookie = cookie(profile, instance.clientInformation());
        FakeClientConnection connection = new FakeClientConnection();
        server.getPlayerList().placeNewPlayer(connection, instance, cookie);
        new FakeServerGamePacketListener(server, connection, instance, cookie);
        loadPlayerData(instance);
        instance.stopRiding(); // otherwise the created fake player will be on the vehicle
        instance.teleportTo(worldIn, pos.x, pos.y, pos.z, Set.of(), (float) yaw, (float) pitch, true, PlayerTeleportEvent.TeleportCause.PLUGIN);
        instance.setHealth(20.0F);
        instance.unsetRemoved();
        instance.getAttribute(Attributes.STEP_HEIGHT).setBaseValue(0.6F);
        instance.gameMode.changeGameModeForPlayer(gamemode);
        server.getPlayerList().broadcastAll(new ClientboundRotateHeadPacket(instance, (byte) (instance.yHeadRot * 256 / 360)), worldIn.dimension());
        server.getPlayerList().broadcastAll(ClientboundEntityPositionSyncPacket.of(instance), worldIn.dimension());
        instance.entityData.set(DATA_PLAYER_MODE_CUSTOMISATION, (byte) 0x7f); // show all model layers (incl. capes)
        instance.getAbilities().flying = flying;
        instance.onUpdateAbilities();
    }

    private static CommonListenerCookie cookie(GameProfile profile, ClientInformation information) {
        return new CommonListenerCookie(profile, 0, information, false, "vanilla", Set.of(), new KeepAlive());
    }

    private static CompletableFuture<GameProfile> fetchGameProfile(MinecraftServer server, final UUID id) {
        final ResolvableProfile resolvableProfile = ResolvableProfile.createUnresolved(id);
        return resolvableProfile.resolveProfile(server.services().profileResolver());
    }

    private static void loadPlayerData(FakePlayer player) {
        try (ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(player.problemPath(), CarpetServer.SLF4J)) {
            Optional<ValueInput> optional = player.level().getServer().getPlayerList().loadPlayerData(player.nameAndId())
                    .map(compoundTag -> TagValueInput.create(scopedCollector, player.registryAccess(), compoundTag));
            optional.ifPresent(valueInput -> {
                player.load(valueInput);
                player.loadAndSpawnEnderPearls(valueInput);
                player.loadAndSpawnParentVehicle(valueInput);
            });
        }
    }

    /**
     * Replaces a real player with a fake one, that keeps doing whatever the player was doing.
     */
    public static FakePlayer createShadow(MinecraftServer server, ServerPlayer player) {
        PlayerActionPack previousActions = ActionPacks.get(player);
        player.connection.disconnect(Component.translatable("multiplayer.disconnect.duplicate_login"));
        ServerLevel worldIn = player.level();
        GameProfile gameprofile = player.getGameProfile();
        FakePlayer playerShadow = new FakePlayer(server, worldIn, gameprofile, player.clientInformation(), true);
        playerShadow.snapTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        playerShadow.setChatSession(player.getChatSession());
        CommonListenerCookie cookie = cookie(gameprofile, player.clientInformation());
        FakeClientConnection connection = new FakeClientConnection();
        server.getPlayerList().placeNewPlayer(connection, playerShadow, cookie);
        new FakeServerGamePacketListener(server, connection, playerShadow, cookie);
        loadPlayerData(playerShadow);

        playerShadow.setHealth(player.getHealth());
        playerShadow.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        playerShadow.gameMode.changeGameModeForPlayer(player.gameMode.getGameModeForPlayer());
        playerShadow.getActionPack().copyFrom(previousActions);
        ActionPacks.remove(player.getUUID());
        // this might create problems if a player logs back in...
        playerShadow.getAttribute(Attributes.STEP_HEIGHT).setBaseValue(0.6F);
        playerShadow.entityData.set(DATA_PLAYER_MODE_CUSTOMISATION, player.getEntityData().get(DATA_PLAYER_MODE_CUSTOMISATION));

        server.getPlayerList().broadcastAll(new ClientboundRotateHeadPacket(playerShadow, (byte) (player.yHeadRot * 256 / 360)), playerShadow.level().dimension());
        server.getPlayerList().broadcastAll(new ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, playerShadow));
        playerShadow.getAbilities().flying = player.getAbilities().flying;
        playerShadow.onUpdateAbilities();
        return playerShadow;
    }

    public static boolean isSpawningPlayer(String username) {
        return SPAWNING.contains(username);
    }

    private FakePlayer(MinecraftServer server, ServerLevel worldIn, GameProfile profile, ClientInformation cli, boolean shadow) {
        super(server, worldIn, profile, cli);
        this.isAShadow = shadow;
        this.actionPack = new PlayerActionPack(this);
    }

    @Override
    public void onEquipItem(final EquipmentSlot slot, final ItemStack previous, final ItemStack stack) {
        if (!this.isUsingItem()) {
            super.onEquipItem(slot, previous, stack);
        }
    }

    @Override
    public void kill(ServerLevel level) {
        this.kill(Component.literal("Killed"));
    }

    public void kill(Component reason) {
        this.shakeOff();
        DisconnectionDetails details = new DisconnectionDetails(reason);
        if (reason.getContents() instanceof TranslatableContents text && text.getKey().equals("multiplayer.disconnect.duplicate_login")) {
            this.connection.onDisconnect(details);
        } else {
            MinecraftServer server = this.level().getServer();
            server.schedule(new TickTask(server.getTickCount(), () -> this.connection.onDisconnect(details)));
        }
    }

    @Override
    public void tick() {
        if (this.level().getServer().getTickCount() % 10 == 0) {
            this.connection.resetPosition();
            this.level().getChunkSource().move(this);
        }
        try {
            this.actionPack.onUpdate();
            super.tick();
            this.doTick();
        } catch (NullPointerException ignored) {
            // the game not gonna crash violently.
        }
    }

    /**
     * Real players report their movement from the client, fake players use their actual movement.
     */
    @Override
    public Vec3 getKnownMovement() {
        return this.getControllingPassenger() instanceof Player controller && this.isAlive() ? controller.getKnownMovement() : this.getDeltaMovement();
    }

    @Override
    public Vec3 getKnownSpeed() {
        return this.getControllingPassenger() instanceof Player controller && this.isAlive() ? controller.getKnownSpeed() : this.position().subtract(this.xo, this.yo, this.zo);
    }

    @Override
    public boolean startRiding(Entity entityToRide, boolean force, boolean sendEventAndTriggers) {
        if (super.startRiding(entityToRide, force, sendEventAndTriggers)) {
            // from ClientPacketListener.handleSetEntityPassengersPacket
            if (entityToRide instanceof AbstractBoat) {
                this.yRotO = entityToRide.getYRot();
                this.setYRot(entityToRide.getYRot());
                this.setYHeadRot(entityToRide.getYRot());
            }
            return true;
        }
        return false;
    }

    private void shakeOff() {
        if (this.getVehicle() instanceof Player) {
            this.stopRiding();
        }
        for (Entity passenger : this.getIndirectPassengers()) {
            if (passenger instanceof Player) {
                passenger.stopRiding();
            }
        }
    }

    @Override
    public void die(DamageSource cause) {
        this.shakeOff();
        super.die(cause);
        this.setHealth(20);
        this.foodData = new FoodData();
        this.kill(this.getCombatTracker().getDeathMessage());
    }

    @Override
    public String getIpAddress() {
        return "127.0.0.1";
    }

    @Override
    public boolean allowsListing() {
        return CarpetSettings.allowListingFakePlayers;
    }

    /**
     * fabric-carpet respawns fake players after the credits through a mixin into {@code PlayerList.respawn}. On Paper
     * the credits are skipped, the same way the {@code disableEndCredits} option does, so the player goes through the
     * portal like any other entity.
     */
    @Override
    public void showEndCredits() {
        this.seenCredits = true;
    }

    @Override
    public @Nullable ServerPlayer teleport(TeleportTransition transition) {
        ServerPlayer result = super.teleport(transition);
        // No client to acknowledge the dimension change
        if (this.isChangingDimension()) {
            this.hasChangedDimension();
        }
        return result;
    }

}
