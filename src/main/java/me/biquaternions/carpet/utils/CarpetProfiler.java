package me.biquaternions.carpet.utils;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import lombok.experimental.UtilityClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

/**
 * <p>Port of fabric-carpet's {@code CarpetProfiler}.</p>
 *
 * <p>fabric-carpet measures every section of the tick (entities, block entities, random ticks, etc...) and the CPU time
 * of every entity type through mixins, which isn't possible on Paper. Instead, only the overall tick time is measured
 * (from {@link com.destroystokyo.paper.event.server.ServerTickEndEvent}) and the entity counts are sampled each tick.
 * For a detailed breakdown, spark ({@code /spark profiler}) is bundled with Paper.</p>
 */
@UtilityClass
public class CarpetProfiler {

    public enum Type {
        NONE,
        GENERAL,
        ENTITY
    }

    private final Object2LongOpenHashMap<Pair<String, String>> ENTITY_COUNT = new Object2LongOpenHashMap<>();
    private @Nullable CommandSender currentRequester = null;
    private int tickHealthRequested = 0;
    private int tickHealthElapsed = 0;
    private double totalTickTime = 0;
    private Type testType = Type.NONE;

    public void prepareTickReport(CommandSender source, int ticks) {
        prepare(source, ticks, Type.GENERAL);
    }

    public void prepareEntityReport(CommandSender source, int ticks) {
        prepare(source, ticks, Type.ENTITY);
    }

    private void prepare(CommandSender source, int ticks, Type type) {
        ENTITY_COUNT.clear();
        totalTickTime = 0;
        testType = type;
        tickHealthElapsed = ticks;
        tickHealthRequested = ticks;
        currentRequester = source;
    }

    /**
     * Called at the end of every tick with the duration of the tick in milliseconds.
     */
    public void endTick(double tickDuration) {
        if (testType == Type.NONE) {
            return;
        }
        totalTickTime += tickDuration;
        if (testType == Type.ENTITY) {
            for (World world : Bukkit.getWorlds()) {
                String dimension = shortKey(world.getKey());
                for (Entity entity : world.getEntities()) {
                    ENTITY_COUNT.addTo(Pair.of(shortKey(entity.getType().getKey()), dimension), 1);
                }
            }
        }
        tickHealthElapsed--;
        if (tickHealthElapsed <= 0) {
            finalizeTickReport();
        }
    }

    private String shortKey(NamespacedKey key) {
        return NamespacedKey.MINECRAFT.equals(key.getNamespace()) ? key.getKey() : key.asString();
    }

    private void finalizeTickReport() {
        CommandSender requester = currentRequester;
        if (requester != null) {
            if (testType == Type.GENERAL) {
                finalizeTickReportForTime(requester);
            }
            if (testType == Type.ENTITY) {
                finalizeTickReportForEntities(requester);
            }
        }
        cleanupTickReport();
    }

    private void cleanupTickReport() {
        ENTITY_COUNT.clear();
        testType = Type.NONE;
        tickHealthElapsed = 0;
        tickHealthRequested = 0;
        totalTickTime = 0;
        currentRequester = null;
    }

    private Component sparkHint() {
        return Component.text("Per section timings are not available on Paper, use ", NamedTextColor.GRAY, TextDecoration.ITALIC)
                .append(Component.text("/spark profiler", NamedTextColor.GOLD).clickEvent(ClickEvent.suggestCommand("/spark profiler start --timeout 30")))
                .append(Component.text(" instead", NamedTextColor.GRAY, TextDecoration.ITALIC));
    }

    private void finalizeTickReportForTime(CommandSender requester) {
        requester.sendMessage(Component.empty());
        requester.sendMessage(Component.text("Average tick time: ", NamedTextColor.WHITE, TextDecoration.BOLD)
                .append(Component.text(String.format("%.3fms", totalTickTime / tickHealthRequested), NamedTextColor.YELLOW, TextDecoration.BOLD)));
        requester.sendMessage(sparkHint());
    }

    private void finalizeTickReportForEntities(CommandSender requester) {
        requester.sendMessage(Component.empty());
        requester.sendMessage(Component.text("Average tick time: ", NamedTextColor.WHITE, TextDecoration.BOLD)
                .append(Component.text(String.format("%.3fms", totalTickTime / tickHealthRequested), NamedTextColor.YELLOW, TextDecoration.BOLD)));
        requester.sendMessage(Component.text("Top 10 counts:", NamedTextColor.WHITE, TextDecoration.BOLD));
        List<Object2LongMap.Entry<Pair<String, String>>> sorted = ENTITY_COUNT.object2LongEntrySet().stream()
                .sorted(Comparator.<Object2LongMap.Entry<Pair<String, String>>>comparingLong(Object2LongMap.Entry::getLongValue).reversed())
                .limit(10)
                .toList();
        for (Object2LongMap.Entry<Pair<String, String>> entry : sorted) {
            requester.sendMessage(Component.text(String.format(" - %s in %s: ", entry.getKey().getLeft(), entry.getKey().getRight()), NamedTextColor.WHITE)
                    .append(Component.text(String.format("%.1f", 1.0D * entry.getLongValue() / tickHealthRequested), NamedTextColor.YELLOW)));
        }
        requester.sendMessage(Component.text("Top 10 CPU hogs:", NamedTextColor.WHITE, TextDecoration.BOLD));
        requester.sendMessage(sparkHint());
    }

}
