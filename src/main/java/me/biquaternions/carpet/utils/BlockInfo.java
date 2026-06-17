package me.biquaternions.carpet.utils;

import net.kyori.adventure.text.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import org.bukkit.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.block.CraftBlockState;

import java.util.ArrayList;
import java.util.List;

public class BlockInfo {
    public static List<Component> blockInfo(Location location) {
        CraftBlock block = (CraftBlock) location.getBlock();
        CraftBlockState craftState = (CraftBlockState) location.getBlock().getState();
        BlockState state = craftState.getHandle();
        Material material = block.getType();
        BlockData data = block.getBlockData();
        String metastring = data.getAsString();

        CraftWorld world = (CraftWorld) location.getWorld();
        if (world == null) {
            return List.of();
        }

        BlockPos pos = block.getPosition();
        ServerLevel level = world.getHandle();
        NamespacedKey soundBreak = Registry.SOUNDS.getKey(data.getSoundGroup().getBreakSound());
        NamespacedKey soundFall = Registry.SOUNDS.getKey(data.getSoundGroup().getFallSound());
        NamespacedKey soundHit = Registry.SOUNDS.getKey(data.getSoundGroup().getHitSound());
        NamespacedKey soundPlace = Registry.SOUNDS.getKey(data.getSoundGroup().getPlaceSound());
        NamespacedKey soundStep = Registry.SOUNDS.getKey(data.getSoundGroup().getStepSound());
        List<Component> lst = new ArrayList<>();
        lst.add(Component.empty());
        lst.add(Component.text("====================================="));
        lst.add(Component.text(String.format("Block info for %s (id %s):", material.name(), metastring )));
        lst.add(Component.text(" - Map colour: ").append(ColorTool.asComponent(data.getMapColor())));
        lst.add(Component.text(String.format(" - Sound break: %s", soundBreak == null ? null : soundBreak.asString())));
        lst.add(Component.text(String.format(" - Sound fall: %s", soundFall == null ? null : soundFall.asString())));
        lst.add(Component.text(String.format(" - Sound hit: %s", soundHit == null ? null : soundHit.asString())));
        lst.add(Component.text(String.format(" - Sound place: %s", soundPlace == null ? null : soundPlace.asString())));
        lst.add(Component.text(String.format(" - Sound step: %s", soundStep == null ? null : soundStep.asString())));
        lst.add(Component.empty());
        lst.add(Component.text(String.format(" - Full block: %s", state.isCollisionShapeFullBlock(level, pos)))); //  isFullCube() )));
        lst.add(Component.text(String.format(" - Normal cube: %s", state.isRedstoneConductor(level, pos)))); //isNormalCube()))); isSimpleFullBlock
        lst.add(Component.text(String.format(" - Is liquid: %s", block.isLiquid())));
        lst.add(Component.empty());
        lst.add(Component.text(String.format(" - Light in: %d, above: %d",
                Math.max(level.getBrightness(LightLayer.BLOCK, pos), level.getBrightness(LightLayer.SKY, pos)) ,
                Math.max(level.getBrightness(LightLayer.BLOCK, pos.above()), level.getBrightness(LightLayer.SKY, pos.above())))));
        lst.add(Component.text(String.format(" - Brightness in: %.2f, above: %.2f", level.getLightLevelDependentMagicValue(pos), level.getLightLevelDependentMagicValue(pos.above()))));
        lst.add(Component.text(String.format(" - Is opaque: %s", state.starlight$isConditionallyFullOpaque() )));
        //lst.add(Component.text(String.format(" - Light opacity: %d", state.getOpacity(world,pos))));
        //lst.add(Component.text(String.format(" - Emitted light: %d", state.getLightValue())));
        //lst.add(Component.text(String.format(" - Picks neighbour light value: %s", state.useNeighborBrightness(world, pos))));
        lst.add(Component.empty());
        lst.add(Component.text(String.format(" - Causes suffocation: %s", state.isSuffocating(level, pos)))); //canSuffocate
        lst.add(Component.text(String.format(" - Blocks movement on land: %s", !state.isPathfindable(PathComputationType.LAND))));
        lst.add(Component.text(String.format(" - Blocks movement in air: %s", !state.isPathfindable(PathComputationType.AIR))));
        lst.add(Component.text(String.format(" - Blocks movement in liquids: %s", !state.isPathfindable(PathComputationType.WATER))));
        lst.add(Component.text(String.format(" - Can burn: %s", state.ignitedByLava())));
        lst.add(Component.text(String.format(" - Hardness: %.2f", state.getDestroySpeed(level, pos))));
        lst.add(Component.text(String.format(" - Blast resistance: %.2f", state.getBlock().getExplosionResistance())));
        lst.add(Component.text(String.format(" - Ticks randomly: %s", state.isRandomlyTicking())));
        lst.add(Component.empty());
        lst.add(Component.text(String.format(" - Can provide power: %s", state.isSignalSource())));
        lst.add(Component.text(String.format(" - Strong power level: %d", level.getDirectSignalTo(pos))));
        lst.add(Component.text(String.format(" - Redstone power level: %d", level.getBestNeighborSignal(pos))));
        lst.add(Component.empty());
        // FIXME: lst.add(wander_chances(pos.above(), world));

        return lst;
    }

//    private static Component wander_chances(Location pos) {
//        if (!(pos.getWorld() instanceof CraftWorld world)) {
//            return Component.empty();
//        }
//
//        ServerLevel worldIn = world.getHandle();
//        PathfinderMob creature = new ZombifiedPiglin(EntityTypes.ZOMBIFIED_PIGLIN, worldIn);
//        creature.finalizeSpawn(worldIn, worldIn.getCurrentDifficultyAt(pos), EntitySpawnReason.NATURAL, null);
//        creature.snapTo(pos, 0.0F, 0.0F);
//        RandomStrollGoal wander = new RandomStrollGoal(creature, 0.8D);
//
//        int success = 0;
//        for (int i = 0; i < 1000; i++) {
//            Vec3 vec = DefaultRandomPos.getPos(creature, 10, 7); // TargetFinder.findTarget(creature, 10, 7);
//            if (vec == null) {
//                continue;
//            }
//            success++;
//        }
//
//        long total_ticks = 0;
//        for (int trie = 0; trie < 1000; trie++) {
//            int i;
//            for (i = 1; i < 30*20*60; i++) { //*60 used to be 5 hours, limited to 30 mins
//                if (wander.canUse()) {
//                    break;
//                }
//            }
//            total_ticks += 3L * i;
//        }
//
//        creature.discard(); // discarded // remove(Entity.RemovalReason.field_26999); // 2nd option - DISCARDED
//        long total_time = (total_ticks) / 1000 / 20;
//        return Component.text(String.format(" - Wander chance above: %.1f%%\n - Average standby above: %s",
//                (100.0F*success)/1000,
//                ((total_time>5000)?"INFINITY":(total_time +" s"))
//        ));
//    }
}
