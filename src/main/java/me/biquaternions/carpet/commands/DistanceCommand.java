package me.biquaternions.carpet.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.BlockPositionResolver;
import io.papermc.paper.math.BlockPosition;
import lombok.experimental.UtilityClass;
import me.biquaternions.carpet.utils.DistanceCalculator;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

@NullMarked
@UtilityClass
@SuppressWarnings("UnstableApiUsage")
public class DistanceCommand {


    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("distance")
                .requires(s -> s.getSender() instanceof Player player && player.hasPermission("carpet.distance"))
                .then(Commands.literal("from")
                        .executes(c -> {
                            Player player = (Player) c.getSource().getSender();
                            DistanceCalculator.setStart(player, player.getLocation().toBlock());
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.argument("from", ArgumentTypes.blockPosition())
                                .executes( c -> {
                                    Player player = (Player) c.getSource().getSender();
                                    BlockPositionResolver positionResolver = c.getArgument("from", BlockPositionResolver.class);
                                    BlockPosition position = positionResolver.resolve(c.getSource());
                                    DistanceCalculator.setStart(player, position);
                                    return Command.SINGLE_SUCCESS;
                                })
                                .then(Commands.literal("to")
                                        .executes(c -> {
                                            Player player = (Player) c.getSource().getSender();
                                            BlockPositionResolver positionResolver = c.getArgument("from", BlockPositionResolver.class);
                                            BlockPosition position = positionResolver.resolve(c.getSource());
                                            DistanceCalculator.distance(player, position, player.getLocation().toBlock());
                                            return Command.SINGLE_SUCCESS;
                                        })
                                        .then(Commands.argument("to", ArgumentTypes.blockPosition())
                                                .executes(c -> {
                                                    Player player = (Player) c.getSource().getSender();
                                                    BlockPositionResolver fromResolver = c.getArgument("from", BlockPositionResolver.class);
                                                    BlockPosition from = fromResolver.resolve(c.getSource());
                                                    BlockPositionResolver toResolver = c.getArgument("to", BlockPositionResolver.class);
                                                    BlockPosition to = toResolver.resolve(c.getSource());
                                                    DistanceCalculator.distance(player, from, to);
                                                    return Command.SINGLE_SUCCESS;
                                                })
                                        )
                                )
                        )
                )
                .then(Commands.literal("to")
                        .executes(c -> {
                            Player player = (Player) c.getSource().getSender();
                            DistanceCalculator.setEnd(player, player.getLocation().toBlock());
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.argument("to", ArgumentTypes.blockPosition())
                                .executes(c -> {
                                    Player player = (Player) c.getSource().getSender();
                                    BlockPositionResolver toResolver = c.getArgument("to", BlockPositionResolver.class);
                                    BlockPosition to = toResolver.resolve(c.getSource());
                                    DistanceCalculator.setEnd(player, to);
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                )
                .build();
    }

}
