package me.biquaternions.carpet.logging;

import io.papermc.paper.adventure.PaperAdventure;
import me.biquaternions.carpet.helper.HopperCounter;
import me.biquaternions.carpet.logging.helpers.PacketCounter;
import me.biquaternions.carpet.utils.ComponentTool;
import me.biquaternions.carpet.utils.SpawnReporter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.server.level.ServerLevel;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * <p>Collects the HUD loggers' messages and sends them in the player list footer once per second.</p>
 */
public class HUDController {

    private static final List<Consumer<MinecraftServer>> HUD_LISTENERS = new ArrayList<>();
    private static final Map<UUID, List<Component>> PLAYER_HUDS = new HashMap<>();

    /**
     * Headers and footers set by scarpet apps, keyed by player name.
     */
    public static final Map<String, net.minecraft.network.chat.Component> SCARPET_HEADERS = new HashMap<>();
    public static final Map<String, net.minecraft.network.chat.Component> SCARPET_FOOTERS = new HashMap<>();

    public static void resetScarpetHUDs() {
        SCARPET_HEADERS.clear();
        SCARPET_FOOTERS.clear();
    }

    /**
     * Adds listener to be called when HUD is updated for logging information
     *
     * @param listener - a method to be called when new HUD information are collected
     */
    public static void register(Consumer<MinecraftServer> listener) {
        HUD_LISTENERS.add(listener);
    }

    public static void addMessage(@Nullable Player player, Component hudMessage) {
        if (player == null) {
            return;
        }
        PLAYER_HUDS.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>()).add(hudMessage);
    }

    public static void clearPlayer(Player player) {
        player.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
    }

    public static void updateHud(MinecraftServer server, @Nullable List<Player> force) {
        if ((server.getTickCount() % 20 != 0) && force == null) {
            return;
        }

        PLAYER_HUDS.clear();

        for (Player player : Bukkit.getOnlinePlayers()) {
            net.minecraft.network.chat.Component footer = SCARPET_FOOTERS.get(player.getName());
            if (footer != null) {
                addMessage(player, PaperAdventure.asAdventure(footer));
            }
        }

        if (LoggerRegistry.__tps) {
            LoggerRegistry.getLogger("tps").log(() -> sendTpsDisplay(server));
        }

        if (LoggerRegistry.__mobcaps) {
            LoggerRegistry.getLogger("mobcaps").log((option, player) -> {
                World world = switch (option) {
                    case "overworld" -> Bukkit.getWorlds().getFirst();
                    case "nether" -> Bukkit.getWorlds().stream().filter(w -> w.getEnvironment() == World.Environment.NETHER).findFirst().orElse(null);
                    case "end" -> Bukkit.getWorlds().stream().filter(w -> w.getEnvironment() == World.Environment.THE_END).findFirst().orElse(null);
                    default -> player.getWorld();
                };
                if (world == null) {
                    return null;
                }
                ServerLevel level = ((CraftWorld) world).getHandle();
                return new Component[]{SpawnReporter.printMobcapsForDimension(level, false).getFirst()};
            });
        }

        if (LoggerRegistry.__counter) {
            LoggerRegistry.getLogger("counter").log((option, player) -> sendCounterInfo(player.getWorld(), option));
        }

        if (LoggerRegistry.__packets) {
            LoggerRegistry.getLogger("packets").log(HUDController::packetCounter);
        }

        // extensions have time to pitch in.
        HUD_LISTENERS.forEach(l -> l.accept(server));

        Set<UUID> targets = new HashSet<>(PLAYER_HUDS.keySet());
        if (force != null) {
            force.forEach(p -> targets.add(p.getUniqueId()));
        }
        for (UUID uuid : targets) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                continue;
            }
            List<Component> lines = PLAYER_HUDS.getOrDefault(uuid, List.of());
            Component footer = Component.join(net.kyori.adventure.text.JoinConfiguration.newlines(), lines);
            net.minecraft.network.chat.Component header = SCARPET_HEADERS.get(player.getName());
            player.sendPlayerListHeaderAndFooter(header == null ? Component.empty() : PaperAdventure.asAdventure(header), footer);
        }
    }

    private static Component[] sendTpsDisplay(MinecraftServer server) {
        double mspt = ((double) server.getAverageTickTimeNanos()) / 1_000_000.0D;
        ServerTickRateManager trm = server.tickRateManager();

        double tps = 1000.0D / Math.max(trm.isSprinting() ? 0.0 : trm.millisecondsPerTick(), mspt);
        if (trm.isFrozen()) {
            tps = 0;
        }
        TextColor color = ComponentTool.heatmapColor(mspt, trm.millisecondsPerTick());
        return new Component[]{Component.text("TPS: ", NamedTextColor.GRAY)
                .append(Component.text(String.format(Locale.US, "%.1f", tps), color))
                .append(Component.text(" MSPT: ", NamedTextColor.GRAY))
                .append(Component.text(String.format(Locale.US, "%.1f", mspt), color))};
    }

    private static Component[] sendCounterInfo(World world, String colors) {
        List<Component> res = new ArrayList<>();
        for (String color : colors.split(",")) {
            HopperCounter counter = HopperCounter.getCounter(world, color.trim());
            if (counter != null) {
                res.addAll(counter.format(world, false, true));
            }
        }
        return res.toArray(new Component[0]);
    }

    private static Component[] packetCounter() {
        Component[] ret = new Component[]{
                Component.text("I/" + PacketCounter.totalIn() + " O/" + PacketCounter.totalOut(), NamedTextColor.WHITE),
        };
        PacketCounter.reset();
        return ret;
    }

}
