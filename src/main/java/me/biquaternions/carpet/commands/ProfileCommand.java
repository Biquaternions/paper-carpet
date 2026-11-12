package me.biquaternions.carpet.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import lombok.experimental.UtilityClass;
import me.biquaternions.carpet.CarpetSettings;
import me.biquaternions.carpet.utils.CarpetProfiler;
import me.biquaternions.carpet.utils.CommandHelper;
import org.jspecify.annotations.NullMarked;

@NullMarked
@UtilityClass
public class ProfileCommand {

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("profile")
                .requires(CommandHelper.commandPermission(() -> CarpetSettings.commandProfile, "carpet.profile"))
                .executes(c -> healthReport(c.getSource(), 100))
                .then(Commands.literal("health")
                        .executes(c -> healthReport(c.getSource(), 100))
                        .then(Commands.argument("ticks", IntegerArgumentType.integer(20, 24000))
                                .executes(c -> healthReport(c.getSource(), IntegerArgumentType.getInteger(c, "ticks")))))
                .then(Commands.literal("entities")
                        .executes(c -> healthEntities(c.getSource(), 100))
                        .then(Commands.argument("ticks", IntegerArgumentType.integer(20, 24000))
                                .executes(c -> healthEntities(c.getSource(), IntegerArgumentType.getInteger(c, "ticks")))))
                .build();
    }

    private int healthReport(CommandSourceStack source, int ticks) {
        CarpetProfiler.prepareTickReport(source.getSender(), ticks);
        return Command.SINGLE_SUCCESS;
    }

    private int healthEntities(CommandSourceStack source, int ticks) {
        CarpetProfiler.prepareEntityReport(source.getSender(), ticks);
        return Command.SINGLE_SUCCESS;
    }

}
