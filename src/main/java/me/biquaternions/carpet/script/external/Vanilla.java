package me.biquaternions.carpet.script.external;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import me.biquaternions.carpet.CarpetSettings;
import me.biquaternions.carpet.PaperCarpet;
import me.biquaternions.carpet.network.ServerNetworkHandler;
import me.biquaternions.carpet.script.CarpetScriptServer;
import me.biquaternions.carpet.script.EntityEventsGroup;
import me.biquaternions.carpet.script.value.MapValue;
import me.biquaternions.carpet.script.value.StringValue;
import me.biquaternions.carpet.script.value.Value;
import me.biquaternions.carpet.script.value.ValueConversions;
import me.biquaternions.carpet.utils.CommandHelper;
import net.minecraft.SharedConstants;
import net.minecraft.util.Util;
import net.minecraft.commands.CommandResultCallback;
import net.minecraft.commands.CommandSigningContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.commands.arguments.blocks.BlockInput;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.TaskChainer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PortalProcessor;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemorySlot;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.PotentialCalculator;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * <p>Bridge between scarpet and Minecraft internals.</p>
 *
 * <p>In fabric-carpet these calls go through mixin accessors and fields added to vanilla classes. On Paper, private
 * members are reached through {@link Reflect reflection}, and the data fabric-carpet stores in vanilla classes
 * (event containers, permanent vehicles, temporary AI tasks) lives in weak maps keyed by the entity.</p>
 */
public class Vanilla {

    private static @Nullable CarpetScriptServer scriptServer;
    private static final ThreadLocal<Boolean> SKIP_GENERATION_CHECKS = ThreadLocal.withInitial(() -> false);
    private static final Map<Entity, EntityEventsGroup> EVENT_CONTAINERS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Entity, Boolean> PERMANENT_VEHICLES = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Mob, Map<String, Goal>> TEMPORARY_TASKS = Collections.synchronizedMap(new WeakHashMap<>());

    public static void MinecraftServer_forceTick(MinecraftServer server, BooleanSupplier sup) {
        long now = Util.getNanos();
        Reflect.set(MinecraftServer.class, server, "nextTickTimeNanos", now);
        Reflect.set(MinecraftServer.class, server, "lastOverloadWarningNanos", now);
        Reflect.invoke(Reflect.method(MinecraftServer.class, "tickServer", BooleanSupplier.class), server, sup);
        Method pollTask = Reflect.method(MinecraftServer.class, "pollTask");
        Reflect.invoke(pollTask, server);
        while (Boolean.TRUE.equals(Reflect.invoke(pollTask, server))) {
            Thread.yield();
        }
    }

    public static void ChunkMap_relightChunk(ChunkMap chunkMap, ChunkPos pos) {
    }

    public static Map<String, Integer> ChunkMap_regenerateChunkRegion(ChunkMap chunkMap, List<ChunkPos> requestedChunks) {
        return Map.of();
    }

    public static int NaturalSpawner_MAGIC_NUMBER() {
        return (int) Math.pow(17.0D, 2.0D);
    }

    public static PotentialCalculator SpawnState_getPotentialCalculator(NaturalSpawner.SpawnState spawnState) {
        return Reflect.get(NaturalSpawner.SpawnState.class, spawnState, "spawnPotential");
    }

    public static void Objective_setCriterion(Objective objective, ObjectiveCriteria criterion) {
        Reflect.set(Objective.class, objective, "criteria", criterion);
    }

    public static Map<ObjectiveCriteria, List<Objective>> Scoreboard_getObjectivesByCriterion(Scoreboard scoreboard) {
        return Reflect.get(Scoreboard.class, scoreboard, "objectivesByCriteria");
    }

    /**
     * Paper's chunk system replaces the ticket storage, its tickets are collected through {@code forEachTicket}.
     */
    public static Long2ObjectOpenHashMap<List<Ticket>> ChunkTicketManager_getTicketsByPosition(DistanceManager ticketManager) {
        Long2ObjectOpenHashMap<List<Ticket>> result = new Long2ObjectOpenHashMap<>();
        try {
            TicketStorage storage = Reflect.get(DistanceManager.class, ticketManager, "ticketStorage");
            BiConsumer<ChunkPos, Ticket> collector = (pos, ticket) -> result.computeIfAbsent(pos.pack(), k -> new ArrayList<>()).add(ticket);
            Reflect.invoke(Reflect.method(TicketStorage.class, "forEachTicket", BiConsumer.class), storage, collector);
        } catch (IllegalStateException ignored) {
        }
        return result;
    }

