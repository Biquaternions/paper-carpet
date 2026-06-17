package me.biquaternions.carpet.utils;

import lombok.experimental.UtilityClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;

@UtilityClass
public class ColorTool {

    public Component asComponent(final Color color) {
        int rgb = color.asRGB();
        return Component.text(String.format("#%06x", rgb), TextColor.color(rgb));
    }

}
