package me.biquaternions.carpet;

import lombok.Getter;
import me.biquaternions.carpet.configurations.CarpetConfiguration;
import me.biquaternions.carpet.configurations.MessageConfiguration;
import me.biquaternions.carpet.helper.HopperCounter;
import me.biquaternions.carpet.listener.HopperListener;
import me.biquaternions.carpet.listener.LoggingListener;
import me.biquaternions.carpet.listener.MobAIListener;
import me.biquaternions.carpet.listener.RulesListener;
import me.biquaternions.carpet.listener.ServerListener;
import me.biquaternions.carpet.listener.WorldListener;
import me.biquaternions.carpet.logging.LoggerRegistry;
import me.biquaternions.carpet.logging.helpers.PacketCounter;
import me.biquaternions.carpet.utils.MobAI;
import me.biquaternions.carpet.utils.SpawnReporter;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;

public final class PaperCarpet extends JavaPlugin {

    @Getter
    private static @Nullable PaperCarpet instance;

    @Override
    public void onLoad() {
        instance = this;
        CarpetConfiguration.load(this);
        MessageConfiguration.load(this);
    }

    @Override
    public void onEnable() {
        PluginManager manager = this.getServer().getPluginManager();
        manager.registerEvents(new HopperListener(), this);
        manager.registerEvents(new WorldListener(), this);
        manager.registerEvents(new ServerListener(), this);
        manager.registerEvents(new LoggingListener(), this);
        manager.registerEvents(new RulesListener(), this);
        manager.registerEvents(new MobAIListener(), this);

        // Worlds loaded before the plugin was enabled (e.g. after a reload)
        for (World world : this.getServer().getWorlds()) {
            HopperCounter.registerWorld(world);
        }

        LoggerRegistry.initLoggers();
        MobAI.resetTrackers();
        for (Player player : this.getServer().getOnlinePlayers()) {
            LoggerRegistry.playerConnected(player);
            PacketCounter.inject(player);
        }

        // Tasks run once the server has finished loading, so the rules can safely touch the worlds
        this.getServer().getScheduler().runTask(this, () -> {
            CarpetServer.settingsManager.loadConfiguration();
            SpawnReporter.resetSpawnStats();
            HopperCounter.resetAll(true);
        });
    }

    @Override
    public void onDisable() {
        for (Player player : this.getServer().getOnlinePlayers()) {
            PacketCounter.uninject(player);
        }
        LoggerRegistry.stopLoggers();
        SpawnReporter.stopTracking();
        CarpetServer.settingsManager.detach();
        instance = null;
    }

}
