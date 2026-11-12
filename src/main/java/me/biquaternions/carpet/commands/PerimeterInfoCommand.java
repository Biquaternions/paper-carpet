package me.biquaternions.carpet.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.BlockPositionResolver;
import io.papermc.paper.math.BlockPosition;
import io.papermc.paper.registry.RegistryKey;
import lombok.experimental.UtilityClass;
import me.biquaternions.carpet.CarpetSettings;
import me.biquaternions.carpet.utils.CommandHelper;
import me.biquaternions.carpet.utils.ComponentTool;
import me.biquaternions.carpet.utils.PerimeterDiagnostics;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftEntityType;
import org.bukkit.entity.EntityType;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

@NullMarked
@UtilityClass
@SuppressWarnings("UnstableApiUsage")
public class PerimeterInfoCommand {

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("perimeterinfo")
                .requires(CommandHelper.commandPermission(() -> CarpetSettings.commandPerimeterInfo, "carpet.perimeterinfo"))
                .executes(c -> {
                    Location location = c.getSource().getLocation();
                    return perimeterDiagnose(c.getSource(), location.getBlockX(), location.getBlockY(), location.getBlockZ(), null);
                })
                .then(Commands.argument("center position", ArgumentTypes.blockPosition())
                        .executes(c -> {
                            BlockPosition pos = c.getArgument("center position", BlockPositionResolver.class).resolve(c.getSource());
                            return perimeterDiagnose(c.getSource(), pos.blockX(), pos.blockY(), pos.blockZ(), null);
                        })
                        .then(Commands.argument("mob", ArgumentTypes.resource(RegistryKey.ENTITY_TYPE))
                                .suggests((c, b) -> suggestSummonable(b))
                                .executes(c -> {
                                    BlockPosition pos = c.getArgument("center position", BlockPositionResolver.class).resolve(c.getSource());
                                    return perimeterDiagnose(c.getSource(), pos.blockX(), pos.blockY(), pos.blockZ(), c.getArgument("mob", EntityType.class));
                                })))
                .build();
    }

    private CompletableFuture<Suggestions> suggestSummonable(SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        Arrays.stream(EntityType.values())
                .filter(type -> type != EntityType.UNKNOWN && type.isSpawnable() && type.isAlive())
                .map(type -> type.getKey().asString())
                .filter(key -> key.startsWith(remaining) || key.substring("minecraft:".length()).startsWith(remaining))
                .forEach(builder::suggest);
        return builder.buildFuture();
    }

    private int perimeterDiagnose(CommandSourceStack source, int x, int y, int z, @Nullable EntityType mobType) {
        CommandSender sender = source.getSender();
        ServerLevel level = ((CraftWorld) source.getLocation().getWorld()).getHandle();
        BlockPos pos = new BlockPos(x, y, z);
        Mob mob = null;
        if (mobType != null) {
            Entity baseEntity = CraftEntityType.bukkitToMinecraft(mobType).create(level, EntitySpawnReason.COMMAND);
            if (!(baseEntity instanceof Mob created)) {
                sender.sendMessage(Component.text("/perimeterinfo requires a mob entity to test against.", NamedTextColor.RED));
                if (baseEntity != null) {
                    baseEntity.discard();
                }
                return 0;
            }
            mob = created;
            mob.snapTo(new BlockPos(x, level.getMinY() - 10, z), mob.getYRot(), mob.getXRot());
        }
        PerimeterDiagnostics.Result res = PerimeterDiagnostics.countSpots(level, pos, mob);

        sender.sendMessage(Component.text("Spawning spaces around ", NamedTextColor.WHITE)
                .append(ComponentTool.tp(NamedTextColor.AQUA, x, y, z))
                .append(Component.text(":", NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("  potential in-liquid: ", NamedTextColor.WHITE).append(Component.text(res.liquid, NamedTextColor.WHITE, TextDecoration.BOLD)));
        sender.sendMessage(Component.text("  potential on-ground: ", NamedTextColor.WHITE).append(Component.text(res.ground, NamedTextColor.WHITE, TextDecoration.BOLD)));
        if (mob != null) {
            sender.sendMessage(Component.text("  ", NamedTextColor.WHITE)
                    .append(Component.translatable(mob.getType().getDescriptionId()))
                    .append(Component.text(": ", NamedTextColor.WHITE))
                    .append(Component.text(res.specific, NamedTextColor.WHITE, TextDecoration.BOLD)));
            res.samples.forEach(bp -> sender.sendMessage(Component.text("  ").append(ComponentTool.tp(NamedTextColor.AQUA, bp.getX(), bp.getY(), bp.getZ()))));
            mob.discard();
        }
        return Command.SINGLE_SUCCESS;
    }

}
