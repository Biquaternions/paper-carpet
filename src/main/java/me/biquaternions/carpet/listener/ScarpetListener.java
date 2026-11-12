package me.biquaternions.carpet.listener;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import com.destroystokyo.paper.event.player.PlayerRecipeBookClickEvent;
import com.destroystokyo.paper.event.server.ServerTickEndEvent;
import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.event.player.PlayerArmSwingEvent;
import io.papermc.paper.event.player.PlayerPurchaseEvent;
import io.papermc.paper.event.player.PlayerStopUsingItemEvent;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import me.biquaternions.carpet.script.CarpetEventServer.Event;
import me.biquaternions.carpet.script.CarpetScriptServer;
import me.biquaternions.carpet.script.EntityEventsGroup;
import me.biquaternions.carpet.script.external.Vanilla;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.ExplosionResult;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.block.CraftBlockState;
import org.bukkit.craftbukkit.damage.CraftDamageSource;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.inventory.CraftMerchant;
import org.bukkit.craftbukkit.inventory.CraftMerchantRecipe;
import org.bukkit.craftbukkit.CraftStatistic;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerStatisticIncrementEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.event.weather.LightningStrikeEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutionException;

/**
 * <p>Translates Paper events into scarpet events.</p>
 *
 * <p>fabric-carpet fires scarpet events from mixins at the exact place they happen. On Paper, the closest Bukkit/Paper
 * event is used instead, so some events fire at slightly different moments, and a few are not available at all
 * ({@code player_collides_with_entity}). Per entity {@code on_tick} and {@code on_move} events are fired at the end of
 * the server tick for entities that have event handlers.</p>
 */
@NullMarked
public class ScarpetListener implements Listener {

    private final Plugin plugin;
    private final Set<UUID> newEntities = new HashSet<>();
    private final Map<net.minecraft.world.entity.Entity, Vec3> lastPositions = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<UUID, Location> lastPlayerLocations = new HashMap<>();

    public ScarpetListener(Plugin plugin) {
        this.plugin = plugin;
    }

    private static @Nullable CarpetScriptServer scriptServer() {
        return Vanilla.MinecraftServer_getScriptServer(MinecraftServer.getServer());
    }

    private static ServerPlayer handle(Player player) {
        return ((CraftPlayer) player).getHandle();
    }

    private static ServerLevel level(World world) {
        return ((CraftWorld) world).getHandle();
    }

    private static BlockPos pos(Block block) {
        return ((CraftBlock) block).getPosition();
    }

    private static InteractionHand hand(@Nullable EquipmentSlot slot) {
        return slot == EquipmentSlot.OFF_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }

    private static Direction direction(org.bukkit.block.BlockFace face) {
        return switch (face) {
            case DOWN -> Direction.DOWN;
            case NORTH -> Direction.NORTH;
            case SOUTH -> Direction.SOUTH;
            case WEST -> Direction.WEST;
            case EAST -> Direction.EAST;
            default -> Direction.UP;
        };
    }

    private static void cancelIf(Cancellable event, boolean cancel) {
        if (cancel) {
            event.setCancelled(true);
        }
    }

    // ----- Server ----- //

    @EventHandler
    public void onTickEnd(final ServerTickEndEvent event) {
        CarpetScriptServer server = scriptServer();
        if (server == null || server.stopAll) {
            return;
        }
        MinecraftServer minecraft = MinecraftServer.getServer();
        if (Event.TICK.isNeeded()) {
            Event.TICK.onTick(minecraft);
        }
        if (Event.NETHER_TICK.isNeeded()) {
            Event.NETHER_TICK.onTick(minecraft);
        }
        if (Event.ENDER_TICK.isNeeded()) {
            Event.ENDER_TICK.onTick(minecraft);
        }
        server.tick();
        this.tickEntityEvents();
    }

