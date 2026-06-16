package me.biquaternions.carpet.utils;

import lombok.experimental.UtilityClass;
import org.bukkit.DyeColor;
import org.bukkit.block.Block;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
@UtilityClass
public class WoolTool {

    public @Nullable DyeColor getWoolColor(final Block block) {
        return switch (block.getType()) {
            case WHITE_WOOL -> DyeColor.WHITE;
            case ORANGE_WOOL -> DyeColor.ORANGE;
            case MAGENTA_WOOL -> DyeColor.MAGENTA;
            case LIGHT_BLUE_WOOL -> DyeColor.LIGHT_BLUE;
            case YELLOW_WOOL -> DyeColor.YELLOW;
            case LIME_WOOL -> DyeColor.LIME;
            case PINK_WOOL -> DyeColor.PINK;
            case GRAY_WOOL -> DyeColor.GRAY;
            case LIGHT_GRAY_WOOL -> DyeColor.LIGHT_GRAY;
            case CYAN_WOOL -> DyeColor.CYAN;
            case PURPLE_WOOL -> DyeColor.PURPLE;
            case BLUE_WOOL -> DyeColor.BLUE;
            case BROWN_WOOL -> DyeColor.BROWN;
            case GREEN_WOOL -> DyeColor.GREEN;
            case RED_WOOL -> DyeColor.RED;
            case BLACK_WOOL -> DyeColor.BLACK;
            default -> null;
        };
    }

}
