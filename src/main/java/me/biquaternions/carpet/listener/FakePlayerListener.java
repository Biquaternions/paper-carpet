package me.biquaternions.carpet.listener;

import com.destroystokyo.paper.event.server.ServerTickStartEvent;
import me.biquaternions.carpet.fakes.ActionPacks;
import me.biquaternions.carpet.fakes.FakePlayer;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class FakePlayerListener implements Listener {

    @EventHandler
    public void onTickStart(final ServerTickStartEvent event) {
        ActionPacks.tickRealPlayers();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(final PlayerQuitEvent event) {
        ActionPacks.remove(event.getPlayer().getUniqueId());
    }

    /**
     * Real players get their velocity reset after being attacked, since the client is the one moving them. Fake players
     * have no client, so they keep it, like fabric-carpet's {@code Player_fakePlayersMixin} does.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onVelocity(final PlayerVelocityEvent event) {
        if (((CraftPlayer) event.getPlayer()).getHandle() instanceof FakePlayer fake) {
            event.setCancelled(true);
            fake.syncVelocity = false;
        }
    }

}
