package me.biquaternions.carpet.listener;

import com.destroystokyo.paper.event.server.ServerTickEndEvent;
import me.biquaternions.carpet.utils.MobAI;
import me.biquaternions.carpet.utils.ParticleDisplay;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.food.VillagerFood;
import net.minecraft.world.level.pathfinder.Path;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.craftbukkit.entity.CraftVillager;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;
import org.jspecify.annotations.NullMarked;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * <p>Port of fabric-carpet's {@code Villager_aiMixin}, used by the {@code /track} command.</p>
 *
 * <p>The villager's name is updated at the end of every tick instead of at the start of the villager's tick. The
 * barrier particle shown when a villager attempts to spawn an iron golem couldn't be ported, as there's no event
 * for golem spawn attempts.</p>
 */
@NullMarked
public class MobAIListener implements Listener {

    private record BreedingDisplay(int totalFood, boolean hasBed, int displayAge) {
    }

    private final Map<UUID, BreedingDisplay> breedingDisplays = new HashMap<>();

    @EventHandler
    public void onTickEnd(final ServerTickEndEvent event) {
        if (!MobAI.isTrackingAnything()) {
            if (!this.breedingDisplays.isEmpty()) {
                this.breedingDisplays.clear();
            }
            return;
        }
        for (World world : org.bukkit.Bukkit.getWorlds()) {
            for (Villager villager : world.getEntitiesByClass(Villager.class)) {
                if (MobAI.isTracking(villager, MobAI.TrackingType.IRON_GOLEM_SPAWNING)) {
                    this.displayGolemSpawning(villager);
                } else if (MobAI.isTracking(villager, MobAI.TrackingType.BREEDING)) {
                    this.displayBreeding(villager);
                }
            }
        }
    }

    private void displayGolemSpawning(final Villager villager) {
        net.minecraft.world.entity.npc.villager.Villager handle = ((CraftVillager) villager).getHandle();
        Brain<?> brain = handle.getBrain();
        long time;
        Optional<Boolean> lastSeen = brain.getMemory(MemoryModuleType.GOLEM_DETECTED_RECENTLY);
        if (lastSeen.isEmpty()) {
            time = 0;
        } else {
            time = brain.getTimeUntilExpiry(MemoryModuleType.GOLEM_DETECTED_RECENTLY);
        }
        boolean recentlySeen = time > 0;
        Optional<Long> lastSlept = brain.getMemory(MemoryModuleType.LAST_SLEPT);
        boolean sleep = false;
        boolean panic = brain.isActive(Activity.PANIC);
        long currentTime = handle.level().getGameTime();
        if (lastSlept.isPresent()) {
            sleep = (currentTime - lastSlept.get()) < 24000L;
        }

        villager.customName(Component.text("☽ ", sleep ? Style.style(NamedTextColor.DARK_GREEN, TextDecoration.BOLD) : Style.style(NamedTextColor.DARK_GRAY, TextDecoration.BOLD))
                .append(Component.text("☣ ", panic ? Style.style(NamedTextColor.GREEN, TextDecoration.BOLD) : Style.style(NamedTextColor.DARK_GRAY, TextDecoration.BOLD)))
                .append(Component.text(time, recentlySeen ? Style.style(NamedTextColor.RED, TextDecoration.BOLD) : Style.style(NamedTextColor.GREEN, TextDecoration.BOLD))));
        villager.setCustomNameVisible(true);
    }

