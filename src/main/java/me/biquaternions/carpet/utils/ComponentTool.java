package me.biquaternions.carpet.utils;

import io.papermc.paper.math.BlockPosition;
import lombok.experimental.UtilityClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.Style;
import org.jspecify.annotations.NullMarked;

@NullMarked
@UtilityClass
@SuppressWarnings("UnstableApiUsage")
public class ComponentTool {

    public Component formatPositionTeleportable(final BlockPosition position, final Style style) {
        return Component.text(String.format("[ %d, %d, %d ]", position.blockX(), position.blockY(), position.blockZ()))
                .style(style)
                .clickEvent(ClickEvent.clickEvent(ClickEvent.Action.RUN_COMMAND, ClickEvent.Payload.string(String.format("minecraft:tp %.2f %.2f %.2f", position.x(), position.y(), position.z()))));
    }

}
