package me.biquaternions.carpet.helper;

import lombok.experimental.UtilityClass;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.EndRodBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Port of fabric-carpet's {@code BlockRotator}, used by the {@code flippinCactus} and {@code rotatorBlock} rules.
 */
@UtilityClass
public class BlockRotator {

    /**
     * Rotates the block in front of a dispenser.
     *
     * @param level the level
     * @param dispenserPos the position of the dispenser
     * @param sourceFace the direction the dispenser is facing
     */
    public void dispenserRotate(Level level, BlockPos dispenserPos, Direction sourceFace) {
        BlockPos blockpos = dispenserPos.relative(sourceFace);
        BlockState blockstate = level.getBlockState(blockpos);
        Block block = blockstate.getBlock();

        // Block rotation for blocks that can be placed in all 6 or 4 rotations.
        if (block instanceof DirectionalBlock || block instanceof DispenserBlock) {
            Direction face = blockstate.getValue(DirectionalBlock.FACING);
            if (block instanceof PistonBaseBlock && (
                    blockstate.getValue(PistonBaseBlock.EXTENDED)
                            || ((level.hasNeighborSignal(blockpos) || level.hasNeighborSignal(blockpos.above())) && (new PistonStructureResolver(level, blockpos, face, true)).resolve())
            )) {
                return;
            }

            Direction rotatedFace = face.getClockWise(sourceFace.getAxis());
            if (sourceFace.get3DDataValue() % 2 == 0 || rotatedFace == face) {
                // Flip to make blocks always rotate clockwise relative to the dispenser
                // when index is equal to zero. when index is equal to zero the dispenser is in the opposite direction.
                rotatedFace = rotatedFace.getOpposite();
            }
            level.setBlock(blockpos, blockstate.setValue(DirectionalBlock.FACING, rotatedFace), Block.UPDATE_ALL);
        } else if (block instanceof HorizontalDirectionalBlock) { // Block rotation for blocks that can be placed in only 4 horizontal rotations.
            if (block instanceof BedBlock) {
                return;
            }
            Direction face = blockstate.getValue(HorizontalDirectionalBlock.FACING).getClockWise(Direction.Axis.Y);

            if (sourceFace == Direction.DOWN) { // same as above.
                face = face.getOpposite();
            }
            level.setBlock(blockpos, blockstate.setValue(HorizontalDirectionalBlock.FACING, face), Block.UPDATE_ALL);
        } else if (block == Blocks.HOPPER) {
            Direction face = blockstate.getValue(HopperBlock.FACING);
            if (face != Direction.DOWN) {
                face = face.getClockWise(Direction.Axis.Y);
                level.setBlock(blockpos, blockstate.setValue(HopperBlock.FACING, face), Block.UPDATE_ALL);
            }
        }
        // Send block update to the block that just have been rotated.
        level.neighborChanged(blockpos, block, null);
    }

    /**
     * Flips the clicked block, without causing block updates.
     *
     * @param hitVec the hit position, relative to the block position
     * @return whether the block was flipped
     */
    public boolean flipBlock(Level level, BlockPos pos, Direction facing, Vec3 hitVec) {
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        BlockState newState = null;
        if ((block instanceof HorizontalDirectionalBlock || block instanceof BaseRailBlock) && !(block instanceof BedBlock)) {
            newState = state.rotate(Rotation.CLOCKWISE_90);
        } else if (block instanceof ObserverBlock || block instanceof EndRodBlock) {
            newState = state.setValue(DirectionalBlock.FACING, state.getValue(DirectionalBlock.FACING).getOpposite());
        } else if (block instanceof DispenserBlock) {
            newState = state.setValue(DispenserBlock.FACING, state.getValue(DispenserBlock.FACING).getOpposite());
        } else if (block instanceof PistonBaseBlock) {
            if (!(state.getValue(PistonBaseBlock.EXTENDED))) {
                newState = state.setValue(DirectionalBlock.FACING, state.getValue(DirectionalBlock.FACING).getOpposite());
            }
        } else if (block instanceof SlabBlock) {
            if (state.getValue(SlabBlock.TYPE) != SlabType.DOUBLE) {
                newState = state.setValue(SlabBlock.TYPE, state.getValue(SlabBlock.TYPE) == SlabType.TOP ? SlabType.BOTTOM : SlabType.TOP);
            }
        } else if (block instanceof HopperBlock) {
            if (state.getValue(HopperBlock.FACING) != Direction.DOWN) {
                newState = state.setValue(HopperBlock.FACING, state.getValue(HopperBlock.FACING).getClockWise());
            }
        } else if (block instanceof StairBlock) {
            if ((facing == Direction.UP && hitVec.y == 1.0f) || (facing == Direction.DOWN && hitVec.y == 0.0f)) {
                newState = state.setValue(StairBlock.HALF, state.getValue(StairBlock.HALF) == Half.TOP ? Half.BOTTOM : Half.TOP);
            } else {
                boolean turnCounterClockwise = switch (facing) {
                    case NORTH -> (hitVec.x <= 0.5);
                    case SOUTH -> !(hitVec.x <= 0.5);
                    case EAST -> (hitVec.z <= 0.5);
                    case WEST -> !(hitVec.z <= 0.5);
                    default -> false;
                };
                newState = state.rotate(turnCounterClockwise ? Rotation.COUNTERCLOCKWISE_90 : Rotation.CLOCKWISE_90);
            }
        } else if (block instanceof RotatedPillarBlock) {
            newState = state.setValue(RotatedPillarBlock.AXIS, switch (state.getValue(RotatedPillarBlock.AXIS)) {
                case X -> Direction.Axis.Z;
                case Y -> Direction.Axis.X;
                case Z -> Direction.Axis.Y;
            });
        }
        return applyFlip(level, pos, state, newState);
    }

    private boolean applyFlip(Level level, BlockPos pos, BlockState state, @Nullable BlockState newState) {
        if (newState == null) {
            return false;
        }
        level.setBlock(pos, newState, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        level.setBlocksDirty(pos, state, newState);
        return true;
    }

}
