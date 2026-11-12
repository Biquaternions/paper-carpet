package me.biquaternions.carpet.utils;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import lombok.experimental.UtilityClass;
import me.biquaternions.carpet.CarpetServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.structures.NetherFortressStructure;
import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.DyeColor;
import org.bukkit.craftbukkit.util.CraftSpawnCategory;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * <p>Port of fabric-carpet's spawn reporter.</p>
 *
 * <p>Spawn tracking hooks into {@link org.bukkit.event.entity.CreatureSpawnEvent} with the {@code NATURAL} spawn reason,
 * which means that only successful spawns can be tracked: the amount of spawn attempts, failed attempts and full mobcap ticks
 * that fabric-carpet reports are not available.</p>
 */
@UtilityClass
public class SpawnReporter {

    private final MobCategory[] CACHED_MOBCATEGORY_VALUES = MobCategory.values();
    public boolean mockSpawns = false;

    public final Map<Pair<ResourceKey<Level>, MobCategory>, Object2LongOpenHashMap<EntityType<?>>> spawn_stats = new HashMap<>();
    public final Object2LongOpenHashMap<Pair<ResourceKey<Level>, MobCategory>> spawn_counts = new Object2LongOpenHashMap<>();
    public final Map<Pair<ResourceKey<Level>, MobCategory>, EvictingQueue<Pair<EntityType<?>, BlockPos>>> spawned_mobs = new HashMap<>();

    private int spawnTrackingStartTime = 0;
    private @Nullable BoundingBox trackedSpawningArea = null;

    public MobCategory[] cachedMobCategories() {
        return CACHED_MOBCATEGORY_VALUES;
    }

    public void registerSpawn(Mob mob, BlockPos pos) {
        if (!trackingSpawns()) {
            return;
        }
        if (trackedSpawningArea != null && !trackedSpawningArea.isInside(pos)) {
            return;
        }
        MobCategory cat = mob.getType().getCategory();
        Pair<ResourceKey<Level>, MobCategory> key = Pair.of(mob.level().dimension(), cat);
        spawn_stats.computeIfAbsent(key, k -> new Object2LongOpenHashMap<>()).addTo(mob.getType(), 1);
        spawned_mobs.computeIfAbsent(key, k -> new EvictingQueue<>()).put(Pair.of(mob.getType(), pos));
        spawn_counts.addTo(key, 1);
    }

    public List<Component> printMobcapsForDimension(ServerLevel world, boolean multiline) {
        ResourceKey<Level> dim = world.dimension();
        String name = dim.identifier().getPath();
        List<Component> lst = new ArrayList<>();
        if (multiline) {
            lst.add(Component.text(String.format("Mobcaps for %s:", name)));
        }
        NaturalSpawner.SpawnState lastSpawner = world.getChunkSource().getLastSpawnState();
        if (lastSpawner == null) {
            lst.add(Component.text("  --UNAVAILABLE--", NamedTextColor.GRAY));
            return lst;
        }
        Object2IntMap<MobCategory> dimCounts = lastSpawner.getMobCategoryCounts();
        int chunkCount = lastSpawner.getSpawnableChunkCount();

        Component shortLine = Component.empty();
        boolean first = true;
        for (MobCategory category : cachedMobCategories()) {
            int cur = dimCounts.getOrDefault(category, -1);
            int max = NaturalSpawner.globalLimitForCategory(world, category, chunkCount);
            TextColor color = ComponentTool.heatmapColor(cur, max);
            TextColor mobColor = ComponentTool.creatureTypeColor(CraftSpawnCategory.toBukkit(category));
            if (multiline) {
                lst.add(Component.text(String.format("  %s: ", category.getName()), NamedTextColor.WHITE)
                        .append(cur < 0 ? Component.text("-", NamedTextColor.GRAY) : Component.text(cur, color))
                        .append(Component.text(" / ", NamedTextColor.GRAY))
                        .append(Component.text(max, mobColor)));
            } else {
                if (!first) {
                    shortLine = shortLine.append(Component.text(",", NamedTextColor.GRAY));
                }
                first = false;
                shortLine = shortLine.append(Component.text(cur < 0 ? "-" : String.valueOf(cur), color))
                        .append(Component.text("/", NamedTextColor.GRAY))
                        .append(Component.text(max, mobColor));
            }
        }
        if (!multiline) {
            lst.add(shortLine);
        }
        return lst;
    }

