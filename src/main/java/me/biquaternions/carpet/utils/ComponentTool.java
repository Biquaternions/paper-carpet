package me.biquaternions.carpet.utils;

import io.papermc.paper.math.BlockPosition;
import lombok.experimental.UtilityClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.SpawnCategory;
import org.jspecify.annotations.NullMarked;

import java.util.Locale;

/**
 * Adventure replacements for the helpers of fabric-carpet's {@code Messenger}.
 */
@NullMarked
@UtilityClass
@SuppressWarnings("UnstableApiUsage")
public class ComponentTool {

    public Component formatPositionTeleportable(final BlockPosition position, final Style style) {
        return Component.text(String.format("[ %d, %d, %d ]", position.blockX(), position.blockY(), position.blockZ()))
                .style(style)
                .clickEvent(ClickEvent.clickEvent(ClickEvent.Action.RUN_COMMAND, ClickEvent.Payload.string(String.format("minecraft:tp %.2f %.2f %.2f", position.x(), position.y(), position.z()))));
    }

    /**
     * Clickable integer coordinates, teleports the player on click. Equivalent of {@code Messenger.tp(style, BlockPos)}.
     */
    public Component tp(final Style style, final int x, final int y, final int z) {
        return Component.text(String.format(Locale.ROOT, "[ %d, %d, %d ]", x, y, z), style)
                .clickEvent(ClickEvent.runCommand(String.format(Locale.ROOT, "/minecraft:tp %d %d %d", x, y, z)));
    }

    public Component tp(final TextColor color, final int x, final int y, final int z) {
        return tp(Style.style(color), x, y, z);
    }

    /**
     * Clickable decimal coordinates, teleports the player on click.
     */
    public Component tp(final Style style, final double x, final double y, final double z) {
        return Component.text(String.format(Locale.ROOT, "[ %.1f, %.1f, %.1f]", x, y, z), style)
                .clickEvent(ClickEvent.runCommand(String.format(Locale.ROOT, "/minecraft:tp %.3f %.3f %.3f", x, y, z)));
    }

    /**
     * Vector with one decimal of precision, every value can be hovered for its full value and clicked to suggest it.
     * Equivalent of {@code Messenger.dblt}.
     */
    public Component dblt(final TextColor color, final double... doubles) {
        TextComponent.Builder builder = Component.text().color(color).append(Component.text("[ "));
        String prefix = "";
        for (double dbl : doubles) {
            builder.append(Component.text(String.format(Locale.ROOT, "%s%.1f", prefix, dbl))
                    .hoverEvent(HoverEvent.showText(Component.text(String.valueOf(dbl), NamedTextColor.WHITE)))
                    .clickEvent(ClickEvent.suggestCommand(String.valueOf(dbl))));
            prefix = ", ";
        }
        builder.append(Component.text(" ]"));
        return builder.build();
    }

    /**
     * Vector with full precision. Equivalent of {@code Messenger.dblf}.
     */
    public Component dblf(final TextColor color, final double... doubles) {
        StringBuilder str = new StringBuilder("[ ");
        String prefix = "";
        for (double dbl : doubles) {
            str.append(String.format(Locale.ROOT, "%s%f", prefix, dbl));
            prefix = ", ";
        }
        str.append(" ]");
        return Component.text(str.toString(), color);
    }

    /**
     * Color for a value compared against a reference, from gray (negative) to magenta (above the reference).
     */
    public TextColor heatmapColor(final double actual, final double reference) {
        TextColor color = NamedTextColor.GRAY;
        if (actual >= 0.0D) color = NamedTextColor.DARK_GREEN;
        if (actual > 0.5D * reference) color = NamedTextColor.YELLOW;
        if (actual > 0.8D * reference) color = NamedTextColor.RED;
        if (actual > reference) color = NamedTextColor.LIGHT_PURPLE;
        return color;
    }

    public TextColor creatureTypeColor(final SpawnCategory category) {
        return switch (category) {
            case MONSTER -> NamedTextColor.DARK_RED;
            case ANIMAL -> NamedTextColor.DARK_GREEN;
            case AMBIENT -> NamedTextColor.DARK_GRAY;
            case WATER_ANIMAL -> NamedTextColor.DARK_BLUE;
            case WATER_AMBIENT -> NamedTextColor.DARK_AQUA;
            default -> NamedTextColor.WHITE;
        };
    }

}
