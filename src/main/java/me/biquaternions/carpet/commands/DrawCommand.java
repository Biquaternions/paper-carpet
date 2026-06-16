package me.biquaternions.carpet.commands;

import com.google.common.collect.Lists;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.predicate.BlockInWorldPredicate;
import io.papermc.paper.command.brigadier.argument.resolvers.BlockPositionResolver;
import io.papermc.paper.math.BlockPosition;
import lombok.experimental.UtilityClass;
import me.biquaternions.carpet.configurations.MessageConfiguration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@UtilityClass
@SuppressWarnings("UnstableApiUsage")
public class DrawCommand {

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("draw")
                .requires(s -> s.getSender() instanceof Player && s.getSender().hasPermission("carpet.draw"))
                .then(Commands.literal("sphere")
                        .then(Commands.argument("center", ArgumentTypes.blockPosition())
                                .then(Commands.argument("radius", IntegerArgumentType.integer())
                                        .then(DrawCommand.drawShape(c -> DrawCommand.drawSphere(c, false)))
                                )
                        )
                )
                .then(Commands.literal("ball")
                        .then(Commands.argument("center", ArgumentTypes.blockPosition())
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1))
                                        .then(DrawCommand.drawShape(c -> DrawCommand.drawSphere(c, true)))
                                )
                        )
                )
                .then(Commands.literal("diamond")
                        .then(Commands.argument("center", ArgumentTypes.blockPosition())
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1))
                                        .then(DrawCommand.drawShape(c -> DrawCommand.drawDiamond(c, true)))
                                )
                        )
                )
                .then(Commands.literal("pyramid")
                        .then(Commands.argument("center", ArgumentTypes.blockPosition())
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1))
                                        .then(Commands.argument("height",IntegerArgumentType.integer(1))
                                                .then(Commands.argument("pointing", StringArgumentType.word())
                                                        .suggests( (c, b) -> basicSuggest(b, "up","down"))
                                                        .then(Commands.argument("orientation",StringArgumentType.word())
                                                                .suggests( (c, b) -> basicSuggest(b, "y","x","z"))
                                                                .then(DrawCommand.drawShape(c -> DrawCommand.drawPyramid(c, "square", true)))
                                                        )
                                                )
                                        )
                                )
                        )
                )
                .then(Commands.literal("cone")
                        .then(Commands.argument("center", ArgumentTypes.blockPosition())
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1))
                                        .then(Commands.argument("height",IntegerArgumentType.integer(1))
                                                .then(Commands.argument("pointing",StringArgumentType.word())
                                                        .suggests( (c, b) -> basicSuggest(b, "up","down"))
                                                        .then(Commands.argument("orientation",StringArgumentType.word())
                                                                .suggests( (c, b) -> basicSuggest(b, "y","x","z"))
                                                                .then(DrawCommand.drawShape(c -> DrawCommand.drawPyramid(c, "circle", true)))
                                                        )
                                                )
                                        )
                                )
                        )
                )
                .then(Commands.literal("cylinder")
                        .then(Commands.argument("center", ArgumentTypes.blockPosition())
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1))
                                        .then(Commands.argument("height",IntegerArgumentType.integer(1))
                                                .then(Commands.argument("orientation",StringArgumentType.word())
                                                        .suggests( (c, b) -> basicSuggest(b, "y","x","z"))
                                                        .then(DrawCommand.drawShape(c -> DrawCommand.drawPrism(c, "circle")))
                                                )
                                        )
                                )
                        )
                ).then(Commands.literal("cuboid")
                        .then(Commands.argument("center", ArgumentTypes.blockPosition())
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1))
                                        .then(Commands.argument("height",IntegerArgumentType.integer(1))
                                                .then(Commands.argument("orientation",StringArgumentType.word())
                                                        .suggests( (c, b) -> basicSuggest(b, "y","x","z"))
                                                        .then(DrawCommand.drawShape(c -> DrawCommand.drawPrism(c, "square")))
                                                )
                                        )
                                )
                        )
                )
                .build();
    }

    private CompletableFuture<Suggestions> basicSuggest(SuggestionsBuilder builder, String ...args) {
        for (String arg : args) {
            builder.suggest(arg);
        }
        return builder.buildFuture();
    }

    private RequiredArgumentBuilder<CommandSourceStack, BlockState> drawShape(Command<CommandSourceStack> drawer) {
        return Commands.argument("block", ArgumentTypes.blockState())
                .executes(drawer)
                .then(Commands.literal("replace")
                        .then(Commands.argument("filter", ArgumentTypes.blockInWorldPredicate())
                                .executes(drawer)
                        )
                );
    }

    private double lengthSq(double x, double y, double z) {
        return (x * x) + (y * y) + (z * z);
    }

    private int setBlock(
            World world, Location mbpos, int x, int y, int z,
            BlockState block, BlockInWorldPredicate replacement,
            List<Location> list
    )
    {
        mbpos.set(x, y, z);
        int success=0;
        if (replacement == null || replacement.testBlock(world.getBlockAt(mbpos), true) == BlockInWorldPredicate.Result.TRUE) {
//            BlockEntity tileentity = world.getBlockEntity(mbpos);
//            if (tileentity instanceof Container) {
//                ((Container) tileentity).clearContent();
//            }
            world.getBlockAt(mbpos).setType(block.getType());
            list.add(block.getLocation());
            ++success;
        }

        return success;
    }

    private int drawSphere(CommandContext<CommandSourceStack> ctx, boolean solid) throws CommandSyntaxException {

        BlockPositionResolver positionResolver = ctx.getArgument("center", BlockPositionResolver.class);
        Player player = (Player) ctx.getSource().getSender();

        BlockPosition pos = positionResolver.resolve(ctx.getSource());
        int radius = ctx.getArgument("radius", int.class);
        BlockState block = ctx.getArgument("block", BlockState.class);
        BlockInWorldPredicate replacement = ctx.getArgument("filter", BlockInWorldPredicate.class);

        int affected = 0;
        World world = player.getWorld();

        double radiusX = radius+0.5;
        double radiusY = radius+0.5;
        double radiusZ = radius+0.5;

        final double invRadiusX = 1 / radiusX;
        final double invRadiusY = 1 / radiusY;
        final double invRadiusZ = 1 / radiusZ;

        final int ceilRadiusX = (int) Math.ceil(radiusX);
        final int ceilRadiusY = (int) Math.ceil(radiusY);
        final int ceilRadiusZ = (int) Math.ceil(radiusZ);

        Location location = pos.toLocation(world);
        List<Location> list = Lists.newArrayList();
        double nextXn = 0;

        forX: for (int x = 0; x <= ceilRadiusX; ++x)
        {
            final double xn = nextXn;
            nextXn = (x + 1) * invRadiusX;
            double nextYn = 0;
            forY: for (int y = 0; y <= ceilRadiusY; ++y)
            {
                final double yn = nextYn;
                nextYn = (y + 1) * invRadiusY;
                double nextZn = 0;
                forZ: for (int z = 0; z <= ceilRadiusZ; ++z)
                {
                    final double zn = nextZn;
                    nextZn = (z + 1) * invRadiusZ;

                    double distanceSq = lengthSq(xn, yn, zn);
                    if (distanceSq > 1)
                    {
                        if (z == 0)
                        {
                            if (y == 0)
                            {
                                break forX;
                            }
                            break forY;
                        }
                        break forZ;
                    }

                    if (!solid && lengthSq(nextXn, yn, zn) <= 1 && lengthSq(xn, nextYn, zn) <= 1 && lengthSq(xn, yn, nextZn) <= 1)
                    {
                        continue;
                    }

                    // FIXME: CarpetSettings.impendingFillSkipUpdates.set(!CarpetSettings.fillUpdates);
                    for (int xmod = -1; xmod < 2; xmod += 2)
                    {
                        for (int ymod = -1; ymod < 2; ymod += 2)
                        {
                            for (int zmod = -1; zmod < 2; zmod += 2)
                            {
                                affected+= setBlock(world, location,
                                        pos.blockX() + xmod * x, pos.blockY() + ymod * y, pos.blockZ() + zmod * z,
                                        block, replacement, list
                                );
                            }
                        }
                    }
                    // FIXME: CarpetSettings.impendingFillSkipUpdates.set(false);
                }
            }
        }
//        if (CarpetSettings.fillUpdates) {
//            list.forEach(blockpos1 -> world.updateNeighborsAt(blockpos1, world.getBlockState(blockpos1).getBlock()));
//        }
        player.sendMessage(MessageConfiguration.CMD_DRAW_BLOCKS_FILLED.resolveComponent(Placeholder.parsed("blocks", String.valueOf(affected))));
        return affected;
    }

    private int drawDiamond(CommandContext<CommandSourceStack> ctx, boolean solid) throws CommandSyntaxException {
        BlockPositionResolver positionResolver = ctx.getArgument("center", BlockPositionResolver.class);
        Player player = (Player) ctx.getSource().getSender();

        BlockPosition pos = positionResolver.resolve(ctx.getSource());
        int radius = ctx.getArgument("radius", int.class);
        BlockState block = ctx.getArgument("block", BlockState.class);
        BlockInWorldPredicate replacement = ctx.getArgument("filter", BlockInWorldPredicate.class);

        int affected=0;


        World world = player.getWorld();
        Location location = pos.toLocation(world);
        List<Location> list = Lists.newArrayList();

        // FIXME: CarpetSettings.impendingFillSkipUpdates.set(!CarpetSettings.fillUpdates);

        for (int r = 0; r < radius; ++r)
        {
            int y=r-radius+1;
            for (int x = -r; x <= r; ++x)
            {
                int z=r-Math.abs(x);

                affected+= setBlock(world, location, pos.blockX()+x, pos.blockY()-y, pos.blockZ()+z, block, replacement, list);
                affected+= setBlock(world, location, pos.blockX()+x, pos.blockY()-y, pos.blockZ()-z, block, replacement, list);
                affected+= setBlock(world, location, pos.blockX()+x, pos.blockY()+y, pos.blockZ()+z, block, replacement, list);
                affected+= setBlock(world, location, pos.blockX()+x, pos.blockY()+y, pos.blockZ()-z, block, replacement, list);
            }
        }

        // FIXME: CarpetSettings.impendingFillSkipUpdates.set(false);

//        if (CarpetSettings.fillUpdates)
//        {
//            list.forEach(p -> world.updateNeighborsAt(p, world.getBlockState(p).getBlock()));
//        }

        player.sendMessage(MessageConfiguration.CMD_DRAW_BLOCKS_FILLED.resolveComponent(Placeholder.parsed("blocks", String.valueOf(affected))));
        return affected;
    }

    private int fillFlat(
            World world, BlockPosition pos, int offset, double dr, boolean rectangle, String orientation,
            BlockState block, BlockInWorldPredicate replacement,
            List<Location> list, Location mbpos
    ) {
        int successes=0;
        int r = (int) Math.floor(dr);
        double drsq = dr*dr;
        if (orientation.equalsIgnoreCase("x"))
        {
            for(int a=-r; a<=r; ++a) for(int b=-r; b<=r; ++b) if(rectangle || a*a + b*b <= drsq)
            {
                successes += setBlock(
                        world, mbpos,pos.blockX()+offset, pos.blockY()+a, pos.blockZ()+b,
                        block, replacement, list
                );
            }
            return successes;
        }
        if (orientation.equalsIgnoreCase("y"))
        {
            for(int a=-r; a<=r; ++a) for(int b=-r; b<=r; ++b) if(rectangle || a*a + b*b <= drsq)
            {
                successes += setBlock(
                        world, mbpos,pos.blockX()+a, pos.blockY()+offset, pos.blockZ()+b,
                        block, replacement, list
                );
            }
            return successes;
        }
        if (orientation.equalsIgnoreCase("z"))
        {
            for(int a=-r; a<=r; ++a) for(int b=-r; b<=r; ++b) if(rectangle || a*a + b*b <= drsq)
            {
                successes += setBlock(
                        world, mbpos,pos.blockX()+b, pos.blockY()+a, pos.blockZ()+offset,
                        block, replacement, list
                );
            }
            return successes;
        }
        return 0;
    }

    private int drawPyramid(CommandContext<CommandSourceStack> ctx, String base, boolean solid) throws CommandSyntaxException {
        BlockPositionResolver positionResolver = ctx.getArgument("center", BlockPositionResolver.class);
        Player player = (Player) ctx.getSource().getSender();

        BlockPosition pos = positionResolver.resolve(ctx.getSource());
        double radius = ctx.getArgument("radius", int.class)+0.5D;
        int height = ctx.getArgument("height", int.class);
        boolean pointup = ctx.getArgument("pointing", String.class).equalsIgnoreCase("up");
        String orientation = ctx.getArgument("orientation", String.class);
        BlockState block = ctx.getArgument("block", BlockState.class);
        BlockInWorldPredicate replacement = ctx.getArgument("filter", BlockInWorldPredicate.class);

        CommandSourceStack source = ctx.getSource();

        int affected = 0;
        World world = player.getWorld();
        Location location = pos.toLocation(world);
        List<Location> list = Lists.newArrayList();

        // FIXME: CarpetSettings.impendingFillSkipUpdates.set(!CarpetSettings.fillUpdates);

        boolean isSquare = base.equalsIgnoreCase("square");

        for(int i =0; i<height;++i)
        {
            double r = pointup ? radius - radius * i / height - 1 : radius * i / height;
            affected+= fillFlat(world, pos, i, r, isSquare, orientation, block, replacement, list, location);
        }

        // FIXME: CarpetSettings.impendingFillSkipUpdates.set(false);

//        if (CarpetSettings.fillUpdates) {
//
//            for (BlockPos blockpos1 : list) {
//                Block blokc = world.getBlockState(blockpos1).getBlock();
//                world.updateNeighborsAt(blockpos1, blokc);
//            }
//        }

        player.sendMessage(MessageConfiguration.CMD_DRAW_BLOCKS_FILLED.resolveComponent(Placeholder.parsed("blocks", String.valueOf(affected))));

        return affected;
    }

    private int drawPrism(CommandContext<CommandSourceStack> ctx, String base) throws CommandSyntaxException {
        BlockPositionResolver positionResolver = ctx.getArgument("center", BlockPositionResolver.class);
        Player player = (Player) ctx.getSource().getSender();

        BlockPosition pos = positionResolver.resolve(ctx.getSource());
        double radius = ctx.getArgument("radius", int.class)+0.5D;
        int height = ctx.getArgument("height", int.class);
        String orientation = ctx.getArgument("orientation", String.class);
        BlockState block = ctx.getArgument("block", BlockState.class);
        BlockInWorldPredicate replacement = ctx.getArgument("filter", BlockInWorldPredicate.class);


        int affected = 0;
        World world = player.getWorld();
        Location location = pos.toLocation(world);
        List<Location> list = Lists.newArrayList();

        // FIXME: CarpetSettings.impendingFillSkipUpdates.set(!CarpetSettings.fillUpdates);

        boolean isSquare = base.equalsIgnoreCase("square");

        for(int i =0; i<height;++i)
        {
            affected+= fillFlat(world, pos, i, radius, isSquare, orientation, block, replacement, list, location);
        }

        // FIXME: CarpetSettings.impendingFillSkipUpdates.set(false);

//        if (CarpetSettings.fillUpdates) {
//
//            for (BlockPos blockpos1 : list) {
//                Block blokc = world.getBlockState(blockpos1).getBlock();
//                world.updateNeighborsAt(blockpos1, blokc);
//            }
//        }

        player.sendMessage(MessageConfiguration.CMD_DRAW_BLOCKS_FILLED.resolveComponent(Placeholder.parsed("blocks", String.valueOf(affected))));

        return affected;
    }

}
