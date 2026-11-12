package me.biquaternions.carpet.listener;

import com.destroystokyo.paper.event.server.ServerTickEndEvent;
import io.papermc.paper.math.BlockPosition;
import io.papermc.paper.math.Position;
import me.biquaternions.carpet.CarpetSettings;
import me.biquaternions.carpet.helper.HopperCounter;
import me.biquaternions.carpet.utils.BlockInfo;
import me.biquaternions.carpet.utils.CarpetProfiler;
import me.biquaternions.carpet.utils.DistanceCalculator;
import me.biquaternions.carpet.utils.SpawnReporter;
import me.biquaternions.carpet.utils.WoolTool;
import net.kyori.adventure.text.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftMob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Locale;

@NullMarked
@SuppressWarnings("UnstableApiUsage")
public class ServerListener implements Listener {

    @EventHandler
    public void onWorldLoad(final WorldLoadEvent event) {
        World world = event.getWorld();
        if (CarpetSettings.viewDistance >= 2) {
            world.setViewDistance(CarpetSettings.viewDistance);
        }
        if (CarpetSettings.simulationDistance >= 2) {
            world.setSimulationDistance(CarpetSettings.simulationDistance);
        }
    }

    @EventHandler
    public void onTickEnd(final ServerTickEndEvent event) {
        CarpetProfiler.endTick(event.getTickDuration());
    }

    // ----- Spawn tracking ----- //

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onNaturalSpawn(final CreatureSpawnEvent event) {
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL || !(event.getEntity() instanceof CraftMob mob)) {
            return;
        }
        Mob handle = mob.getHandle();
        SpawnReporter.registerSpawn(handle, BlockPos.containing(event.getLocation().getX(), event.getLocation().getY(), event.getLocation().getZ()));
        if (SpawnReporter.mockSpawns) {
            event.setCancelled(true);
        }
    }

    // ----- Carpet placement actions ----- //

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCarpetPlace(final BlockPlaceEvent event) {
        if (!CarpetSettings.carpets) {
            return;
        }
        Block block = event.getBlockPlaced();
        DyeColor color = carpetColor(block.getType());
        if (color == null) {
            return;
        }
        Player player = event.getPlayer();
        ServerLevel level = ((CraftWorld) block.getWorld()).getHandle();
        BlockPos pos = new BlockPos(block.getX(), block.getY(), block.getZ());
        switch (color) {
            case PINK -> {
                if (!"false".equals(CarpetSettings.commandSpawn)) {
                    send(player, SpawnReporter.report(pos, level));
                }
            }
            case BLACK -> {
                if (!"false".equals(CarpetSettings.commandSpawn)) {
                    send(player, SpawnReporter.handleWoolAction(pos, level));
                }
            }
            case BROWN -> {
                if (!"false".equals(CarpetSettings.commandDistance)) {
                    BlockPosition position = Position.block(block.getX(), block.getY(), block.getZ());
                    if (!DistanceCalculator.hasStartingPoint(player) || player.isSneaking()) {
                        DistanceCalculator.setStart(player, position);
                    } else {
                        DistanceCalculator.setEnd(player, position);
                    }
                }
            }
            case GRAY -> {
                if (!"false".equals(CarpetSettings.commandInfo)) {
                    send(player, BlockInfo.blockInfo(block.getRelative(0, -1, 0).getLocation()));
                }
            }
            case GREEN -> {
                if (CarpetSettings.hopperCounters) {
                    DyeColor under = WoolTool.getWoolColor(block.getRelative(0, -1, 0));
                    HopperCounter counter = under == null ? null : HopperCounter.getCounter(block.getWorld(), under);
                    if (counter != null) {
                        send(player, counter.format(block.getWorld(), false, false));
                    }
                }
            }
            case RED -> {
                if (CarpetSettings.hopperCounters) {
                    DyeColor under = WoolTool.getWoolColor(block.getRelative(0, -1, 0));
                    HopperCounter counter = under == null ? null : HopperCounter.getCounter(block.getWorld(), under);
                    if (counter != null) {
                        counter.reset(block.getWorld());
                        player.sendMessage(Component.text(String.format("%s counter reset", under.name().toLowerCase(Locale.ROOT))));
                    }
                }
            }
            default -> {
            }
        }
    }

    private static void send(final Player player, final List<Component> components) {
        components.forEach(player::sendMessage);
    }

    private static @org.jspecify.annotations.Nullable DyeColor carpetColor(final Material material) {
        if (!org.bukkit.Tag.WOOL_CARPETS.isTagged(material)) {
            return null;
        }
        String name = material.name();
        try {
            return DyeColor.valueOf(name.substring(0, name.length() - "_CARPET".length()));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

}