    private void displayBreeding(final Villager villager) {
        net.minecraft.world.entity.npc.villager.Villager handle = ((CraftVillager) villager).getHandle();
        BreedingDisplay display = this.breedingDisplays.get(villager.getUniqueId());
        if (display == null || handle.tickCount % 50 == 0 || handle.tickCount < 20) {
            display = new BreedingDisplay(countFoodPointsInInventory(handle) / 12, handle.getBrain().getMemory(MemoryModuleType.HOME).isPresent(), handle.getAge());
        }
        int displayAge = display.displayAge();
        if (Math.abs(displayAge) < 100 && displayAge != 0) {
            displayAge = handle.getAge();
        }
        display = new BreedingDisplay(display.totalFood(), display.hasBed(), displayAge);
        this.breedingDisplays.put(villager.getUniqueId(), display);

        Style bold = Style.style(TextDecoration.BOLD);
        villager.customName(Component.text("☖ ", bold.color(display.hasBed() ? NamedTextColor.DARK_GREEN : NamedTextColor.DARK_GRAY))
                .append(Component.text("♨", bold.color(display.totalFood() > 0 ? NamedTextColor.DARK_GREEN : NamedTextColor.DARK_GRAY)))
                .append(Component.text(display.totalFood() + " ", display.totalFood() > 0 ? NamedTextColor.DARK_GREEN : NamedTextColor.DARK_GRAY))
                .append(Component.text("♡", bold.color(displayAge == 0 ? NamedTextColor.DARK_GREEN : NamedTextColor.DARK_GRAY)))
                .append(Component.text(displayAge == 0 ? "" : String.valueOf(displayAge), displayAge == 0 ? NamedTextColor.DARK_GREEN : NamedTextColor.DARK_GRAY)));
        villager.setCustomNameVisible(true);
    }

    private static int countFoodPointsInInventory(final net.minecraft.world.entity.npc.villager.Villager villager) {
        int points = 0;
        for (ItemStack item : villager.getInventory().getItems()) {
            VillagerFood villagerFood = item.get(DataComponents.VILLAGER_FOOD);
            if (villagerFood != null) {
                points += item.count() * villagerFood.nutrition();
            }
        }
        return points;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteract(final PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager villager) || !MobAI.isTracking(villager, MobAI.TrackingType.BREEDING)) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        net.minecraft.world.entity.npc.villager.Villager handle = ((CraftVillager) villager).getHandle();
        ServerLevel level = (ServerLevel) handle.level();
        Material item = player.getInventory().getItemInMainHand().getType();
        if (item == Material.EMERALD) {
            GlobalPos bedPos = handle.getBrain().getMemory(MemoryModuleType.HOME).orElse(null);
            if (bedPos == null || bedPos.dimension() != level.dimension()) {
                handle.setUnhappy();
                villager.getWorld().spawnParticle(Particle.BLOCK_MARKER, villager.getX(), villager.getY() + villager.getEyeHeight() + 1, villager.getZ(), 1, 0.1, 0.1, 0.1, 0.0, Material.BARRIER.createBlockData());
            } else {
                BlockPos pos = bedPos.pos();
                ParticleDisplay.drawParticleLine(player, villager.getLocation().toVector(), new Vector(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5),
                        new ParticleDisplay.ParticleOptions<>(Particle.DUST, new Particle.DustOptions(Color.BLACK, 1)),
                        ParticleDisplay.ParticleOptions.of(Particle.HAPPY_VILLAGER), 100, 0.2);
            }
        } else if (item == Material.ROTTEN_FLESH) {
            while (countFoodPointsInInventory(handle) >= 12) {
                handle.eatAndDigestFood();
            }
        } else if (Tag.BEDS.isTagged(item)) {
            List<PoiRecord> records = level.getPoiManager().getInRange(type -> type.is(PoiTypes.HOME), handle.blockPosition(), 48, PoiManager.Occupancy.ANY).toList();
            for (PoiRecord poi : records) {
                BlockPos pos = poi.getPos();
                double x = pos.getX() + 0.5;
                double y = pos.getY() + 0.5;
                double z = pos.getZ() + 0.5;
                if (!poi.hasSpace()) {
                    villager.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, x, y + 1.5, z, 50, 0.1, 0.3, 0.1, 0.0);
                } else if (canReachHome(handle, pos, poi)) {
                    villager.getWorld().spawnParticle(Particle.END_ROD, x, y + 1, z, 50, 0.1, 0.3, 0.1, 0.0);
                } else {
                    villager.getWorld().spawnParticle(Particle.BLOCK_MARKER, x, y + 1, z, 1, 0.1, 0.1, 0.1, 0.0, Material.BARRIER.createBlockData());
                }
            }
        }
    }

    // stolen from VillagerMakeLove
    private static boolean canReachHome(final net.minecraft.world.entity.npc.villager.Villager villager, final BlockPos pos, final PoiRecord poi) {
        Path path = villager.getNavigation().createPath(pos, poi.getPoiType().value().validRange());
        return path != null && path.canReach();
    }

}