    public static CompoundTag BlockInput_getTag(BlockInput blockInput) {
        return Reflect.get(BlockInput.class, blockInput, "tag");
    }

    public static @Nullable CarpetScriptServer MinecraftServer_getScriptServer(MinecraftServer server) {
        return scriptServer;
    }

    public static void MinecraftServer_setScriptServer(@Nullable CarpetScriptServer server) {
        scriptServer = server;
    }

    public static Biome.ClimateSettings Biome_getClimateSettings(Biome biome) {
        return Reflect.get(Biome.class, biome, "climateSettings");
    }

    public static ThreadLocal<Boolean> skipGenerationChecks(ServerLevel level) {
        return SKIP_GENERATION_CHECKS;
    }

    public static void sendScarpetShapesDataToPlayer(ServerPlayer player, Tag data) {
        PaperCarpet plugin = PaperCarpet.getInstance();
        ServerNetworkHandler handler = plugin == null ? null : plugin.getNetworkHandler();
        if (handler != null) {
            handler.sendCustomCommand(player.getBukkitEntity(), "scShapes", data);
        }
    }

    public static PermissionSet MinecraftServer_getRunPermissionLevel(MinecraftServer server) {
        return CarpetSettings.runPermissionLevel;
    }

    public static void CommandSourceStack_setupPrivates(CommandSourceStack css, boolean silent, CommandResultCallback commandResultCallback, EntityAnchorArgument.Anchor anchor, CommandSigningContext commandSigningContext, TaskChainer taskChainer) {
        Reflect.set(CommandSourceStack.class, css, "silent", silent);
        Reflect.set(CommandSourceStack.class, css, "resultCallback", commandResultCallback);
        Reflect.set(CommandSourceStack.class, css, "anchor", anchor);
        Reflect.set(CommandSourceStack.class, css, "signingContext", commandSigningContext);
        Reflect.set(CommandSourceStack.class, css, "chatMessageChainer", taskChainer);
    }

    public static CommandSourceStack.NamesProvider CommandSourceStack_getNamesProvider(CommandSourceStack css) {
        return Reflect.get(CommandSourceStack.class, css, "namesProvider");
    }

    public static int[] MinecraftServer_getReleaseTarget(MinecraftServer server) {
        String[] parts = SharedConstants.getCurrentVersion().name().split("[.\\-]");
        int major = parts.length > 1 ? parseOr(parts[1], 0) : 0;
        int minor = parts.length > 2 ? parseOr(parts[2], 0) : 0;
        return new int[]{major, minor};
    }

