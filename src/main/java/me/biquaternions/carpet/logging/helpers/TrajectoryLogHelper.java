package me.biquaternions.carpet.logging.helpers;

import me.biquaternions.carpet.logging.Logger;
import me.biquaternions.carpet.logging.LoggerRegistry;
import me.biquaternions.carpet.utils.ComponentTool;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * A generic log helper for logging the trajectory of things like blocks and throwables.
 */
public class TrajectoryLogHelper {

    private static final int MAX_TICKS_PER_LINE = 20;

    private final Logger logger;
    private final List<Vector> positions = new ArrayList<>();
    private final List<Vector> motions = new ArrayList<>();
    private boolean doLog;

    public TrajectoryLogHelper(String logName) {
        this.logger = LoggerRegistry.getLogger(logName);
        this.doLog = this.logger != null && this.logger.hasOnlineSubscribers();
    }

    public void onTick(double x, double y, double z, Vector velocity) {
        if (!this.doLog) {
            return;
        }
        this.positions.add(new Vector(x, y, z));
        this.motions.add(velocity.clone());
    }

    public void onFinish() {
        if (!this.doLog) {
            return;
        }
        this.logger.log((String option) -> {
            List<Component> comp = new ArrayList<>();
            switch (option) {
                case "brief" -> {
                    comp.add(Component.empty());
                    TextComponent.Builder line = Component.text();
                    for (int i = 0; i < this.positions.size(); i++) {
                        Vector pos = this.positions.get(i);
                        Vector mot = this.motions.get(i);
                        line.append(Component.text(" x", NamedTextColor.WHITE).hoverEvent(HoverEvent.showText(Component.text(
                                String.format("Tick: %d\nx: %f\ny: %f\nz: %f\n------------\nmx: %f\nmy: %f\nmz: %f",
                                        i, pos.getX(), pos.getY(), pos.getZ(), mot.getX(), mot.getY(), mot.getZ()), NamedTextColor.WHITE))));
                        if ((((i + 1) % MAX_TICKS_PER_LINE) == 0) || i == this.positions.size() - 1) {
                            comp.add(line.build());
                            line = Component.text();
                        }
                    }
                }
                case "full" -> {
                    comp.add(Component.text("---------", NamedTextColor.WHITE));
                    for (int i = 0; i < this.positions.size(); i++) {
                        Vector pos = this.positions.get(i);
                        Vector mot = this.motions.get(i);
                        comp.add(Component.text(String.format("tick: %3d pos", i), NamedTextColor.WHITE)
                                .append(ComponentTool.dblt(NamedTextColor.WHITE, pos.getX(), pos.getY(), pos.getZ()))
                                .append(Component.text("   mot", NamedTextColor.WHITE))
                                .append(ComponentTool.dblt(NamedTextColor.WHITE, mot.getX(), mot.getY(), mot.getZ())));
                    }
                }
                default -> {
                }
            }
            return comp.toArray(new Component[0]);
        });
        this.doLog = false;
    }

}
