package me.biquaternions.carpet.listener;

import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import com.destroystokyo.paper.event.server.ServerTickEndEvent;
import me.biquaternions.carpet.CarpetSettings;
import me.biquaternions.carpet.helper.BlockRotator;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.Vec3;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ElderGuardian;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Guardian;
import org.bukkit.entity.Item;
import org.bukkit.entity.LightningStrike;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.SkeletonHorse;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Wither;
import org.bukkit.entity.WitherSkull;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.TNTPrimeEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerExpCooldownChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.weather.LightningStrikeEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * <p>Implementation of the {@link CarpetSettings} rules that can be done through Paper events.</p>
 */
@NullMarked
@SuppressWarnings("UnstableApiUsage")
public class RulesListener implements Listener {

    // ---------------------------------------------------------------------------------------------------------------
    // persistentParrots
    // ---------------------------------------------------------------------------------------------------------------

    private boolean allowShoulderRelease = false;

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShoulderEntityRelease(final CreatureSpawnEvent event) {
        if (!CarpetSettings.persistentParrots || this.allowShoulderRelease || event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.SHOULDER_ENTITY) {
            return;
        }
        if (!(event.getEntity() instanceof Tameable tameable) || !(tameable.getOwner() instanceof Player player) || !player.isOnline()) {
            return;
        }
        if (player.isDead() || player.getHealth() <= 0) {
            return;
        }
        boolean invulnerable = player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR;
        if ((invulnerable && player.getFallDistance() > 0.5F) || player.isInWater() || player.isFlying() || player.isSleeping() || player.isInPowderedSnow()) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDamage(final EntityDamageEvent event) {
        if (!CarpetSettings.persistentParrots || !(event.getEntity() instanceof Player player) || player.isSneaking()) {
            return;
        }
        double damage = event.getFinalDamage();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        this.allowShoulderRelease = true;
        try {
            if (random.nextFloat() < damage / 15.0) {
                player.releaseLeftShoulderEntity();
            }
            if (random.nextFloat() < damage / 15.0) {
                player.releaseRightShoulderEntity();
            }
        } finally {
            this.allowShoulderRelease = false;
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // xpNoCooldown
    // ---------------------------------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onExpCooldownChange(final PlayerExpCooldownChangeEvent event) {
        if (CarpetSettings.xpNoCooldown) {
            event.setNewCooldown(0);
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // explosionNoBlockDamage & xpFromExplosions
    // ---------------------------------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(final EntityExplodeEvent event) {
        this.handleExplosion(event.getLocation().getWorld(), event.blockList(), event.getEntity());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(final BlockExplodeEvent event) {
        this.handleExplosion(event.getBlock().getWorld(), event.blockList(), null);
    }

    private void handleExplosion(final World world, final List<Block> blocks, @Nullable Entity source) {
        if (CarpetSettings.explosionNoBlockDamage) {
            blocks.clear();
            return;
        }
        if (!CarpetSettings.xpFromExplosions) {
            return;
        }
        ServerLevel level = ((CraftWorld) world).getHandle();
        for (Block block : blocks) {
            BlockPos pos = ((CraftBlock) block).getPosition();
            net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
            int xp = state.getBlock().getExpDrop(state, level, pos, net.minecraft.world.item.ItemStack.EMPTY, true);
            if (xp > 0) {
                Location location = block.getLocation().add(0.5, 0.5, 0.5);
                world.spawn(location, ExperienceOrb.class, orb -> orb.setExperience(xp));
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // TNT: tntPrimerMomentumRemoved, hardcodeTNTangle, mergeTNT, tntDoNotUpdate
    // ---------------------------------------------------------------------------------------------------------------

    private final Map<UUID, Integer> mergedTnt = new HashMap<>();
    private final Set<UUID> movedTnt = new HashSet<>();
    private final Set<Block> placedTnt = new HashSet<>();

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onTntSpawn(final EntitySpawnEvent event) {
        if (!(event.getEntity() instanceof TNTPrimed tnt)) {
            return;
        }
        if (CarpetSettings.hardcodeTNTangle != -1.0D) {
            tnt.setVelocity(new Vector(-Math.sin(CarpetSettings.hardcodeTNTangle) * 0.02, 0.2, -Math.cos(CarpetSettings.hardcodeTNTangle) * 0.02));
        }
        if (CarpetSettings.tntPrimerMomentumRemoved) {
            tnt.setVelocity(new Vector(0.0, 0.20000000298023224D, 0.0));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(final BlockPlaceEvent event) {
        if (CarpetSettings.tntDoNotUpdate && event.getBlockPlaced().getType() == Material.TNT) {
            this.placedTnt.add(event.getBlockPlaced());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTntPrime(final TNTPrimeEvent event) {
        if (CarpetSettings.tntDoNotUpdate && event.getCause() == TNTPrimeEvent.PrimeCause.REDSTONE && this.placedTnt.contains(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTntExplode(final ExplosionPrimeEvent event) {
        if (!(event.getEntity() instanceof TNTPrimed tnt)) {
            return;
        }
        this.movedTnt.remove(tnt.getUniqueId());
        Integer merged = this.mergedTnt.remove(tnt.getUniqueId());
        if (merged == null || merged <= 1) {
            return;
        }
        Location location = tnt.getLocation().add(0, tnt.getHeight() / 16.0F, 0);
        for (int i = 0; i < merged - 1; i++) {
            tnt.getWorld().createExplosion(tnt, location, 4.0F, false, true);
        }
    }

    @EventHandler
    public void onTickEnd(final ServerTickEndEvent event) {
        this.placedTnt.clear();
        if (!CarpetSettings.mergeTNT) {
            if (!this.movedTnt.isEmpty()) {
                this.movedTnt.clear();
            }
            return;
        }
        for (World world : org.bukkit.Bukkit.getWorlds()) {
            for (TNTPrimed tnt : world.getEntitiesByClass(TNTPrimed.class)) {
                if (!tnt.isValid()) {
                    continue;
                }
                Vector velocity = tnt.getVelocity();
                boolean stationary = velocity.getX() == 0 && velocity.getY() == 0 && velocity.getZ() == 0;
                if (!stationary) {
                    // Merge code, merge only tnt that have had a chance to move CARPET-XCOM
                    this.movedTnt.add(tnt.getUniqueId());
                    continue;
                }
                if (!this.movedTnt.remove(tnt.getUniqueId())) {
                    continue;
                }
                Location location = tnt.getLocation();
                for (Entity entity : tnt.getNearbyEntities(0.0, 0.0, 0.0)) {
                    if (!(entity instanceof TNTPrimed other) || !other.isValid()) {
                        continue;
                    }
                    Vector otherVelocity = other.getVelocity();
                    Location otherLocation = other.getLocation();
                    if (otherVelocity.getX() == 0 && otherVelocity.getY() == 0 && otherVelocity.getZ() == 0
                            && location.getX() == otherLocation.getX() && location.getY() == otherLocation.getY() && location.getZ() == otherLocation.getZ()
                            && tnt.getFuseTicks() == other.getFuseTicks()) {
                        int mine = this.mergedTnt.getOrDefault(tnt.getUniqueId(), 1);
                        int theirs = this.mergedTnt.getOrDefault(other.getUniqueId(), 1);
                        this.mergedTnt.put(tnt.getUniqueId(), mine + theirs);
                        this.mergedTnt.remove(other.getUniqueId());
                        this.movedTnt.remove(other.getUniqueId());
                        other.remove();
                    }
                }
            }
        }
        Iterator<UUID> iterator = this.mergedTnt.keySet().iterator();
        while (iterator.hasNext()) {
            Entity entity = org.bukkit.Bukkit.getEntity(iterator.next());
            if (entity == null || !entity.isValid()) {
                iterator.remove();
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // flippinCactus & rotatorBlock
    // ---------------------------------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(final PlayerInteractEvent event) {
        if (!CarpetSettings.flippinCactus || event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        if (block == null || player.getInventory().getItemInMainHand().getType() != Material.CACTUS
                || player.getGameMode() == GameMode.ADVENTURE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        Location point = event.getInteractionPoint();
        Vec3 hitVec = point == null ? new Vec3(0.5, 0.5, 0.5) : new Vec3(point.getX() - block.getX(), point.getY() - block.getY(), point.getZ() - block.getZ());
        Direction facing = toDirection(event.getBlockFace());
        ServerLevel level = ((CraftWorld) block.getWorld()).getHandle();
        if (BlockRotator.flipBlock(level, ((CraftBlock) block).getPosition(), facing, hitVec)) {
            event.setCancelled(true);
        }
    }

    /**
     * Placing directional blocks with a cactus in the offhand places them facing the opposite direction.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlaceWithCactus(final BlockPlaceEvent event) {
        if (!CarpetSettings.flippinCactus || event.getPlayer().getInventory().getItemInOffHand().getType() != Material.CACTUS) {
            return;
        }
        Block block = event.getBlockPlaced();
        if (block.getBlockData() instanceof Directional directional) {
            BlockFace opposite = directional.getFacing().getOppositeFace();
            if (directional.getFaces().contains(opposite)) {
                directional.setFacing(opposite);
                block.setBlockData(directional, false);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDispense(final BlockDispenseEvent event) {
        if (!CarpetSettings.rotatorBlock || event.getItem().getType() != Material.CACTUS || event.getBlock().getType() != Material.DISPENSER) {
            return;
        }
        if (!(event.getBlock().getBlockData() instanceof Directional directional)) {
            return;
        }
        event.setCancelled(true);
        ServerLevel level = ((CraftWorld) event.getBlock().getWorld()).getHandle();
        BlockRotator.dispenserRotate(level, ((CraftBlock) event.getBlock()).getPosition(), toDirection(directional.getFacing()));
    }

    private static Direction toDirection(final BlockFace face) {
        return switch (face) {
            case UP -> Direction.UP;
            case DOWN -> Direction.DOWN;
            case NORTH -> Direction.NORTH;
            case SOUTH -> Direction.SOUTH;
            case EAST -> Direction.EAST;
            case WEST -> Direction.WEST;
            default -> Direction.UP;
        };
    }

    // ---------------------------------------------------------------------------------------------------------------
    // movableAmethyst
    // ---------------------------------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(final BlockBreakEvent event) {
        if (!CarpetSettings.movableAmethyst || event.getBlock().getType() != Material.BUDDING_AMETHYST || !event.isDropItems()) {
            return;
        }
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (Tag.ITEMS_PICKAXES.isTagged(tool.getType()) && tool.getEnchantmentLevel(Enchantment.SILK_TOUCH) > 0) {
            Block block = event.getBlock();
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), new ItemStack(Material.BUDDING_AMETHYST));
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // renewableSponges & lightningKillsDropsFix
    // ---------------------------------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLightningDamage(final EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof LightningStrike)) {
            return;
        }
        Entity entity = event.getEntity();
        if (CarpetSettings.lightningKillsDropsFix && entity instanceof Item item && item.getTicksLived() <= 8) {
            event.setCancelled(true);
            return;
        }
        if (CarpetSettings.renewableSponges && entity instanceof Guardian guardian && !(entity instanceof ElderGuardian) && guardian.isValid()) {
            event.setCancelled(true);
            guardian.getWorld().spawn(guardian.getLocation(), ElderGuardian.class, CreatureSpawnEvent.SpawnReason.LIGHTNING, elder -> {
                elder.setAI(guardian.hasAI());
                if (guardian.customName() != null) {
                    elder.customName(guardian.customName());
                    elder.setCustomNameVisible(guardian.isCustomNameVisible());
                }
            });
            guardian.remove();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLightningCombust(final EntityCombustByEntityEvent event) {
        if (!(event.getCombuster() instanceof LightningStrike)) {
            return;
        }
        Entity entity = event.getEntity();
        if ((CarpetSettings.lightningKillsDropsFix && entity instanceof Item item && item.getTicksLived() <= 8)
                || (CarpetSettings.renewableSponges && entity instanceof Guardian && !(entity instanceof ElderGuardian))) {
            event.setCancelled(true);
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // desertShrubs
    // ---------------------------------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onStructureGrow(final StructureGrowEvent event) {
        if (!CarpetSettings.desertShrubs) {
            return;
        }
        Block block = event.getLocation().getBlock();
        if (!Tag.SAPLINGS.isTagged(block.getType())) {
            return;
        }
        ServerLevel level = ((CraftWorld) block.getWorld()).getHandle();
        BlockPos pos = ((CraftBlock) block).getPosition();
        if (level.getBiome(pos).is(BiomeTags.HAS_DESERT_PYRAMID) && !nearWater(level, pos)) {
            event.setCancelled(true);
            block.setType(Material.DEAD_BUSH);
        }
    }

    private static boolean nearWater(final ServerLevel level, final BlockPos pos) {
        for (BlockPos blockPos : BlockPos.betweenClosed(pos.offset(-4, -4, -4), pos.offset(4, 1, 4))) {
            if (level.getFluidState(blockPos).is(FluidTags.WATER)) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // silverFishDropGravel, structure spawn overrides
    // ---------------------------------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSilverfishSpawn(final CreatureSpawnEvent event) {
        if (CarpetSettings.silverFishDropGravel && event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SILVERFISH_BLOCK) {
            Block block = event.getLocation().getBlock();
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), new ItemStack(Material.GRAVEL));
        }
    }

    private record Override(ResourceKey<Structure> structure, boolean wholeStructure, List<EntityType> types, int[] weights) {
        EntityType pick() {
            int total = 0;
            for (int weight : this.weights) {
                total += weight;
            }
            int roll = ThreadLocalRandom.current().nextInt(total);
            for (int i = 0; i < this.types.size(); i++) {
                roll -= this.weights[i];
                if (roll < 0) {
                    return this.types.get(i);
                }
            }
            return this.types.getLast();
        }
    }

    private static final Override HUSK_OVERRIDE = new Override(BuiltinStructures.DESERT_PYRAMID, true, List.of(EntityType.HUSK), new int[]{1});
    private static final Override SHULKER_OVERRIDE = new Override(BuiltinStructures.END_CITY, false, List.of(EntityType.SHULKER), new int[]{1});
    private static final Override BASTION_OVERRIDE = new Override(BuiltinStructures.BASTION_REMNANT, false,
            List.of(EntityType.PIGLIN_BRUTE, EntityType.PIGLIN, EntityType.HOGLIN), new int[]{5, 10, 2});

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onNaturalSpawn(final CreatureSpawnEvent event) {
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL || !(event.getEntity() instanceof Monster || event.getEntity() instanceof org.bukkit.entity.Shulker || event.getEntity() instanceof org.bukkit.entity.Hoglin)) {
            return;
        }
        if (!CarpetSettings.huskSpawningInTemples && !CarpetSettings.shulkerSpawningInEndCities && !CarpetSettings.piglinsSpawningInBastions) {
            return;
        }
        Location location = event.getLocation();
        ServerLevel level = ((CraftWorld) location.getWorld()).getHandle();
        BlockPos pos = BlockPos.containing(location.getX(), location.getY(), location.getZ());
        Override override = null;
        if (CarpetSettings.huskSpawningInTemples && isInside(level, pos, HUSK_OVERRIDE)) {
            override = HUSK_OVERRIDE;
        } else if (CarpetSettings.shulkerSpawningInEndCities && isInside(level, pos, SHULKER_OVERRIDE)) {
            override = SHULKER_OVERRIDE;
        } else if (CarpetSettings.piglinsSpawningInBastions && isInside(level, pos, BASTION_OVERRIDE)) {
            override = BASTION_OVERRIDE;
        }
        if (override == null || override.types().contains(event.getEntityType())) {
            return;
        }
        event.setCancelled(true);
        EntityType replacement = override.pick();
        Class<? extends Entity> entityClass = Objects.requireNonNull(replacement.getEntityClass());
        location.getWorld().spawn(location, entityClass, CreatureSpawnEvent.SpawnReason.NATURAL, e -> {
        });
    }

    private static boolean isInside(final ServerLevel level, final BlockPos pos, final Override override) {
        Structure structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(override.structure());
        if (structure == null) {
            return false;
        }
        StructureManager manager = level.structureManager();
        return override.wholeStructure()
                ? manager.getStructureAt(pos, structure).isValid()
                : manager.getStructureWithPieceAt(pos.getX(), pos.getY(), pos.getZ(), structure).isValid();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // summonNaturalLightning
    // ---------------------------------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLightningStrike(final LightningStrikeEvent event) {
        if (!CarpetSettings.summonNaturalLightning || event.getCause() != LightningStrikeEvent.Cause.COMMAND) {
            return;
        }
        World world = event.getWorld();
        Location location = event.getLightning().getLocation();
        ServerLevel level = ((CraftWorld) world).getHandle();
        BlockPos at = BlockPos.containing(location.getX(), location.getY(), location.getZ());
        double effective = level.getCurrentDifficultyAt(at).getEffectiveDifficulty();
        boolean spawnMobs = Boolean.TRUE.equals(world.getGameRuleValue(GameRules.SPAWN_MOBS));
        if (spawnMobs && ThreadLocalRandom.current().nextDouble() < effective * 0.01D) {
            world.spawn(location, SkeletonHorse.class, CreatureSpawnEvent.SpawnReason.LIGHTNING, horse -> {
                horse.setTrapped(true);
                horse.setAge(0);
            });
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // liquidDamageDisabled & renewableDeepslate
    // ---------------------------------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLiquidFlow(final BlockFromToEvent event) {
        if (!CarpetSettings.liquidDamageDisabled || !event.getBlock().isLiquid()) {
            return;
        }
        Block to = event.getToBlock();
        Material type = to.getType();
        if (type.isAir() || type == Material.WATER || type == Material.LAVA) {
            return;
        }
        BlockData data = to.getBlockData();
        if (data instanceof Waterlogged && event.getBlock().getType() == Material.WATER) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockForm(final BlockFormEvent event) {
        if (!CarpetSettings.renewableDeepslate || !event.getBlock().isLiquid() || event.getBlock().getY() >= 0) {
            return;
        }
        ServerLevel level = ((CraftWorld) event.getBlock().getWorld()).getHandle();
        if (level.dimension() != net.minecraft.world.level.Level.OVERWORLD) {
            return;
        }
        Material type = event.getNewState().getType();
        if (type == Material.COBBLESTONE) {
            event.getNewState().setType(Material.COBBLED_DEEPSLATE);
        } else if (type == Material.STONE) {
            event.getNewState().setType(Material.DEEPSLATE);
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // moreBlueSkulls
    // ---------------------------------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWitherSkull(final ProjectileLaunchEvent event) {
        if (CarpetSettings.moreBlueSkulls && event.getEntity() instanceof WitherSkull skull && skull.getShooter() instanceof Wither && !skull.isCharged()
                && ThreadLocalRandom.current().nextFloat() < 0.1F) {
            skull.setCharged(true);
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // pingPlayerListLimit & customMOTD
    // ---------------------------------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onServerListPing(final PaperServerListPingEvent event) {
        if (!"_".equals(CarpetSettings.customMOTD)) {
            event.motd(MiniMessage.miniMessage().deserialize(CarpetSettings.customMOTD));
        }
        List<?> players = event.getListedPlayers();
        int limit = CarpetSettings.pingPlayerListLimit;
        while (players.size() > limit) {
            players.removeLast();
        }
    }

}