    private static int parseOr(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static boolean isDevelopmentEnvironment() {
        return Boolean.getBoolean("carpet.debug");
    }

    /**
     * Plugins take the place of mods on Paper.
     */
    public static MapValue getServerMods(MinecraftServer server) {
        Map<Value, Value> ret = new HashMap<>();
        for (Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
            ret.put(new StringValue(plugin.getName().toLowerCase(java.util.Locale.ROOT)), new StringValue(plugin.getPluginMeta().getVersion()));
        }
        ret.put(new StringValue("minecraft"), new StringValue(SharedConstants.getCurrentVersion().name()));
        ret.put(new StringValue("paper"), new StringValue(Bukkit.getVersion()));
        return MapValue.wrap(ret);
    }

    public static Map<MemoryModuleType<?>, MemorySlot<?>> Brain_getMemories(Brain<?> brain) {
        return Reflect.get(Brain.class, brain, "memories");
    }

    public static LevelStorageSource.LevelStorageAccess MinecraftServer_storageSource(MinecraftServer server) {
        return Reflect.get(MinecraftServer.class, server, "storageSource");
    }

    public static @Nullable BlockPos ServerPlayerGameMode_getCurrentBlockPosition(ServerPlayerGameMode gameMode) {
        if (!(boolean) Reflect.get(ServerPlayerGameMode.class, gameMode, "isDestroyingBlock")) {
            return null;
        }
        return Reflect.get(ServerPlayerGameMode.class, gameMode, "destroyPos");
    }

    public static int ServerPlayerGameMode_getCurrentBlockBreakingProgress(ServerPlayerGameMode gameMode) {
        if (!(boolean) Reflect.get(ServerPlayerGameMode.class, gameMode, "isDestroyingBlock")) {
            return -1;
        }
        return Reflect.get(ServerPlayerGameMode.class, gameMode, "lastSentState");
    }

    public static void ServerPlayerGameMode_setBlockBreakingProgress(ServerPlayerGameMode gameMode, int progress) {
        int state = Mth.clamp(progress, -1, 10);
        Reflect.set(ServerPlayerGameMode.class, gameMode, "lastSentState", state);
        ServerLevel level = Reflect.get(ServerPlayerGameMode.class, gameMode, "level");
        BlockPos destroyPos = Reflect.get(ServerPlayerGameMode.class, gameMode, "destroyPos");
        ServerPlayer player = Reflect.get(ServerPlayerGameMode.class, gameMode, "player");
        level.destroyBlockProgress(-1 * player.getId(), destroyPos, state);
    }

    /**
     * fabric-carpet flags the old player object when the player respawns. On Paper the object that's no longer in the
     * player list is the invalid one.
     */
    public static boolean ServerPlayer_isInvalidEntityObject(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        return server.getPlayerList().getPlayer(player.getUUID()) != player;
    }

    public static GoalSelector Mob_getAI(Mob mob, boolean target) {
        return Reflect.get(Mob.class, mob, target ? "targetSelector" : "goalSelector");
    }

    public static Map<String, Goal> Mob_getTemporaryTasks(Mob mob) {
        return TEMPORARY_TASKS.computeIfAbsent(mob, k -> new HashMap<>());
    }

    public static void Mob_setPersistence(Mob mob, boolean what) {
        Reflect.set(Mob.class, mob, "persistenceRequired", what);
    }

    public static EntityEventsGroup Entity_getEventContainer(Entity entity) {
        return EVENT_CONTAINERS.computeIfAbsent(entity, EntityEventsGroup::new);
    }

    public static @Nullable EntityEventsGroup Entity_getEventContainerIfPresent(Entity entity) {
        return EVENT_CONTAINERS.get(entity);
    }

    public static boolean Entity_isPermanentVehicle(Entity entity) {
        return PERMANENT_VEHICLES.getOrDefault(entity, false);
    }

    public static void Entity_setPermanentVehicle(Entity entity, boolean permanent) {
        if (permanent) {
            PERMANENT_VEHICLES.put(entity, true);
        } else {
            PERMANENT_VEHICLES.remove(entity);
        }
    }

    public static int Entity_getPortalTimer(Entity entity) {
        PortalProcessor processor = entity.portalProcess;
        return processor == null ? 0 : processor.getPortalTime();
    }

    public static void Entity_setPortalTimer(Entity entity, int amount) {
        PortalProcessor processor = entity.portalProcess;
        if (processor != null) {
            Reflect.set(PortalProcessor.class, processor, "portalTime", amount);
        }
    }

    public static int Entity_getPublicNetherPortalCooldown(Entity entity) {
        return Reflect.get(Entity.class, entity, "portalCooldown");
    }

    public static void Entity_setPublicNetherPortalCooldown(Entity entity, int what) {
        Reflect.set(Entity.class, entity, "portalCooldown", what);
    }

    public static int ItemEntity_getPickupDelay(ItemEntity entity) {
        return Reflect.get(ItemEntity.class, entity, "pickupDelay");
    }

    public static boolean LivingEntity_isJumping(LivingEntity entity) {
        return Reflect.get(LivingEntity.class, entity, "jumping");
    }

    public static void LivingEntity_setJumping(LivingEntity entity) {
        entity.jumpFromGround();
    }

    public static Container AbstractHorse_getInventory(AbstractHorse horse) {
        return Reflect.get(AbstractHorse.class, horse, "inventory");
    }

    public static DataSlot AbstractContainerMenu_getDataSlot(AbstractContainerMenu handler, int index) {
        List<DataSlot> slots = Reflect.get(AbstractContainerMenu.class, handler, "dataSlots");
        return slots.get(index);
    }

    public static void CommandDispatcher_unregisterCommand(CommandDispatcher<CommandSourceStack> dispatcher, String name) {
        CommandNode<CommandSourceStack> root = dispatcher.getRoot();
        Reflect.<Map<String, ?>>get(CommandNode.class, root, "children").remove(name);
        Reflect.<Map<String, ?>>get(CommandNode.class, root, "literals").remove(name);
        Reflect.<Map<String, ?>>get(CommandNode.class, root, "arguments").remove(name);
    }

    public static boolean MinecraftServer_doScriptsAutoload(MinecraftServer server) {
        return CarpetSettings.scriptsAutoload;
    }

    public static void MinecraftServer_notifyPlayersCommandsChanged(MinecraftServer server) {
        CommandHelper.notifyPlayersCommandsChanged();
    }

    public static boolean ScriptServer_scriptOptimizations(MinecraftServer scriptServer) {
        return CarpetSettings.scriptsOptimization;
    }

    public static boolean ScriptServer_scriptDebugging(MinecraftServer server) {
        return CarpetSettings.scriptsDebugging;
    }

    public static boolean ServerPlayer_canScriptACE(CommandSourceStack player) {
        return CommandHelper.canUseCommand(player, CarpetSettings.commandScriptACE);
    }

    public static boolean ServerPlayer_canScriptGeneral(CommandSourceStack player) {
        return CommandHelper.canUseCommand(player, CarpetSettings.commandScript, "carpet.script");
    }

    public static int PoiRecord_getFreeTickets(PoiRecord record) {
        return Reflect.get(PoiRecord.class, record, "freeTickets");
    }

    public static void PoiRecord_callAcquireTicket(PoiRecord record) {
        Reflect.invoke(Reflect.method(PoiRecord.class, "acquireTicket"), record);
    }

    public static double FoodData_getExhaustion(FoodData foodData) {
        return Reflect.<Float>get(FoodData.class, foodData, "exhaustionLevel");
    }

    public static void FoodData_setExhaustion(FoodData foodData, float exhaustion) {
        Reflect.set(FoodData.class, foodData, "exhaustionLevel", exhaustion);
    }

    public static PermissionSet defaultRunPermissionLevel() {
        return LevelBasedPermissionSet.GAMEMASTER;
    }

    public record BlockPredicatePayload(@Nullable BlockState state, @Nullable TagKey<Block> tagKey, Map<Value, Value> properties, @Nullable CompoundTag tag) {
        private static final String BLOCK_PREDICATE = "net.minecraft.commands.arguments.blocks.BlockPredicateArgument$BlockPredicate";
        private static final String TAG_PREDICATE = "net.minecraft.commands.arguments.blocks.BlockPredicateArgument$TagPredicate";

        public static BlockPredicatePayload of(Predicate<BlockInWorld> blockPredicate) {
            Class<?> type = blockPredicate.getClass();
            if (type.getName().equals(BLOCK_PREDICATE)) {
                BlockState state = Reflect.get(type, blockPredicate, "state");
                Set<Property<?>> properties = Reflect.get(type, blockPredicate, "properties");
                CompoundTag nbt = Reflect.get(type, blockPredicate, "nbt");
                Map<Value, Value> props = properties.stream().collect(Collectors.toMap(
                        p -> StringValue.of(p.getName()),
                        p -> ValueConversions.fromProperty(state, p),
                        (a, b) -> b
                ));
                return new BlockPredicatePayload(state, null, props, nbt);
            }
            if (type.getName().equals(TAG_PREDICATE)) {
                HolderSet<Block> tag = Reflect.get(type, blockPredicate, "tag");
                Map<String, String> vagueProperties = Reflect.get(type, blockPredicate, "vagueProperties");
                CompoundTag nbt = Reflect.get(type, blockPredicate, "nbt");
                Map<Value, Value> props = new HashMap<>();
                vagueProperties.forEach((k, v) -> props.put(StringValue.of(k), StringValue.of(v)));
                return new BlockPredicatePayload(null, tag.unwrap().left().orElse(null), props, nbt);
            }
            return new BlockPredicatePayload(null, null, Map.of(), null);
        }
    }

}