    public List<Component> getRecentSpawns(Level world, MobCategory category) {
        List<Component> lst = new ArrayList<>();
        if (!trackingSpawns()) {
            lst.add(Component.text("Spawn tracking not started"));
            return lst;
        }
        String categoryName = category.getName();

        lst.add(Component.text(String.format("Recent %s spawns:", categoryName)));
        EvictingQueue<Pair<EntityType<?>, BlockPos>> queue = spawned_mobs.get(Pair.of(world.dimension(), category));
        if (queue != null) {
            for (Pair<EntityType<?>, BlockPos> pair : queue.keySet()) {
                BlockPos pos = pair.getRight();
                lst.add(Component.text(" - ", NamedTextColor.WHITE)
                        .append(ComponentTool.tp(Style.style(NamedTextColor.WHITE, TextDecoration.BOLD), pos.getX(), pos.getY(), pos.getZ()))
                        .append(Component.text(": ", NamedTextColor.WHITE))
                        .append(Component.translatable(pair.getLeft().getDescriptionId(), NamedTextColor.WHITE)));
            }
        }

        if (lst.size() == 1) {
            lst.add(Component.text(" - Nothing spawned yet, sorry."));
        }
        return lst;
    }

    public List<Component> handleWoolAction(BlockPos pos, ServerLevel worldIn) {
        DyeColor under = WoolTool.getWoolColor(worldIn.getWorld().getBlockAt(pos.getX(), pos.getY() - 1, pos.getZ()));
        if (under == null) {
            return trackingSpawns() ? makeTrackingReport(worldIn) : printMobcapsForDimension(worldIn, true);
        }
        MobCategory category = getCategoryFromWoolColor(under);
        if (category != null) {
            return trackingSpawns() ? getRecentSpawns(worldIn, category) : printEntitiesByType(category, worldIn, true);
        }
        return trackingSpawns() ? makeTrackingReport(worldIn) : printMobcapsForDimension(worldIn, true);
    }

    public @Nullable MobCategory getCategoryFromWoolColor(DyeColor color) {
        return switch (color) {
            case RED -> MobCategory.MONSTER;
            case GREEN -> MobCategory.CREATURE;
            case BLUE -> MobCategory.WATER_CREATURE;
            case BROWN -> MobCategory.AMBIENT;
            case CYAN -> MobCategory.WATER_AMBIENT;
            default -> null;
        };
    }

    public List<Component> printEntitiesByType(MobCategory cat, ServerLevel worldIn, boolean all) {
        List<Component> lst = new ArrayList<>();
        lst.add(Component.text(String.format("Loaded entities for %s category:", cat.getName())));
        for (Entity entity : worldIn.getEntities(EntityTypeTest.forClass(Entity.class), (e) -> e.getType().getCategory() == cat)) {
            boolean persistent = entity instanceof Mob mob && (mob.isPersistenceRequired() || mob.requiresCustomPersistence());
            if (!all && persistent) {
                continue;
            }

            EntityType<?> type = entity.getType();
            BlockPos pos = entity.blockPosition();
            NamedTextColor color = persistent ? NamedTextColor.GRAY : NamedTextColor.WHITE;
            lst.add(Component.text(" - ", NamedTextColor.WHITE)
                    .append(ComponentTool.tp(Style.style(color, TextDecoration.BOLD), pos.getX(), pos.getY(), pos.getZ()))
                    .append(Component.text(": ", color))
                    .append(Component.translatable(type.getDescriptionId(), color)));
        }
        if (lst.size() == 1) {
            lst.add(Component.text(" - Empty."));
        }
        return lst;
    }

    public void resetSpawnStats() {
        spawn_stats.clear();
        spawn_counts.clear();
        spawned_mobs.clear();
        spawnTrackingStartTime = 0;
    }

    public boolean trackingSpawns() {
        return spawnTrackingStartTime != 0L;
    }

    public void startTracking(MinecraftServer server, @Nullable BoundingBox trackedArea) {
        resetSpawnStats();
        spawnTrackingStartTime = Math.max(server.getTickCount(), 1);
        trackedSpawningArea = trackedArea;
    }

    public void stopTracking() {
        resetSpawnStats();
        spawnTrackingStartTime = 0;
        trackedSpawningArea = null;
    }

    private String getWorldCode(ResourceKey<Level> world) {
        if (world == Level.OVERWORLD) {
            return "";
        }
        String path = world.identifier().getPath();
        return "(" + Character.toUpperCase(path.startsWith("the_") ? path.charAt("the_".length()) : path.charAt(0)) + ")";
    }

