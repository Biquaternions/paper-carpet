package me.biquaternions.carpet.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import lombok.experimental.UtilityClass;
import me.biquaternions.carpet.helper.HopperCounter;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.World;

@UtilityClass
@SuppressWarnings({"SameReturnValue", "UnusedReturnValue", "SameParameterValue"})
public class CounterCommand {

    public LiteralCommandNode<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal("counter")
                .requires(s -> s.getSender().hasPermission("carpet.counter"))
                .executes(c -> CounterCommand.listAllCounters(c.getSource(), false))
                .then(Commands.literal("reset")
                        .executes(c -> CounterCommand.resetCounters(c.getSource()))
                );

        for (DyeColor color : DyeColor.values()) {
            builder.then(Commands.literal(color.name())
                    .executes(c -> CounterCommand.displayCounter(c.getSource(), color, false))
                    .then(Commands.literal("reset")
                            .then(Commands.literal("reset")
                                    .executes(c -> CounterCommand.resetCounter(c.getSource(), color))
                            )
                            .then(Commands.literal("realtime")
                                    .executes(c ->CounterCommand.displayCounter(c.getSource(), color, true))
                            )
                    )
            );
        }

        return builder.build();
    }

    /**
     * A method to prettily display the contents of a counter to the player
     * @param color The counter color whose contents we are querying.
     * @param realtime Whether or not to display it as in-game time or IRL time, which accounts for less than 20TPS which
     *                would make it slower than IRL
     */
    private int displayCounter(CommandSourceStack source, DyeColor color, boolean realtime) {
        for (World world : Bukkit.getWorlds()) {
            CounterCommand.displayCounter(source, world, color, realtime);
        }
        return Command.SINGLE_SUCCESS;
    }

    /**
     * A method to prettily display the contents of a counter to the player
     * @param world World from which to locate the counters
     * @param color The counter color whose contents we are querying.
     * @param realtime Whether or not to display it as in-game time or IRL time, which accounts for less than 20TPS which
     *                would make it slower than IRL
     */
    private int displayCounter(CommandSourceStack source, World world, DyeColor color, boolean realtime) {
        HopperCounter counter = HopperCounter.getCounter(world, color);
        if (counter == null) {
            return Command.SINGLE_SUCCESS;
        }

        for (Component message : counter.format(world, realtime, false)) {
            source.getSender().sendMessage(message);
        }
        return Command.SINGLE_SUCCESS;
    }

    private int resetCounters(CommandSourceStack source) {
        HopperCounter.resetAll(false);
        source.getSender().sendMessage("w Restarted all counters");
        return Command.SINGLE_SUCCESS;
    }

    /**
     * A method to reset the counter's timer to 0 and empty its items
     *
     * @param color The counter whose contents we want to reset
     */
    private int resetCounter(CommandSourceStack source, DyeColor color) {
        for (World world : Bukkit.getWorlds()) {
            CounterCommand.resetCounter(source, world, color);
        }
        return Command.SINGLE_SUCCESS;
    }

    /**
     * A method to reset the counter's timer to 0 and empty its items
     *
     * @param world World from which to locate the counters
     * @param color The counter whose contents we want to reset
     */
    private int resetCounter(CommandSourceStack source, World world, DyeColor color) {
        HopperCounter counter = HopperCounter.getCounter(world, color);
        if (counter == null) {
            return Command.SINGLE_SUCCESS;
        }

        counter.reset(world);
        source.getSender().sendMessage("w Restarted " + color + " counter");
        return Command.SINGLE_SUCCESS;
    }

    /**
     * A method to prettily display all the counters to the player
     * @param realtime Whether or not to display it as in-game time or IRL time, which accounts for less than 20TPS which
     *                would make it slower than IRL
     */
    private int listAllCounters(CommandSourceStack source, boolean realtime) {
        for (Component message: HopperCounter.formatAll(realtime)) {
            source.getSender().sendMessage(message);
        }
        return Command.SINGLE_SUCCESS;
    }

}
