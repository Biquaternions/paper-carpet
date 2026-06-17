package me.biquaternions.carpet.listener;

import me.biquaternions.carpet.helper.HopperCounter;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class WorldListener implements Listener {

    @EventHandler
    public void onWorldLoad(final WorldLoadEvent event) {
        HopperCounter.registerWorld(event.getWorld());
    }

    @EventHandler
    public void onWorldUnload(final WorldUnloadEvent event) {
        HopperCounter.unregisterWorld(event.getWorld());
    }

}