    private void tickEntityEvents() {
        for (World world : Bukkit.getWorlds()) {
            for (org.bukkit.entity.Entity bukkit : world.getEntities()) {
                Entity entity = ((CraftEntity) bukkit).getHandle();
                EntityEventsGroup events = Vanilla.Entity_getEventContainerIfPresent(entity);
                if (events == null) {
                    continue;
                }
                events.onEvent(EntityEventsGroup.Event.ON_TICK);
                Vec3 position = entity.position();
                Vec3 previous = this.lastPositions.put(entity, position);
                if (previous != null && !previous.equals(position)) {
                    events.onEvent(EntityEventsGroup.Event.ON_MOVE, position.subtract(previous), previous, position);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(final ChunkLoadEvent event) {
        ServerLevel level = level(event.getWorld());
        ChunkPos pos = new ChunkPos(event.getChunk().getX(), event.getChunk().getZ());
        if (event.isNewChunk() && Event.CHUNK_GENERATED.isNeeded()) {
            Event.CHUNK_GENERATED.onChunkEvent(level, pos, true);
        }
        if (Event.CHUNK_LOADED.isNeeded()) {
            Event.CHUNK_LOADED.onChunkEvent(level, pos, event.isNewChunk());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(final ChunkUnloadEvent event) {
        if (Event.CHUNK_UNLOADED.isNeeded()) {
            Event.CHUNK_UNLOADED.onChunkEvent(level(event.getWorld()), new ChunkPos(event.getChunk().getX(), event.getChunk().getZ()), false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLightning(final LightningStrikeEvent event) {
        if (Event.LIGHTNING.isNeeded()) {
            Location location = event.getLightning().getLocation();
            Event.LIGHTNING.onWorldEventFlag(level(event.getWorld()), BlockPos.containing(location.getX(), location.getY(), location.getZ()), 0);
        }
    }

    private static Explosion.BlockInteraction interaction(ExplosionResult result) {
        return switch (result) {
            case KEEP -> Explosion.BlockInteraction.KEEP;
            case DESTROY -> Explosion.BlockInteraction.DESTROY;
            case DESTROY_WITH_DECAY -> Explosion.BlockInteraction.DESTROY_WITH_DECAY;
            case TRIGGER_BLOCK -> Explosion.BlockInteraction.TRIGGER_BLOCK;
        };
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplosionPrime(final ExplosionPrimeEvent event) {
        if (!Event.EXPLOSION.isNeeded()) {
            return;
        }
        Entity entity = ((CraftEntity) event.getEntity()).getHandle();
        boolean cancel = Event.EXPLOSION.onExplosion((ServerLevel) entity.level(), entity, null, entity.position(), event.getRadius(), event.getFire(),
                null, null, Explosion.BlockInteraction.DESTROY);
        cancelIf(event, cancel);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(final EntityExplodeEvent event) {
        if (!Event.EXPLOSION_OUTCOME.isNeeded()) {
            return;
        }
        Entity entity = ((CraftEntity) event.getEntity()).getHandle();
        Location location = event.getLocation();
        List<BlockPos> blocks = event.blockList().stream().map(ScarpetListener::pos).toList();
        Event.EXPLOSION_OUTCOME.onExplosion(level(location.getWorld()), entity, null, new Vec3(location.getX(), location.getY(), location.getZ()),
                event.getYield(), false, blocks, List.of(), interaction(event.getExplosionResult()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(final BlockExplodeEvent event) {
        if (!Event.EXPLOSION_OUTCOME.isNeeded()) {
            return;
        }
        Block block = event.getBlock();
        List<BlockPos> blocks = event.blockList().stream().map(ScarpetListener::pos).toList();
        Event.EXPLOSION_OUTCOME.onExplosion(level(block.getWorld()), null, null, new Vec3(block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5),
                event.getYield(), false, blocks, List.of(), interaction(event.getExplosionResult()));
    }

    // ----- Entities ----- //

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntitySpawn(final EntitySpawnEvent event) {
        this.newEntities.add(event.getEntity().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityAdd(final EntityAddToWorldEvent event) {
        Entity entity = ((CraftEntity) event.getEntity()).getHandle();
        boolean created = this.newEntities.remove(entity.getUUID());
        Event handler = Event.ENTITY_HANDLER.get(entity.getType());
        if (handler != null && handler.isNeeded()) {
            handler.onEntityAction(entity, created);
        }
        Event load = Event.ENTITY_LOAD.get(entity.getType());
        if (load != null && load.isNeeded()) {
            load.onEntityAction(entity, true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityRemove(final EntityRemoveFromWorldEvent event) {
        Entity entity = ((CraftEntity) event.getEntity()).getHandle();
        this.newEntities.remove(entity.getUUID());
        this.lastPositions.remove(entity);
        EntityEventsGroup events = Vanilla.Entity_getEventContainerIfPresent(entity);
        if (events != null) {
            events.onEvent(EntityEventsGroup.Event.ON_REMOVED);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(final EntityDamageEvent event) {
        Entity entity = ((CraftEntity) event.getEntity()).getHandle();
        DamageSource source = ((CraftDamageSource) event.getDamageSource()).getHandle();
        float amount = (float) event.getDamage();
        EntityEventsGroup events = Vanilla.Entity_getEventContainerIfPresent(entity);
        if (events != null && event.getEntity() instanceof LivingEntity) {
            events.onEvent(EntityEventsGroup.Event.ON_DAMAGE, amount, source);
        }
        if (entity instanceof ServerPlayer && Event.PLAYER_TAKES_DAMAGE.isNeeded()) {
            cancelIf(event, Event.PLAYER_TAKES_DAMAGE.onDamage(entity, amount, source));
        }
        if (event instanceof EntityDamageByEntityEvent byEntity && ((CraftEntity) byEntity.getDamager()).getHandle() instanceof ServerPlayer
                && Event.PLAYER_DEALS_DAMAGE.isNeeded() && !event.isCancelled()) {
            cancelIf(event, Event.PLAYER_DEALS_DAMAGE.onDamage(entity, amount, source));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(final EntityDeathEvent event) {
        Entity entity = ((CraftEntity) event.getEntity()).getHandle();
        EntityEventsGroup events = Vanilla.Entity_getEventContainerIfPresent(entity);
        if (events != null) {
            events.onEvent(EntityEventsGroup.Event.ON_DEATH, ((CraftDamageSource) event.getDamageSource()).getHandle().getMsgId());
        }
        if (entity instanceof ServerPlayer player && Event.PLAYER_DIES.isNeeded()) {
            Event.PLAYER_DIES.onPlayerEvent(player);
        }
    }

    /**
     * Permanent vehicles (set with {@code modify(e, 'mount_passengers', ...)}) can't be dismounted.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDismount(final EntityDismountEvent event) {
        Entity vehicle = ((CraftEntity) event.getDismounted()).getHandle();
        if (Vanilla.Entity_isPermanentVehicle(vehicle) && vehicle.isAlive() && event.isCancellable()) {
            event.setCancelled(true);
        }
    }

    // ----- Players ----- //

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(final PlayerJoinEvent event) {
        CarpetScriptServer server = scriptServer();
        if (server != null) {
            server.onPlayerJoin(handle(event.getPlayer()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(final PlayerQuitEvent event) {
        this.lastPlayerLocations.remove(event.getPlayer().getUniqueId());
        CarpetScriptServer server = scriptServer();
        if (server != null && !server.stopAll) {
            String reason = event.getReason().name().toLowerCase(java.util.Locale.ROOT);
            server.onPlayerLoggedOut(handle(event.getPlayer()), net.minecraft.network.chat.Component.literal(reason));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJump(final PlayerJumpEvent event) {
        if (Event.PLAYER_JUMPS.isNeeded()) {
            Event.PLAYER_JUMPS.onPlayerEvent(handle(event.getPlayer()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGlide(final EntityToggleGlideEvent event) {
        if (event.isGliding() && event.getEntity() instanceof Player player && Event.PLAYER_DEPLOYS_ELYTRA.isNeeded()) {
            Event.PLAYER_DEPLOYS_ELYTRA.onPlayerEvent(handle(player));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBedLeave(final PlayerBedLeaveEvent event) {
        long time = event.getPlayer().getWorld().getTime();
        boolean night = time > 12541 && time < 23458;
        Event scarpet = night ? Event.PLAYER_ESCAPES_SLEEP : Event.PLAYER_WAKES_UP;
        if (scarpet.isNeeded()) {
            scarpet.onPlayerEvent(handle(event.getPlayer()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInput(final PlayerInputEvent event) {
        Player player = event.getPlayer();
        if (player.getVehicle() == null || !Event.PLAYER_RIDES.isNeeded()) {
            return;
        }
        org.bukkit.Input input = event.getInput();
        float strafe = input.isLeft() == input.isRight() ? 0 : (input.isLeft() ? -1 : 1);
        float forward = input.isForward() == input.isBackward() ? 0 : (input.isForward() ? 1 : -1);
        Event.PLAYER_RIDES.onMountControls(handle(player), strafe, forward, input.isJump(), input.isSneak());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(final PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ServerPlayer handle = handle(player);
        InteractionHand hand = hand(event.getHand());
        Action action = event.getAction();
        Block block = event.getClickedBlock();
        if (action == Action.LEFT_CLICK_BLOCK && block != null && Event.PLAYER_CLICKS_BLOCK.isNeeded()) {
            cancelIf(event, Event.PLAYER_CLICKS_BLOCK.onBlockAction(handle, pos(block), direction(event.getBlockFace())));
        }
        if (action == Action.RIGHT_CLICK_BLOCK && block != null && Event.PLAYER_RIGHT_CLICKS_BLOCK.isNeeded()) {
            Location point = event.getInteractionPoint();
            Vec3 hit = point == null ? Vec3.atCenterOf(pos(block)) : new Vec3(point.getX(), point.getY(), point.getZ());
            BlockHitResult hitResult = new BlockHitResult(hit, direction(event.getBlockFace()), pos(block), false);
            if (Event.PLAYER_RIGHT_CLICKS_BLOCK.onBlockHit(handle, hand, hitResult)) {
                event.setCancelled(true);
                return;
            }
        }
        if (action == Action.RIGHT_CLICK_AIR && event.getItem() != null && Event.PLAYER_USES_ITEM.isNeeded()) {
            if (Event.PLAYER_USES_ITEM.onItemAction(handle, hand, CraftItemStack.asNMSCopy(event.getItem()))) {
                event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteracted(final PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || block == null || event.useInteractedBlock() == org.bukkit.event.Event.Result.DENY
                || !block.getType().isInteractable() || !Event.PLAYER_INTERACTS_WITH_BLOCK.isNeeded()) {
            return;
        }
        Location point = event.getInteractionPoint();
        Vec3 hit = point == null ? Vec3.atCenterOf(pos(block)) : new Vec3(point.getX(), point.getY(), point.getZ());
        Event.PLAYER_INTERACTS_WITH_BLOCK.onBlockHit(handle(event.getPlayer()), hand(event.getHand()), new BlockHitResult(hit, direction(event.getBlockFace()), pos(block), false));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlacing(final BlockPlaceEvent event) {
        if (Event.PLAYER_PLACING_BLOCK.isNeeded()) {
            cancelIf(event, Event.PLAYER_PLACING_BLOCK.onBlockPlaced(handle(event.getPlayer()), pos(event.getBlockPlaced()), hand(event.getHand()), CraftItemStack.asNMSCopy(event.getItemInHand())));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlaced(final BlockPlaceEvent event) {
        if (Event.PLAYER_PLACES_BLOCK.isNeeded()) {
            Event.PLAYER_PLACES_BLOCK.onBlockPlaced(handle(event.getPlayer()), pos(event.getBlockPlaced()), hand(event.getHand()), CraftItemStack.asNMSCopy(event.getItemInHand()));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(final BlockBreakEvent event) {
        if (Event.PLAYER_BREAK_BLOCK.isNeeded()) {
            cancelIf(event, Event.PLAYER_BREAK_BLOCK.onBlockBroken(handle(event.getPlayer()), pos(event.getBlock()), ((CraftBlockState) event.getBlock().getState()).getHandle()));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(final PlayerInteractEntityEvent event) {
        if (Event.PLAYER_INTERACTS_WITH_ENTITY.isNeeded()) {
            cancelIf(event, Event.PLAYER_INTERACTS_WITH_ENTITY.onEntityHandAction(handle(event.getPlayer()), ((CraftEntity) event.getRightClicked()).getHandle(), hand(event.getHand())));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAttack(final PrePlayerAttackEntityEvent event) {
        if (Event.PLAYER_ATTACKS_ENTITY.isNeeded()) {
            cancelIf(event, Event.PLAYER_ATTACKS_ENTITY.onEntityHandAction(handle(event.getPlayer()), ((CraftEntity) event.getAttacked()).getHandle(), null));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTrade(final PlayerPurchaseEvent event) {
        if (!Event.PLAYER_TRADES.isNeeded() || !(event.getMerchant() instanceof CraftMerchant craftMerchant)) {
            return;
        }
        Merchant merchant = craftMerchant.getMerchant();
        MerchantOffer offer = CraftMerchantRecipe.fromBukkit(event.getTrade()).toMinecraft();
        Event.PLAYER_TRADES.onTrade(handle(event.getPlayer()), merchant, offer);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(final EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && Event.PLAYER_PICKS_UP_ITEM.isNeeded()) {
            Event.PLAYER_PICKS_UP_ITEM.onItemAction(handle(player), null, CraftItemStack.asNMSCopy(event.getItem().getItemStack()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSneak(final PlayerToggleSneakEvent event) {
        Event scarpet = event.isSneaking() ? Event.PLAYER_STARTS_SNEAKING : Event.PLAYER_STOPS_SNEAKING;
        if (scarpet.isNeeded()) {
            scarpet.onPlayerEvent(handle(event.getPlayer()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSprint(final PlayerToggleSprintEvent event) {
        Event scarpet = event.isSprinting() ? Event.PLAYER_STARTS_SPRINTING : Event.PLAYER_STOPS_SPRINTING;
        if (scarpet.isNeeded()) {
            scarpet.onPlayerEvent(handle(event.getPlayer()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onStopUsing(final PlayerStopUsingItemEvent event) {
        if (Event.PLAYER_RELEASED_ITEM.isNeeded()) {
            ServerPlayer player = handle(event.getPlayer());
            Event.PLAYER_RELEASED_ITEM.onItemAction(player, player.getUsedItemHand(), CraftItemStack.asNMSCopy(event.getItem()));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onConsume(final PlayerItemConsumeEvent event) {
        if (Event.PLAYER_FINISHED_USING_ITEM.isNeeded()) {
            cancelIf(event, Event.PLAYER_FINISHED_USING_ITEM.onItemAction(handle(event.getPlayer()), hand(event.getHand()), CraftItemStack.asNMSCopy(event.getItem())));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(final PlayerDropItemEvent event) {
        // Paper doesn't tell whether the whole stack was dropped, dropping more than one item is considered a stack drop
        Event scarpet = event.getItemDrop().getItemStack().getAmount() > 1 ? Event.PLAYER_DROPS_STACK : Event.PLAYER_DROPS_ITEM;
        if (scarpet.isNeeded()) {
            cancelIf(event, scarpet.onPlayerEvent(handle(event.getPlayer())));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRecipe(final PlayerRecipeBookClickEvent event) {
        if (Event.PLAYER_CHOOSES_RECIPE.isNeeded()) {
            Identifier recipe = Identifier.fromNamespaceAndPath(event.getRecipe().getNamespace(), event.getRecipe().getKey());
            cancelIf(event, Event.PLAYER_CHOOSES_RECIPE.onRecipeSelected(handle(event.getPlayer()), recipe, event.isMakeAll()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSlot(final PlayerItemHeldEvent event) {
        if (Event.PLAYER_SWITCHES_SLOT.isNeeded()) {
            Event.PLAYER_SWITCHES_SLOT.onSlotSwitch(handle(event.getPlayer()), event.getPreviousSlot(), event.getNewSlot());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwap(final PlayerSwapHandItemsEvent event) {
        if (Event.PLAYER_SWAPS_HANDS.isNeeded()) {
            cancelIf(event, Event.PLAYER_SWAPS_HANDS.onPlayerEvent(handle(event.getPlayer())));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwing(final PlayerArmSwingEvent event) {
        if (Event.PLAYER_SWINGS_HAND.isNeeded()) {
            Event.PLAYER_SWINGS_HAND.onHandAction(handle(event.getPlayer()), hand(event.getHand()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(final PlayerPostRespawnEvent event) {
        if (Event.PLAYER_RESPAWNS.isNeeded()) {
            Event.PLAYER_RESPAWNS.onPlayerEvent(handle(event.getPlayer()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(final PlayerTeleportEvent event) {
        if (event.getTo().getWorld() != event.getFrom().getWorld()) {
            this.lastPlayerLocations.put(event.getPlayer().getUniqueId(), event.getFrom());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangedWorld(final PlayerChangedWorldEvent event) {
        Location from = this.lastPlayerLocations.remove(event.getPlayer().getUniqueId());
        if (!Event.PLAYER_CHANGES_DIMENSION.isNeeded()) {
            return;
        }
        ServerPlayer player = handle(event.getPlayer());
        Vec3 fromPos = from == null ? player.position() : new Vec3(from.getX(), from.getY(), from.getZ());
        Event.PLAYER_CHANGES_DIMENSION.onDimensionChange(player, fromPos, player.position(), level(event.getFrom()).dimension(), player.level().dimension());
    }

    /**
     * Scarpet runs on the main thread, chat is handled asynchronously by Paper.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(final AsyncChatEvent event) {
        if (!Event.PLAYER_MESSAGE.isNeeded()) {
            return;
        }
        ServerPlayer player = handle(event.getPlayer());
        String message = PlainTextComponentSerializer.plainText().serialize(event.originalMessage());
        boolean cancel;
        if (Bukkit.isPrimaryThread()) {
            cancel = Event.PLAYER_MESSAGE.onPlayerMessage(player, message);
        } else {
            try {
                cancel = Bukkit.getScheduler().callSyncMethod(this.plugin, () -> Event.PLAYER_MESSAGE.onPlayerMessage(player, message)).get();
            } catch (InterruptedException | ExecutionException e) {
                cancel = false;
            }
        }
        cancelIf(event, cancel);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(final PlayerCommandPreprocessEvent event) {
        if (Event.PLAYER_COMMAND.isNeeded()) {
            String command = event.getMessage().startsWith("/") ? event.getMessage().substring(1) : event.getMessage();
            Event.PLAYER_COMMAND.onPlayerMessage(handle(event.getPlayer()), command);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onStatistic(final PlayerStatisticIncrementEvent event) {
        if (!Event.STATISTICS.isNeeded()) {
            return;
        }
        Stat<?> stat = statistic(event);
        if (stat != null) {
            Event.STATISTICS.onPlayerStatistic(handle(event.getPlayer()), stat, event.getNewValue() - event.getPreviousValue());
        }
    }

    private static @Nullable Stat<?> statistic(PlayerStatisticIncrementEvent event) {
        Statistic statistic = event.getStatistic();
        if (event.getEntityType() != null) {
            return CraftStatistic.getEntityStatistic(statistic, event.getEntityType());
        }
        Material material = event.getMaterial();
        if (material != null) {
            Identifier key = Identifier.parse(material.getKey().asString());
            return switch (statistic) {
                case MINE_BLOCK -> Stats.BLOCK_MINED.get(BuiltInRegistries.BLOCK.getValue(key));
                case CRAFT_ITEM -> Stats.ITEM_CRAFTED.get(BuiltInRegistries.ITEM.getValue(key));
                case USE_ITEM -> Stats.ITEM_USED.get(BuiltInRegistries.ITEM.getValue(key));
                case BREAK_ITEM -> Stats.ITEM_BROKEN.get(BuiltInRegistries.ITEM.getValue(key));
                case PICKUP -> Stats.ITEM_PICKED_UP.get(BuiltInRegistries.ITEM.getValue(key));
                case DROP -> Stats.ITEM_DROPPED.get(BuiltInRegistries.ITEM.getValue(key));
                default -> null;
            };
        }
        return CraftStatistic.getNMSStatistic(statistic);
    }

}
