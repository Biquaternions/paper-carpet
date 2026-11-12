package me.biquaternions.carpet.listener;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import com.destroystokyo.paper.event.server.ServerTickEndEvent;
import me.biquaternions.carpet.logging.HUDController;
import me.biquaternions.carpet.logging.LoggerRegistry;
import me.biquaternions.carpet.logging.helpers.ExplosionLogHelper;
import me.biquaternions.carpet.logging.helpers.PacketCounter;
import me.biquaternions.carpet.logging.helpers.TNTLogHelper;
import me.biquaternions.carpet.logging.helpers.TrajectoryLogHelper;
import net.minecraft.server.MinecraftServer;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.ThrowableProjectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import io.papermc.paper.event.entity.EntityKnockbackEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * <p>Feeds the loggers from Paper events.</p>
 *
 * <p>fabric-carpet hooks into entity ticks to record trajectories, Paper has no per-entity tick event, so instead the
 * tracked entities are sampled at the end of every server tick.</p>
 */
@NullMarked
public class LoggingListener implements Listener {

    private final Map<UUID, TNTLogHelper> tntHelpers = new HashMap<>();
    private final Map<UUID, Tracked> trajectories = new HashMap<>();
    private final List<ExplosionLogHelper.EntityChangedStatusWithCount> orphanKnockbacks = new ArrayList<>();
    private @Nullable ExplosionLogHelper currentExplosion;
    private @Nullable Location currentExplosionLocation;

    private record Tracked(Entity entity, TrajectoryLogHelper helper) {
    }

    // ----- Players ----- //

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(final PlayerJoinEvent event) {
        LoggerRegistry.playerConnected(event.getPlayer());
        PacketCounter.inject(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(final PlayerQuitEvent event) {
        LoggerRegistry.playerDisconnected(event.getPlayer());
        PacketCounter.uninject(event.getPlayer());
    }

    // ----- Ticking ----- //

    @EventHandler
    public void onTickEnd(final ServerTickEndEvent event) {
        // Trajectories
        Iterator<Tracked> iterator = this.trajectories.values().iterator();
        while (iterator.hasNext()) {
            Tracked tracked = iterator.next();
            Entity entity = tracked.entity();
            if (!entity.isValid()) {
                tracked.helper().onFinish();
                iterator.remove();
                continue;
            }
            Location location = entity.getLocation();
            tracked.helper().onTick(location.getX(), location.getY(), location.getZ(), entity.getVelocity());
            if (entity instanceof AbstractArrow arrow && arrow.isInBlock()) {
                tracked.helper().onFinish();
                iterator.remove();
            }
        }

        // Explosions that never reached an EntityExplodeEvent (cancelled, or they don't break blocks)
        if (this.currentExplosion != null) {
            this.finishExplosion(false, null);
        }
        this.orphanKnockbacks.clear();

        // HUD
        HUDController.updateHud(MinecraftServer.getServer(), null);
    }

    // ----- TNT ----- //

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntitySpawn(final EntitySpawnEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof TNTPrimed && LoggerRegistry.__tnt) {
            TNTLogHelper helper = new TNTLogHelper();
            Location location = entity.getLocation();
            helper.onPrimed(location.getX(), location.getY(), location.getZ(), entity.getVelocity());
            this.tntHelpers.put(entity.getUniqueId(), helper);
        }
        if (entity instanceof FallingBlock && LoggerRegistry.__fallingBlocks) {
            this.trajectories.put(entity.getUniqueId(), new Tracked(entity, new TrajectoryLogHelper("fallingBlocks")));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityRemove(final EntityRemoveFromWorldEvent event) {
        UUID uuid = event.getEntity().getUniqueId();
        this.tntHelpers.remove(uuid);
        Tracked tracked = this.trajectories.remove(uuid);
        if (tracked != null) {
            tracked.helper().onFinish();
        }
    }

    // ----- Projectiles ----- //

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileLaunch(final ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        if (LoggerRegistry.__projectiles && (projectile instanceof ThrowableProjectile || projectile instanceof AbstractArrow)) {
            this.trajectories.put(projectile.getUniqueId(), new Tracked(projectile, new TrajectoryLogHelper("projectiles")));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileHit(final ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof AbstractArrow arrow)) {
            return;
        }
        Tracked tracked = this.trajectories.remove(arrow.getUniqueId());
        if (tracked != null) {
            Location location = arrow.getLocation();
            tracked.helper().onTick(location.getX(), location.getY(), location.getZ(), arrow.getVelocity());
            tracked.helper().onFinish();
        }
    }

    // ----- Explosions ----- //

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplosionPrime(final ExplosionPrimeEvent event) {
        Entity entity = event.getEntity();
        Location location = entity.getLocation();

        TNTLogHelper tntHelper = this.tntHelpers.remove(entity.getUniqueId());
        if (tntHelper != null && LoggerRegistry.__tnt) {
            tntHelper.onExploded(location.getX(), location.getY(), location.getZ(), entity.getWorld().getGameTime());
        }

        if (LoggerRegistry.__explosions) {
            if (this.currentExplosion != null) {
                this.finishExplosion(false, null);
            }
            this.currentExplosion = new ExplosionLogHelper(location.getX(), location.getY(), location.getZ(), event.getRadius(), event.getFire());
            this.currentExplosionLocation = location;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKnockback(final EntityKnockbackEvent event) {
        if (!LoggerRegistry.__explosions || event.getCause() != EntityKnockbackEvent.Cause.EXPLOSION) {
            return;
        }
        if (this.currentExplosion != null) {
            this.currentExplosion.onEntityImpacted(event.getEntity(), event.getKnockback());
        } else {
            this.orphanKnockbacks.add(new ExplosionLogHelper.EntityChangedStatusWithCount(event.getEntity().getLocation().toVector(), event.getEntity().getType(), event.getKnockback().clone()));
        }
    }

    // HIGH so the blocks are logged before explosionNoBlockDamage clears them, like fabric-carpet does
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityExplode(final EntityExplodeEvent event) {
        if (!LoggerRegistry.__explosions) {
            return;
        }
        if (this.currentExplosion == null) {
            Location location = event.getLocation();
            this.currentExplosion = new ExplosionLogHelper(location.getX(), location.getY(), location.getZ(), null, null);
            this.currentExplosionLocation = location;
        }
        this.finishExplosion(!event.isCancelled() && !event.blockList().isEmpty(), event.getExplosionResult().name().toLowerCase(Locale.ROOT));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockExplode(final BlockExplodeEvent event) {
        if (!LoggerRegistry.__explosions) {
            return;
        }
        if (this.currentExplosion != null) {
            this.finishExplosion(false, null);
        }
        Block block = event.getBlock();
        ExplosionLogHelper helper = new ExplosionLogHelper(block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5, null, null);
        this.orphanKnockbacks.forEach(helper::onEntityImpacted);
        this.orphanKnockbacks.clear();
        helper.setAffectBlocks(!event.isCancelled() && !event.blockList().isEmpty());
        helper.setBlockDestructionType(event.getExplosionResult().name().toLowerCase(Locale.ROOT));
        helper.onExplosionDone(block.getWorld().getGameTime());
    }

    private void finishExplosion(boolean affectBlocks, @Nullable String destructionType) {
        ExplosionLogHelper helper = this.currentExplosion;
        Location location = this.currentExplosionLocation;
        this.currentExplosion = null;
        this.currentExplosionLocation = null;
        if (helper == null || location == null) {
            return;
        }
        helper.setAffectBlocks(affectBlocks);
        helper.setBlockDestructionType(destructionType);
        helper.onExplosionDone(location.getWorld().getGameTime());
    }
}