    public List<Component> makeTrackingReport(Level worldIn) {
        List<Component> report = new ArrayList<>();
        if (!trackingSpawns()) {
            report.add(Component.text("Spawn tracking is disabled, type '", NamedTextColor.WHITE)
                    .append(Component.text("/spawn tracking start", NamedTextColor.WHITE, TextDecoration.ITALIC)
                            .clickEvent(ClickEvent.suggestCommand("/spawn tracking start")))
                    .append(Component.text("' to enable", NamedTextColor.WHITE)));
            return report;
        }
        MinecraftServer server = worldIn.getServer();
        int duration = Math.max(server.getTickCount() - spawnTrackingStartTime, 1);
        report.add(Component.text("--------------------", NamedTextColor.WHITE, TextDecoration.BOLD));
        String simulated = mockSpawns ? "[SIMULATED] " : "";
        String location = (trackedSpawningArea != null) ? String.format("[in (%d, %d, %d)x(%d, %d, %d)]",
                trackedSpawningArea.minX(), trackedSpawningArea.minY(), trackedSpawningArea.minZ(),
                trackedSpawningArea.maxX(), trackedSpawningArea.maxY(), trackedSpawningArea.maxZ()) : "";
        report.add(Component.text(String.format("%sSpawn statistics %s: for %.1f min", simulated, location, (duration / 72000.0) * 60)));

        for (MobCategory category : cachedMobCategories()) {
            for (ResourceKey<Level> dim : server.levelKeys()) {
                Pair<ResourceKey<Level>, MobCategory> key = Pair.of(dim, category);
                long spawns = spawn_counts.getLong(key);
                if (spawns > 0L) {
                    report.add(Component.text(String.format(" > %s%s: %d spawns, %.1f per hour",
                            category.getName().substring(0, 3), getWorldCode(dim),
                            spawns, 72000.0 * spawns / duration)));
                    Object2LongOpenHashMap<EntityType<?>> stats = spawn_stats.get(key);
                    if (stats == null) {
                        continue;
                    }
                    for (Object2LongMap.Entry<EntityType<?>> entry : stats.object2LongEntrySet()) {
                        report.add(Component.text("   - ")
                                .append(Component.translatable(entry.getKey().getDescriptionId()))
                                .append(Component.text(String.format(": %d spawns, %d per hour",
                                        entry.getLongValue(), (72000 * entry.getLongValue() / duration)))));
                    }
                }
            }
        }
        return report;
    }

    public void killEntity(Entity entity) {
        // Some entities, like Parched and the Warm Chicken Variant also
        // require special treatment due to new multi-passenger Jockeys (Camel Husk, etc)
        List<Entity> toRemove = new ArrayList<>();
        if (entity.isPassenger()) {
            Entity vehicle = entity.getVehicle();
            if (vehicle != null) {
                toRemove.addAll(vehicle.getPassengers());
                toRemove.add(vehicle);
            }
        }
        if (entity.isVehicle()) {
            toRemove.addAll(entity.getPassengers());
        }
        if (entity instanceof Chicken) {
            for (Entity e : entity.level().getEntities(entity, entity.getBoundingBox())) {
                if (!(e instanceof ServerPlayer)) {
                    e.discard();
                }
            }
        }
        entity.discard();
        toRemove.forEach(SpawnReporter::killEntity);
    }

    private WeightedList<MobSpawnSettings.SpawnerData> getSpawnEntries(ServerLevel serverLevel, StructureManager structureManager, ChunkGenerator chunkGenerator, MobCategory mobCategory, BlockPos blockPos, @Nullable Holder<Biome> holder) {
        return NaturalSpawner.isInNetherFortressBounds(blockPos, serverLevel, mobCategory, structureManager) ? NetherFortressStructure.FORTRESS_ENEMIES : chunkGenerator.getMobsAt(serverLevel, structureManager, mobCategory, blockPos);
    }

