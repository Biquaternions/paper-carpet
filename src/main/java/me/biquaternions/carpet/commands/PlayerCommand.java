package me.biquaternions.carpet.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.FinePositionResolver;
import io.papermc.paper.command.brigadier.argument.resolvers.RotationResolver;
import io.papermc.paper.math.FinePosition;
import io.papermc.paper.math.Rotation;
import lombok.experimental.UtilityClass;
import me.biquaternions.carpet.CarpetSettings;
import me.biquaternions.carpet.fakes.ActionPacks;
import me.biquaternions.carpet.fakes.FakePlayer;
import me.biquaternions.carpet.fakes.PlayerActionPack;
import me.biquaternions.carpet.fakes.PlayerActionPack.Action;
import me.biquaternions.carpet.fakes.PlayerActionPack.ActionType;
import me.biquaternions.carpet.utils.CommandHelper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.OldUsersConverter;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Port of fabric-carpet's {@code /player} command.
 */
@NullMarked
@UtilityClass
@SuppressWarnings("UnstableApiUsage")
public class PlayerCommand {

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("player")
                .requires(CommandHelper.commandPermission(() -> CarpetSettings.commandPlayer, "carpet.player"))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((c, b) -> suggestPlayers(b))
                        .then(Commands.literal("stop").executes(manipulation(PlayerActionPack::stopAll)))
                        .then(makeActionCommand("use", ActionType.USE))
                        .then(makeActionCommand("jump", ActionType.JUMP))
                        .then(makeActionCommand("attack", ActionType.ATTACK))
                        .then(makeActionCommand("drop", ActionType.DROP_ITEM))
                        .then(makeDropCommand("drop", false))
                        .then(makeActionCommand("dropStack", ActionType.DROP_STACK))
                        .then(makeDropCommand("dropStack", true))
                        .then(makeActionCommand("swapHands", ActionType.SWAP_HANDS))
                        .then(Commands.literal("hotbar")
                                .then(Commands.argument("slot", IntegerArgumentType.integer(1, 9))
                                        .executes(c -> manipulate(c, ap -> ap.setSlot(IntegerArgumentType.getInteger(c, "slot"))))))
                        .then(Commands.literal("kill").executes(PlayerCommand::kill))
                        .then(Commands.literal("shadow").executes(PlayerCommand::shadow))
                        .then(Commands.literal("mount").executes(manipulation(ap -> ap.mount(true)))
                                .then(Commands.literal("anything").executes(manipulation(ap -> ap.mount(false)))))
                        .then(Commands.literal("dismount").executes(manipulation(PlayerActionPack::dismount)))
                        .then(Commands.literal("sneak").executes(manipulation(ap -> ap.setSneaking(true))))
                        .then(Commands.literal("unsneak").executes(manipulation(ap -> ap.setSneaking(false))))
                        .then(Commands.literal("sprint").executes(manipulation(ap -> ap.setSprinting(true))))
                        .then(Commands.literal("unsprint").executes(manipulation(ap -> ap.setSprinting(false))))
                        .then(Commands.literal("look")
                                .then(Commands.literal("north").executes(manipulation(ap -> ap.look(Direction.NORTH))))
                                .then(Commands.literal("south").executes(manipulation(ap -> ap.look(Direction.SOUTH))))
                                .then(Commands.literal("east").executes(manipulation(ap -> ap.look(Direction.EAST))))
                                .then(Commands.literal("west").executes(manipulation(ap -> ap.look(Direction.WEST))))
                                .then(Commands.literal("up").executes(manipulation(ap -> ap.look(Direction.UP))))
                                .then(Commands.literal("down").executes(manipulation(ap -> ap.look(Direction.DOWN))))
                                .then(Commands.literal("at").then(Commands.argument("position", ArgumentTypes.finePosition())
                                        .executes(c -> {
                                            Vec3 position = position(c, "position");
                                            return manipulate(c, ap -> ap.lookAt(position));
                                        })))
                                .then(Commands.argument("direction", ArgumentTypes.rotation())
                                        .executes(c -> {
                                            Vec2 rotation = rotation(c, "direction");
                                            return manipulate(c, ap -> ap.look(rotation));
                                        })))
                        .then(Commands.literal("turn")
                                .then(Commands.literal("left").executes(manipulation(ap -> ap.turn(-90, 0))))
                                .then(Commands.literal("right").executes(manipulation(ap -> ap.turn(90, 0))))
                                .then(Commands.literal("back").executes(manipulation(ap -> ap.turn(180, 0))))
                                .then(Commands.argument("rotation", ArgumentTypes.rotation())
                                        .executes(c -> {
                                            Vec2 rotation = rotation(c, "rotation");
                                            return manipulate(c, ap -> ap.turn(rotation));
                                        })))
                        .then(Commands.literal("move").executes(manipulation(PlayerActionPack::stopMovement))
                                .then(Commands.literal("forward").executes(manipulation(ap -> ap.setForward(1))))
                                .then(Commands.literal("backward").executes(manipulation(ap -> ap.setForward(-1))))
                                .then(Commands.literal("left").executes(manipulation(ap -> ap.setStrafing(1))))
                                .then(Commands.literal("right").executes(manipulation(ap -> ap.setStrafing(-1)))))
                        .then(Commands.literal("spawn").executes(PlayerCommand::spawn)
                                .then(Commands.literal("in").requires(s -> CommandHelper.hasPermissionLevel(s, 2))
                                        .then(Commands.argument("gamemode", ArgumentTypes.gameMode())
                                                .executes(PlayerCommand::spawn)))
                                .then(Commands.literal("at").then(Commands.argument("position", ArgumentTypes.finePosition()).executes(PlayerCommand::spawn)
                                        .then(Commands.literal("facing").then(Commands.argument("direction", ArgumentTypes.rotation()).executes(PlayerCommand::spawn)
                                                .then(Commands.literal("in").then(Commands.argument("dimension", ArgumentTypes.world()).executes(PlayerCommand::spawn)
                                                        .then(Commands.literal("in").requires(s -> CommandHelper.hasPermissionLevel(s, 2))
                                                                .then(Commands.argument("gamemode", ArgumentTypes.gameMode())
                                                                        .executes(PlayerCommand::spawn)))))))))))
                .build();
    }

    private LiteralArgumentBuilder<CommandSourceStack> makeActionCommand(String actionName, ActionType type) {
        return Commands.literal(actionName)
                .executes(manipulation(ap -> ap.start(type, Action.once())))
                .then(Commands.literal("once").executes(manipulation(ap -> ap.start(type, Action.once()))))
                .then(Commands.literal("continuous").executes(manipulation(ap -> ap.start(type, Action.continuous()))))
                .then(Commands.literal("interval").then(Commands.argument("ticks", IntegerArgumentType.integer(1))
                        .executes(c -> manipulate(c, ap -> ap.start(type, Action.interval(IntegerArgumentType.getInteger(c, "ticks")))))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> makeDropCommand(String actionName, boolean dropAll) {
        return Commands.literal(actionName)
                .then(Commands.literal("all").executes(manipulation(ap -> ap.drop(-2, dropAll))))
                .then(Commands.literal("mainhand").executes(manipulation(ap -> ap.drop(-1, dropAll))))
                .then(Commands.literal("offhand").executes(manipulation(ap -> ap.drop(40, dropAll))))
                .then(Commands.argument("slot", IntegerArgumentType.integer(0, 40))
                        .executes(c -> manipulate(c, ap -> ap.drop(IntegerArgumentType.getInteger(c, "slot"), dropAll))));
    }

    private CompletableFuture<Suggestions> suggestPlayers(SuggestionsBuilder builder) {
        Set<String> players = new LinkedHashSet<>(List.of("Steve", "Alex"));
        Bukkit.getOnlinePlayers().forEach(p -> players.add(p.getName()));
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        players.stream().filter(name -> name.toLowerCase(Locale.ROOT).startsWith(remaining)).forEach(builder::suggest);
        return builder.buildFuture();
    }

    private Vec3 position(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        FinePosition position = context.getArgument(name, FinePositionResolver.class).resolve(context.getSource());
        return new Vec3(position.x(), position.y(), position.z());
    }

    private Vec2 rotation(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        Rotation rotation = context.getArgument(name, RotationResolver.class).resolve(context.getSource());
        return new Vec2(rotation.yaw(), rotation.pitch());
    }

    private @Nullable ServerPlayer getPlayer(CommandContext<CommandSourceStack> context) {
        Player player = Bukkit.getPlayerExact(StringArgumentType.getString(context, "player"));
        return player == null ? null : ((CraftPlayer) player).getHandle();
    }

    private void error(CommandSender sender, String message) {
        sender.sendMessage(Component.text(message, NamedTextColor.RED));
    }

    private boolean cantManipulate(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = getPlayer(context);
        CommandSender sender = context.getSource().getSender();
        if (player == null) {
            error(sender, "Can only manipulate existing players");
            return true;
        }
        if (!(sender instanceof Player senderPlayer)) {
            return false;
        }
        if (!senderPlayer.isOp() && !senderPlayer.hasPermission("carpet.player.others")) {
            if (((CraftPlayer) senderPlayer).getHandle() != player && !(player instanceof FakePlayer)) {
                error(sender, "Non OP players can't control other real players");
                return true;
            }
        }
        return false;
    }

    private boolean cantReMove(CommandContext<CommandSourceStack> context) {
        if (cantManipulate(context)) {
            return true;
        }
        if (getPlayer(context) instanceof FakePlayer) {
            return false;
        }
        error(context.getSource().getSender(), "Only fake players can be moved or killed");
        return true;
    }

    private boolean cantSpawn(CommandContext<CommandSourceStack> context) {
        String playerName = StringArgumentType.getString(context, "player");
        MinecraftServer server = MinecraftServer.getServer();
        PlayerList manager = server.getPlayerList();
        CommandSender sender = context.getSource().getSender();

        if (FakePlayer.isSpawningPlayer(playerName)) {
            sender.sendMessage(Component.text("Player ", NamedTextColor.RED)
                    .append(Component.text(playerName, NamedTextColor.RED, TextDecoration.BOLD))
                    .append(Component.text(" is currently logging on", NamedTextColor.RED)));
            return true;
        }
        if (manager.getPlayerByName(playerName) != null) {
            sender.sendMessage(Component.text("Player ", NamedTextColor.RED)
                    .append(Component.text(playerName, NamedTextColor.RED, TextDecoration.BOLD))
                    .append(Component.text(" is already logged on", NamedTextColor.RED)));
            return true;
        }
        String notFound = "Player " + playerName + " is either banned by Mojang, or auth servers are down. "
                + "Banned players can only be summoned in Singleplayer and in servers in off-line mode.";
        UUID uuid = OldUsersConverter.convertMobOwnerIfNecessary(server, playerName);
        if (uuid == null) {
            if (!CarpetSettings.allowSpawningOfflinePlayers) {
                error(sender, notFound);
                return true;
            }
            uuid = UUIDUtil.createOfflinePlayerUUID(playerName);
        }
        NameAndId profile = server.services().nameToIdCache().get(uuid).orElse(null);
        if (profile == null) {
            if (!CarpetSettings.allowSpawningOfflinePlayers) {
                error(sender, notFound);
                return true;
            }
            profile = new NameAndId(UUIDUtil.createOfflinePlayerUUID(playerName), playerName);
        }
        if (manager.getBans().isBanned(profile)) {
            sender.sendMessage(Component.text("Player ", NamedTextColor.RED)
                    .append(Component.text(playerName, NamedTextColor.RED, TextDecoration.BOLD))
                    .append(Component.text(" is banned on this server", NamedTextColor.RED)));
            return true;
        }
        if (manager.isUsingWhitelist() && manager.isWhiteListed(profile) && !CommandHelper.hasPermissionLevel(context.getSource(), 2)) {
            error(sender, "Whitelisted players can only be spawned by operators");
            return true;
        }
        return false;
    }

    private int kill(CommandContext<CommandSourceStack> context) {
        if (cantReMove(context)) {
            return 0;
        }
        ServerPlayer player = getPlayer(context);
        player.kill(player.level());
        return Command.SINGLE_SUCCESS;
    }

    @FunctionalInterface
    interface SupplierWithCSE<T> {
        T get() throws CommandSyntaxException;
    }

    private <T> T getArgOrDefault(SupplierWithCSE<T> getter, T defaultValue) throws CommandSyntaxException {
        try {
            return getter.get();
        } catch (IllegalArgumentException e) {
            return defaultValue;
        }
    }

    private int spawn(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        if (cantSpawn(context)) {
            return 0;
        }

        CommandSourceStack source = context.getSource();
        CommandSender sender = source.getSender();
        Location location = source.getLocation();
        Vec3 pos = getArgOrDefault(() -> position(context, "position"), new Vec3(location.getX(), location.getY(), location.getZ()));
        Vec2 facing = getArgOrDefault(() -> rotation(context, "direction"), new Vec2(location.getYaw(), location.getPitch()));
        World world = getArgOrDefault(() -> context.getArgument("dimension", World.class), location.getWorld());

        GameType mode = GameType.CREATIVE;
        boolean flying = false;
        if (source.getExecutor() instanceof Player executor) {
            ServerPlayer handle = ((CraftPlayer) executor).getHandle();
            mode = handle.gameMode.getGameModeForPlayer();
            flying = handle.getAbilities().flying;
        }
        GameMode gameMode = getArgOrDefault(() -> context.getArgument("gamemode", GameMode.class), null);
        if (gameMode != null) {
            mode = GameType.byId(gameMode.getValue());
        }

        if (mode == GameType.SPECTATOR) {
            // Force override flying to true for spectator players, or they will fell out of the world.
            flying = true;
        } else if (mode.isSurvival()) {
            // Force override flying to false for survival-like players, or they will fly too
            flying = false;
        }
        String playerName = StringArgumentType.getString(context, "player");
        if (playerName.length() > SharedConstants.MAX_PLAYER_NAME_LENGTH) {
            sender.sendMessage(Component.text("Player name: " + playerName + " is too long", NamedTextColor.RED, TextDecoration.BOLD));
            return 0;
        }

        if (!Level.isInSpawnableBounds(BlockPos.containing(pos))) {
            sender.sendMessage(Component.text("Player " + playerName + " cannot be placed outside of the world", NamedTextColor.RED, TextDecoration.BOLD));
            return 0;
        }
        boolean success = FakePlayer.createFake(playerName, MinecraftServer.getServer(), pos, facing.x, facing.y,
                ((CraftWorld) world).getHandle().dimension(), mode, flying);
        if (!success) {
            sender.sendMessage(Component.text("Player " + playerName + " doesn't exist and cannot spawn in online mode. "
                    + "Turn the server offline or the allowSpawningOfflinePlayers on to spawn non-existing players", NamedTextColor.RED, TextDecoration.BOLD));
            return 0;
        }
        return Command.SINGLE_SUCCESS;
    }

    private int manipulate(CommandContext<CommandSourceStack> context, Consumer<PlayerActionPack> action) {
        if (cantManipulate(context)) {
            return 0;
        }
        ServerPlayer player = getPlayer(context);
        action.accept(ActionPacks.get(player));
        return Command.SINGLE_SUCCESS;
    }

    private Command<CommandSourceStack> manipulation(Consumer<PlayerActionPack> action) {
        return c -> manipulate(c, action);
    }

    private int shadow(CommandContext<CommandSourceStack> context) {
        if (cantManipulate(context)) {
            return 0;
        }

        ServerPlayer player = getPlayer(context);
        if (player instanceof FakePlayer) {
            error(context.getSource().getSender(), "Cannot shadow fake players");
            return 0;
        }

        FakePlayer.createShadow(MinecraftServer.getServer(), player);
        return Command.SINGLE_SUCCESS;
    }

}
