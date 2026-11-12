package me.biquaternions.carpet.logging.helpers;

import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import me.biquaternions.carpet.logging.Logger;
import me.biquaternions.carpet.logging.LoggerRegistry;
import me.biquaternions.carpet.utils.ComponentTool;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.util.Vector;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>Logs explosions.</p>
 *
 * <p>On Paper, the explosion power and fire are only known for explosions caused by entities
 * ({@link org.bukkit.event.entity.ExplosionPrimeEvent}), and only living entities report the knockback they received
 * ({@link org.bukkit.event.entity.EntityKnockbackEvent}).</p>
 */
public class ExplosionLogHelper {

    private static long lastGametime = 0;
    private static int explosionCountInCurrentGT = 0;
    private static boolean newTick;

    public final Vector pos;
    private final @Nullable Float power;
    private final @Nullable Boolean createFire;
    private final Object2IntMap<EntityChangedStatusWithCount> impactedEntities = new Object2IntLinkedOpenHashMap<>();
    private @Nullable String blockDestructionType;
    private boolean affectBlocks = false;

    public ExplosionLogHelper(double x, double y, double z, @Nullable Float power, @Nullable Boolean createFire) {
        this.power = power;
        this.pos = new Vector(x, y, z);
        this.createFire = createFire;
    }

    public void setAffectBlocks(boolean b) {
        this.affectBlocks = b;
    }

    public void setBlockDestructionType(@Nullable String type) {
        this.blockDestructionType = type;
    }

    public void onExplosionDone(long gametime) {
        newTick = false;
        if (lastGametime != gametime) {
            explosionCountInCurrentGT = 0;
            lastGametime = gametime;
            newTick = true;
        }
        explosionCountInCurrentGT++;
        final int count = explosionCountInCurrentGT;
        final boolean isNewTick = newTick;
        Logger logger = LoggerRegistry.getLogger("explosions");
        if (logger == null) {
            return;
        }
        logger.log((String option) -> {
            List<Component> messages = new ArrayList<>();
            if (isNewTick) {
                messages.add(Component.text("tick : ", NamedTextColor.WHITE, TextDecoration.BOLD).append(Component.text(gametime, NamedTextColor.GOLD)));
            }
            if ("brief".equals(option)) {
                messages.add(Component.text("#" + count, NamedTextColor.GOLD)
                        .append(Component.text("->", NamedTextColor.GRAY, TextDecoration.BOLD))
                        .append(ComponentTool.dblt(NamedTextColor.GREEN, this.pos.getX(), this.pos.getY(), this.pos.getZ()))
                        .append(Component.text(this.affectBlocks ? "  (affects blocks)" : "  (doesn't affect blocks)", NamedTextColor.LIGHT_PURPLE)));
            }
            if ("full".equals(option)) {
                messages.add(Component.text("#" + count, NamedTextColor.GOLD)
                        .append(Component.text("->", NamedTextColor.GRAY, TextDecoration.BOLD))
                        .append(ComponentTool.dblt(NamedTextColor.GREEN, this.pos.getX(), this.pos.getY(), this.pos.getZ())));
                messages.add(Component.text("  affects blocks: ", NamedTextColor.WHITE).append(Component.text(this.affectBlocks, NamedTextColor.LIGHT_PURPLE)));
                messages.add(Component.text("  creates fire: ", NamedTextColor.WHITE).append(Component.text(this.createFire == null ? "?" : String.valueOf(this.createFire), NamedTextColor.LIGHT_PURPLE)));
                messages.add(Component.text("  power: ", NamedTextColor.WHITE).append(Component.text(this.power == null ? "?" : String.valueOf(this.power), NamedTextColor.AQUA)));
                messages.add(Component.text("  destruction: ", NamedTextColor.WHITE).append(Component.text(this.blockDestructionType == null ? "?" : this.blockDestructionType, NamedTextColor.AQUA)));
                if (this.impactedEntities.isEmpty()) {
                    messages.add(Component.text("  affected entities: ", NamedTextColor.WHITE).append(Component.text("None", NamedTextColor.LIGHT_PURPLE)));
                } else {
                    messages.add(Component.text("  affected entities:", NamedTextColor.WHITE));
                    this.impactedEntities.forEach((k, v) -> {
                        boolean self = k.pos.equals(this.pos);
                        messages.add((self ? Component.text("  - TNT", NamedTextColor.RED) : Component.text("  - ", NamedTextColor.WHITE))
                                .append(ComponentTool.dblt(self ? NamedTextColor.RED : NamedTextColor.YELLOW, k.pos.getX(), k.pos.getY(), k.pos.getZ()))
                                .append(Component.text(" dV", NamedTextColor.WHITE))
                                .append(ComponentTool.dblt(NamedTextColor.GOLD, k.accel.getX(), k.accel.getY(), k.accel.getZ()))
                                .append(Component.text(" " + k.type.getKey().getKey(), NamedTextColor.WHITE))
                                .append(v > 1 ? Component.text(" (" + v + ")", NamedTextColor.GREEN) : Component.empty()));
                    });
                }
            }
            return messages.toArray(new Component[0]);
        });
    }

    public void onEntityImpacted(Entity entity, Vector accel) {
        this.onEntityImpacted(new EntityChangedStatusWithCount(entity.getLocation().toVector(), entity.getType(), accel.clone()));
    }

    public void onEntityImpacted(EntityChangedStatusWithCount ent) {
        this.impactedEntities.put(ent, this.impactedEntities.getOrDefault(ent, 0) + 1);
    }

    public record EntityChangedStatusWithCount(Vector pos, EntityType type, Vector accel) {
    }

}
