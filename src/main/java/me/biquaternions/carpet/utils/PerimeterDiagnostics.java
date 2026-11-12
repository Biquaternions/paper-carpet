package me.biquaternions.carpet.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.animal.fish.WaterAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class PerimeterDiagnostics {

    public static class Result {
        public int liquid;
        public int ground;
        public int specific;
        public final List<BlockPos> samples = new ArrayList<>();
    }

    private MobSpawnSettings.@Nullable SpawnerData sle;
    private final ServerLevel worldServer;
    private final @Nullable MobCategory ctype;
    private final @Nullable Mob el;

    private PerimeterDiagnostics(ServerLevel server, @Nullable MobCategory ctype, @Nullable Mob el) {
        this.sle = null;
        this.worldServer = server;
        this.ctype = ctype;
        this.el = el;
    }

    public static Result countSpots(ServerLevel worldserver, BlockPos epos, @Nullable Mob el) {
        BlockPos pos;
        int eY = epos.getY();
        int eX = epos.getX();
        int eZ = epos.getZ();
        Result result = new Result();

        boolean addWater = false;
        boolean addGround = false;
        MobCategory ctype = null;

        if (el != null) {
            if (el instanceof WaterAnimal) {
                addWater = true;
                ctype = MobCategory.WATER_CREATURE;
            } else if (el instanceof AgeableMob) {
                addGround = true;
                ctype = MobCategory.CREATURE;
            } else if (el instanceof Enemy) {
                addGround = true;
                ctype = MobCategory.MONSTER;
            } else if (el instanceof AmbientCreature) {
                ctype = MobCategory.AMBIENT;
            }
        }
        PerimeterDiagnostics diagnostic = new PerimeterDiagnostics(worldserver, ctype, el);
        EntityType<?> type = EntityTypes.ZOMBIE;
        if (el != null) {
            type = el.getType();
        }
        int minY = worldserver.getMinY();
        int maxY = worldserver.getMaxY();
        for (int x = -128; x <= 128; ++x) {
            for (int z = -128; z <= 128; ++z) {
                if (x * x + z * z > 128 * 128) { // cut out a cylinder first
                    continue;
                }
                for (int y = minY; y < maxY; ++y) {
                    if ((Math.abs(y - eY) > 128)) {
                        continue;
                    }
                    int distsq = (x) * (x) + (eY - y) * (eY - y) + (z) * (z);
                    if (distsq > 128 * 128 || distsq < 24 * 24) {
                        continue;
                    }
                    pos = new BlockPos(eX + x, y, eZ + z);

                    BlockState iblockstate = worldserver.getBlockState(pos);
                    BlockState iblockstateDown = worldserver.getBlockState(pos.below());
                    BlockState iblockstateUp = worldserver.getBlockState(pos.above());

                    if (iblockstate.getFluidState().is(FluidTags.WATER) && !iblockstateUp.isRedstoneConductor(worldserver, pos)) { // isSimpleFullBlock
                        result.liquid++;
                        if (addWater && diagnostic.checkEntitySpawn(pos)) {
                            result.specific++;
                            if (result.samples.size() < 10) {
                                result.samples.add(pos);
                            }
                        }
                    } else {
                        if (iblockstateDown.isRedstoneConductor(worldserver, pos)) { // isSimpleFullBlock
                            Block block = iblockstateDown.getBlock();
                            boolean flag = block != Blocks.BEDROCK && block != Blocks.BARRIER;
                            if (flag && NaturalSpawner.isValidEmptySpawnBlock(worldserver, pos, iblockstate, iblockstate.getFluidState(), type)
                                    && NaturalSpawner.isValidEmptySpawnBlock(worldserver, pos.above(), iblockstateUp, iblockstateUp.getFluidState(), type)) {
                                result.ground++;
                                if (addGround && diagnostic.checkEntitySpawn(pos)) {
                                    result.specific++;
                                    if (result.samples.size() < 10) {
                                        result.samples.add(pos);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return result;
    }

    private List<Weighted<MobSpawnSettings.SpawnerData>> mobsAt(BlockPos pos) {
        return this.worldServer.getChunkSource().getGenerator().getMobsAt(this.worldServer, this.worldServer.structureManager(), this.ctype, pos).unwrap();
    }

    private boolean containsSle(BlockPos pos) {
        for (Weighted<MobSpawnSettings.SpawnerData> weighted : mobsAt(pos)) {
            if (weighted.value().equals(this.sle)) {
                return true;
            }
        }
        return false;
    }

    private boolean checkEntitySpawn(BlockPos pos) {
        if (this.el == null || this.ctype == null) {
            return false;
        }
        if (this.sle == null || !containsSle(pos)) {
            this.sle = null;
            for (Weighted<MobSpawnSettings.SpawnerData> wsle : mobsAt(pos)) {
                MobSpawnSettings.SpawnerData data = wsle.value();
                if (this.el.getType() == data.type()) {
                    this.sle = data;
                    break;
                }
            }
            if (this.sle == null || !containsSle(pos)) {
                return false;
            }
        }

        if (SpawnPlacements.isSpawnPositionOk(this.sle.type(), this.worldServer, pos)) {
            this.el.snapTo(pos.getX() + 0.5F, pos.getY(), pos.getZ() + 0.5F, 0.0F, 0.0F);
            return this.el.checkSpawnObstruction(this.worldServer) && this.el.checkSpawnRules(this.worldServer, EntitySpawnReason.NATURAL) &&
                    SpawnPlacements.checkSpawnRules(this.el.getType(), this.worldServer, EntitySpawnReason.NATURAL, this.el.blockPosition(), this.worldServer.getRandom()) &&
                    this.worldServer.noCollision(this.el); // check collision rules once they stop fiddling with them after 1.14.1
        }
        return false;
    }

}
