package me.biquaternions.carpet.utils;

import io.papermc.paper.math.BlockPosition;
import lombok.experimental.UtilityClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@NullMarked
@UtilityClass
@SuppressWarnings("UnstableApiUsage")
public class DistanceCalculator {

    public final ConcurrentMap<UUID, BlockPosition> START_POINT_STORAGE = new ConcurrentHashMap<>();

    public boolean hasStartingPoint(final Player player) {
        return START_POINT_STORAGE.containsKey(player.getUniqueId());
    }

    public List<Component> findDistanceBetweenTwoPoints(BlockPosition pos1, BlockPosition pos2) {
        double dx = Math.abs(pos1.x() - pos2.x());
        double dy = Math.abs(pos1.y() - pos2.y());
        double dz = Math.abs(pos1.z() -  pos2.z());
        double manhattan = dx + dy + dz;
        double spherical = Math.sqrt(dx*dx + dy*dy + dz*dz);
        double cylindrical = Math.sqrt(dx*dx + dz*dz);

        Style style = Style.style()
                .color(NamedTextColor.RED)
                .build();
        List<Component> res = new ArrayList<>();
        res.add(Component.text("Distance between ")
                .appendSpace()
                .append(ComponentTool.formatPositionTeleportable(pos1, style))
                .appendSpace()
                .append(Component.text("and", NamedTextColor.WHITE))
                .appendSpace()
                .append(ComponentTool.formatPositionTeleportable(pos2, style))
                .append(Component.text(":", NamedTextColor.WHITE))
        );
        res.add(Component.text(" - Spherical: ").append(Component.text(String.format("%.2f", spherical), NamedTextColor.WHITE, TextDecoration.BOLD)));
        res.add(Component.text(" - Cylindrical: ").append(Component.text(String.format("%.2f", cylindrical), NamedTextColor.WHITE, TextDecoration.BOLD)));
        res.add(Component.text(" - Manhattan: ").append(Component.text(String.format("%.1f", manhattan), NamedTextColor.WHITE, TextDecoration.BOLD)));
        return res;
    }

    public void distance(final Player player, BlockPosition pos1, BlockPosition pos2) {
        DistanceCalculator.findDistanceBetweenTwoPoints(pos1, pos2)
                .forEach(player::sendMessage);
    }

    public void setStart(final Player player, BlockPosition pos) {
        START_POINT_STORAGE.put(player.getUniqueId(), pos);
        Style style = Style.style()
                .color(NamedTextColor.GRAY)
                .build();
        player.sendMessage(Component.text("Initial point set to: ", NamedTextColor.GRAY, TextDecoration.ITALIC).append(ComponentTool.formatPositionTeleportable(pos, style)));
    }

    public void setEnd(final Player player, BlockPosition pos) {
        if ( !hasStartingPoint(player) ) {
            START_POINT_STORAGE.put(player.getUniqueId(), pos);
            Style style = Style.style()
                    .color(NamedTextColor.GRAY)
                    .build();
            player.sendMessage(Component.text("There was no initial point for " + player.getName(), NamedTextColor.GRAY, TextDecoration.ITALIC));
            player.sendMessage(Component.text("Initial point set to:", NamedTextColor.GRAY, TextDecoration.ITALIC).appendSpace().append(ComponentTool.formatPositionTeleportable(pos, style)));
            return;
        }
        DistanceCalculator.findDistanceBetweenTwoPoints(START_POINT_STORAGE.get(player.getUniqueId()), pos)
                        .forEach(player::sendMessage);
    }

}
