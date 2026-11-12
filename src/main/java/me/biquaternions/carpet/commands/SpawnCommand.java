package me.biquaternions.carpet.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.MessageComponentSerializer;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.BlockPositionResolver;
import io.papermc.paper.math.BlockPosition;
import lombok.experimental.UtilityClass;
import me.biquaternions.carpet.CarpetSettings;
import me.biquaternions.carpet.helper.HopperCounter;
import me.biquaternions.carpet.utils.CommandHelper;
import me.biquaternions.carpet.utils.SpawnReporter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.util.CraftSpawnCategory;
import org.bukkit.entity.SpawnCategory;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * <p>Port of fabric-carpet's {@code /spawn} command.</p>
 *
 * <p>{@code /spawn mobcaps} redirects to Paper's built-in {@code /paper mobcaps}, and {@code /spawn rates} was dropped as
 * Paper has no way of running the spawning algorithm more than once per tick.</p>
 */
@NullMarked
@UtilityClass
@SuppressWarnings("UnstableApiUsage")
public class SpawnCommand {

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("spawn")
                .requires(CommandHelper.commandPermission(() -> CarpetSettings.commandSpawn, "carpet.spawn"))
                .then(Commands.literal("list")
                        .then(Commands.argument("pos", ArgumentTypes.blockPosition())
                                .executes(c -> listSpawns(c.getSource(), c.getArgument("pos", BlockPositionResolver.class).resolve(c.getSource())))))
                .then(Commands.literal("tracking")
                        .executes(c -> printTrackingReport(c.getSource()))
                        .then(Commands.literal("start")
                                .executes(c -> startTracking(c.getSource(), null))
                                .then(Commands.argument("from", ArgumentTypes.blockPosition())
                                        .then(Commands.argument("to", ArgumentTypes.blockPosition())
                                                .executes(c -> {
                                                    BlockPosition from = c.getArgument("from", BlockPositionResolver.class).resolve(c.getSource());
                                                    BlockPosition to = c.getArgument("to", BlockPositionResolver.class).resolve(c.getSource());
                                                    return startTracking(c.getSource(), BoundingBox.fromCorners(toNms(from), toNms(to)));
                                                }))))
                        .then(Commands.literal("stop")
                                .executes(c -> stopTracking(c.getSource())))
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests((c, b) -> suggestCategories(b))
                                .executes(c -> recentSpawnsForType(c.getSource(), StringArgumentType.getString(c, "type")))))
                .then(Commands.literal("test")
                        .executes(c -> runTest(c.getSource(), 72000, null))
                        .then(Commands.argument("ticks", IntegerArgumentType.integer(10))
                                .executes(c -> runTest(c.getSource(), IntegerArgumentType.getInteger(c, "ticks"), null))
                                .then(Commands.argument("counter", StringArgumentType.word())
                                        .suggests((c, b) -> suggest(Arrays.stream(DyeColor.values()).map(d -> d.name().toLowerCase(Locale.ROOT)).toList(), b))
                                        .executes(c -> runTest(c.getSource(), IntegerArgumentType.getInteger(c, "ticks"), StringArgumentType.getString(c, "counter"))))))
                .then(Commands.literal("mocking")
                        .then(Commands.argument("to do or not to do?", BoolArgumentType.bool())
                                .executes(c -> toggleMocking(c.getSource(), BoolArgumentType.getBool(c, "to do or not to do?")))))
                .then(Commands.literal("mobcaps")
                        .executes(c -> generalMobcaps(c.getSource(), null))
                        .then(Commands.literal("set")
                                .then(Commands.argument("cap (hostile)", IntegerArgumentType.integer(1, 1400))
                                        .executes(c -> setMobcaps(c.getSource(), IntegerArgumentType.getInteger(c, "cap (hostile)")))))
                        .then(Commands.argument("dimension", ArgumentTypes.world())
                                .executes(c -> generalMobcaps(c.getSource(), c.getArgument("dimension", World.class)))))
                .then(Commands.literal("entities")
                        .executes(c -> generalMobcaps(c.getSource(), null))
                        .then(Commands.argument("type", StringArgumentType.string())
                                .suggests((c, b) -> suggestCategories(b))
                                .executes(c -> listEntitiesOfType(c.getSource(), StringArgumentType.getString(c, "type"), false))
                                .then(Commands.literal("all")
                                        .executes(c -> listEntitiesOfType(c.getSource(), StringArgumentType.getString(c, "type"), true)))))
                .build();
    }

    private CompletableFuture<Suggestions> suggest(Iterable<String> values, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                builder.suggest(value);
            }
        }
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestCategories(SuggestionsBuilder builder) {
        return suggest(Arrays.stream(SpawnReporter.cachedMobCategories()).map(MobCategory::getName).toList(), builder);
    }

    private BlockPos toNms(BlockPosition position) {
        return new BlockPos(position.blockX(), position.blockY(), position.blockZ());
    }

    private ServerLevel level(CommandSourceStack source) {
        return ((CraftWorld) source.getLocation().getWorld()).getHandle();
    }

    private void send(CommandSender sender, List<Component> components) {
        components.forEach(sender::sendMessage);
    }

    private MobCategory getCategory(String string) throws CommandSyntaxException {
        for (MobCategory category : SpawnReporter.cachedMobCategories()) {
            if (category.getName().equalsIgnoreCase(string)) {
                return category;
            }
        }
        String valid = Arrays.stream(SpawnReporter.cachedMobCategories()).map(MobCategory::getName).collect(Collectors.joining(", "));
        throw new SimpleCommandExceptionType(MessageComponentSerializer.message().serialize(
                Component.text("Wrong mob type: " + string + " should be " + valid, NamedTextColor.RED))).create();
    }

    private int listSpawns(CommandSourceStack source, BlockPosition pos) {
        send(source.getSender(), SpawnReporter.report(toNms(pos), level(source)));
        return Command.SINGLE_SUCCESS;
    }

    private int printTrackingReport(CommandSourceStack source) {
        send(source.getSender(), SpawnReporter.makeTrackingReport(level(source)));
        return Command.SINGLE_SUCCESS;
    }

    private int startTracking(CommandSourceStack source, @Nullable BoundingBox filter) {
        if (SpawnReporter.trackingSpawns()) {
            source.getSender().sendMessage(Component.text("You are already tracking spawning.", NamedTextColor.RED));
            return 0;
        }
        SpawnReporter.startTracking(MinecraftServer.getServer(), filter);
        source.getSender().sendMessage(Component.text("Spawning tracking started.", NamedTextColor.GRAY, TextDecoration.ITALIC));
        return Command.SINGLE_SUCCESS;
    }

    private int stopTracking(CommandSourceStack source) {
        send(source.getSender(), SpawnReporter.makeTrackingReport(level(source)));
        SpawnReporter.stopTracking();
        source.getSender().sendMessage(Component.text("Spawning tracking stopped.", NamedTextColor.GRAY, TextDecoration.ITALIC));
        return Command.SINGLE_SUCCESS;
    }

    private int recentSpawnsForType(CommandSourceStack source, String mobType) throws CommandSyntaxException {
        MobCategory cat = getCategory(mobType);
        send(source.getSender(), SpawnReporter.getRecentSpawns(level(source), cat));
        return Command.SINGLE_SUCCESS;
    }

    private int runTest(CommandSourceStack source, int ticks, @Nullable String counter) {
        // Start tracking
        SpawnReporter.startTracking(MinecraftServer.getServer(), null);
        // Reset counter
        if (counter == null) {
            HopperCounter.resetAll(false);
        } else {
            for (World world : Bukkit.getWorlds()) {
                HopperCounter hCounter = HopperCounter.getCounter(world, counter);
                if (hCounter != null) {
                    hCounter.reset(world);
                }
            }
        }

        Bukkit.getServerTickManager().requestGameToSprint(ticks);
        source.getSender().sendMessage(Component.text(String.format("Started spawn test for %d ticks", ticks), NamedTextColor.GRAY, TextDecoration.ITALIC));
        return Command.SINGLE_SUCCESS;
    }

    private int toggleMocking(CommandSourceStack source, boolean domock) {
        SpawnReporter.mockSpawns = domock;
        if (domock) {
            source.getSender().sendMessage(Component.text("Mob spawns will now be mocked.", NamedTextColor.GRAY, TextDecoration.ITALIC));
        } else {
            source.getSender().sendMessage(Component.text("Normal mob spawning.", NamedTextColor.GRAY, TextDecoration.ITALIC));
        }
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Redirects to Paper's built-in {@code /paper mobcaps} command.
     */
    private int generalMobcaps(CommandSourceStack source, @Nullable World world) {
        CommandSender sender = source.getSender();
        String command = world == null ? "paper mobcaps" : "paper mobcaps " + world.getName();
        if (!Bukkit.dispatchCommand(sender, command)) {
            Location location = source.getLocation();
            ServerLevel level = ((CraftWorld) (world == null ? location.getWorld() : world)).getHandle();
            send(sender, SpawnReporter.printMobcapsForDimension(level, true));
        }
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Changes the spawn limits of every world, scaling every category the same way as the hostile one.
     */
    private int setMobcaps(CommandSourceStack source, int hostileCap) {
        double ratio = (double) hostileCap / MobCategory.MONSTER.getMaxInstancesPerChunk();
        for (World world : Bukkit.getWorlds()) {
            for (SpawnCategory category : SpawnCategory.values()) {
                if (!CraftSpawnCategory.isValidForLimits(category)) {
                    continue;
                }
                int base = CraftSpawnCategory.toNMS(category).getMaxInstancesPerChunk();
                world.setSpawnLimit(category, Math.max(1, (int) Math.round(base * ratio)));
            }
        }
        source.getSender().sendMessage(Component.text(String.format("Mobcaps for hostile mobs changed to %d, other groups will follow", hostileCap), NamedTextColor.GRAY, TextDecoration.ITALIC));
        return Command.SINGLE_SUCCESS;
    }

    private int listEntitiesOfType(CommandSourceStack source, String mobType, boolean all) throws CommandSyntaxException {
        MobCategory cat = getCategory(mobType);
        send(source.getSender(), SpawnReporter.printEntitiesByType(cat, level(source), all));
        return Command.SINGLE_SUCCESS;
    }

}