    public List<Component> report(BlockPos pos, ServerLevel worldIn) {
        List<Component> rep = new ArrayList<>();
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        ChunkAccess chunk = worldIn.getChunk(pos);
        int lc = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) + 1;
        String relativeHeight = (y == lc) ? "right at it." : String.format("%d blocks %s it.", Mth.abs(y - lc), (y >= lc) ? "above" : "below");
        rep.add(Component.text(String.format("Maximum spawn Y value for (%+d, %+d) is %d. You are " + relativeHeight, x, z, lc)));
        rep.add(Component.text("Spawns:"));
        for (MobCategory category : cachedMobCategories()) {
            String categoryCode = category.getName().substring(0, 3).toUpperCase(Locale.ROOT);
            WeightedList<MobSpawnSettings.SpawnerData> lst = getSpawnEntries(worldIn, worldIn.structureManager(), worldIn.getChunkSource().getGenerator(), category, pos, worldIn.getBiome(pos));
            if (lst == null || lst.isEmpty()) {
                continue;
            }
            for (Weighted<MobSpawnSettings.SpawnerData> wspawnEntry : lst.unwrap()) {
                MobSpawnSettings.SpawnerData spawnEntry = wspawnEntry.value();
                if (SpawnPlacements.getPlacementType(spawnEntry.type()) == null) {
                    continue; // vanilla bug
                }
                boolean canSpawn = SpawnPlacements.isSpawnPositionOk(spawnEntry.type(), worldIn, pos);
                int willSpawn = -1;
                boolean fits = false;

                Mob mob;
                try {
                    mob = (Mob) spawnEntry.type().create(worldIn, EntitySpawnReason.NATURAL);
                } catch (Exception e) {
                    CarpetServer.LOG.warning("Exception while creating mob for spawn reporter: " + e);
                    return rep;
                }
                if (mob == null) {
                    continue;
                }

                if (canSpawn) {
                    willSpawn = 0;
                    for (int attempt = 0; attempt < 50; ++attempt) {
                        float f = x + 0.5F;
                        float f1 = z + 0.5F;
                        mob.snapTo(f, y, f1, worldIn.getRandom().nextFloat() * 360.0F, 0.0F);
                        fits = worldIn.noCollision(mob);
                        EntityType<?> etype = mob.getType();

                        for (int i = 0; i < 20; ++i) {
                            if (SpawnPlacements.checkSpawnRules(etype, worldIn, EntitySpawnReason.NATURAL, pos, worldIn.getRandom()) &&
                                    SpawnPlacements.isSpawnPositionOk(etype, worldIn, pos) &&
                                    mob.checkSpawnRules(worldIn, EntitySpawnReason.NATURAL)) {
                                if (etype == EntityTypes.OCELOT) {
                                    BlockState blockState = worldIn.getBlockState(pos.below());
                                    if ((pos.getY() < worldIn.getSeaLevel()) || !(blockState.is(Blocks.GRASS_BLOCK) || blockState.is(BlockTags.LEAVES))) {
                                        continue;
                                    }
                                }
                                willSpawn += 1;
                            }
                        }
                        mob.finalizeSpawn(worldIn, worldIn.getCurrentDifficultyAt(mob.blockPosition()), EntitySpawnReason.NATURAL, null);
                        // the code invokes onInitialSpawn after getCanSpawHere
                        fits = fits && worldIn.noCollision(mob);

                        killEntity(mob);

                        try {
                            mob = (Mob) spawnEntry.type().create(worldIn, EntitySpawnReason.NATURAL);
                        } catch (Exception e) {
                            CarpetServer.LOG.warning("Exception while creating mob for spawn reporter: " + e);
                            return rep;
                        }
                        if (mob == null) {
                            break;
                        }
                    }
                }
                if (mob == null) {
                    continue;
                }

                Component mobTypeName = Component.translatable(mob.getType().getDescriptionId());
                int weight = wspawnEntry.weight();
                String counts = String.format(" (%d:%d-%d/%d), can: ", weight, spawnEntry.count().minInclusive(), spawnEntry.count().maxInclusive(), mob.getMaxSpawnClusterSize());
                if (canSpawn) {
                    Style style = (fits && willSpawn > 0) ? Style.style(NamedTextColor.DARK_GREEN) : Style.style(NamedTextColor.GRAY, TextDecoration.ITALIC);
                    rep.add(Component.text(categoryCode + ": ", style)
                            .append(mobTypeName.style(style))
                            .append(Component.text(counts, style))
                            .append(Component.text("YES", NamedTextColor.GREEN))
                            .append(Component.text(", fit: ", style))
                            .append(fits ? Component.text("YES", NamedTextColor.GREEN) : Component.text("NO", NamedTextColor.RED))
                            .append(Component.text(", will: ", style))
                            .append(Component.text(Math.round((double) willSpawn) / 10 + "%", willSpawn > 0 ? NamedTextColor.GREEN : NamedTextColor.RED)));
                } else {
                    Style style = Style.style(NamedTextColor.GRAY, TextDecoration.ITALIC);
                    rep.add(Component.text(categoryCode + ": ", style)
                            .append(mobTypeName.style(style))
                            .append(Component.text(counts, style))
                            .append(Component.text("NO", NamedTextColor.DARK_RED)));
                }
                killEntity(mob);
            }
        }
        return rep;
    }

}
