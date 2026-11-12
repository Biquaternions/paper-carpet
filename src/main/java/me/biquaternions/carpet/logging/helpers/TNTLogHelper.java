package me.biquaternions.carpet.logging.helpers;

import me.biquaternions.carpet.logging.Logger;
import me.biquaternions.carpet.logging.LoggerRegistry;
import me.biquaternions.carpet.utils.ComponentTool;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.util.Vector;

public class TNTLogHelper {

    private static long lastGametime = 0;
    private static int tntCount = 0;

    private double primedX, primedY, primedZ;
    private Vector primedAngle = new Vector();

    /**
     * Runs when the TNT is primed. Expects the position and motion angle of the TNT.
     */
    public void onPrimed(double x, double y, double z, Vector motion) {
        this.primedX = x;
        this.primedY = y;
        this.primedZ = z;
        this.primedAngle = motion.clone();
    }

    /**
     * Runs when the TNT explodes. Expects the position of the TNT.
     */
    public void onExploded(double x, double y, double z, long gametime) {
        if (lastGametime != gametime) {
            tntCount = 0;
            lastGametime = gametime;
        }
        tntCount++;
        Logger logger = LoggerRegistry.getLogger("tnt");
        if (logger == null) {
            return;
        }
        logger.log((String option) -> switch (option) {
            case "brief" -> new Component[]{Component.text("P ", NamedTextColor.GREEN)
                    .append(ComponentTool.dblt(NamedTextColor.GREEN, this.primedX, this.primedY, this.primedZ))
                    .append(Component.text(" ", NamedTextColor.WHITE))
                    .append(ComponentTool.dblt(NamedTextColor.GREEN, this.primedAngle.getX(), this.primedAngle.getY(), this.primedAngle.getZ()))
                    .append(Component.text(" E ", NamedTextColor.RED))
                    .append(ComponentTool.dblt(NamedTextColor.RED, x, y, z))};
            case "full" -> new Component[]{Component.text("#" + tntCount, NamedTextColor.RED)
                    .append(Component.text("@" + gametime, NamedTextColor.LIGHT_PURPLE))
                    .append(Component.text(" : ", NamedTextColor.GRAY))
                    .append(Component.text("P ", NamedTextColor.GREEN))
                    .append(ComponentTool.dblf(NamedTextColor.GREEN, this.primedX, this.primedY, this.primedZ))
                    .append(Component.text(" ", NamedTextColor.WHITE))
                    .append(ComponentTool.dblf(NamedTextColor.GREEN, this.primedAngle.getX(), this.primedAngle.getY(), this.primedAngle.getZ()))
                    .append(Component.text(" E ", NamedTextColor.RED))
                    .append(ComponentTool.dblf(NamedTextColor.RED, x, y, z))};
            default -> null;
        });
    }

}
