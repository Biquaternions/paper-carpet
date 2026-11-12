package me.biquaternions.carpet;

import lombok.Getter;
import me.biquaternions.carpet.configurations.CarpetConfiguration;
import me.biquaternions.carpet.configurations.MessageConfiguration;
import me.biquaternions.carpet.fakes.ActionPacks;
import me.biquaternions.carpet.fakes.FakePlayer;
import me.biquaternions.carpet.helper.HopperCounter;
import me.biquaternions.carpet.listener.FakePlayerListener;
import me.biquaternions.carpet.listener.ScarpetListener;
import me.biquaternions.carpet.logging.HUDController;
import me.biquaternions.carpet.script.CarpetScriptServer;
import me.biquaternions.carpet.script.external.Carpet;
import me.biquaternions.carpet.script.external.Vanilla;
import net.minecraft.server.MinecraftServer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import io.papermc.paper.event.server.ServerResourcesReloadedEvent;
import me.biquaternions.carpet.listener.HopperListener;
import me.biquaternions.carpet.listener.LoggingListener;
import me.biquaternions.carpet.listener.MobAIListener;
import me.biquaternions.carpet.listener.RulesListener;
import me.biquaternions.carpet.listener.ServerListener;
import me.biquaternions.carpet.listener.WorldListener;
import me.biquaternions.carpet.logging.LoggerRegistry;
import me.biquaternions.carpet.logging.helpers.PacketCounter;
import me.biquaternions.carpet.network.ServerNetworkHandler;
import me.biquaternions.carpet.utils.MobAI;
import me.biquaternions.carpet.utils.SpawnReporter;
import net.minecraft.network.DisconnectionDetails;
import org.bukkit.World;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;

import java.util.List;

public final class PaperCarpet extends JavaPlugin {

    @Getter
    private static @Nullable PaperCarpet instance;

    @Getter
    private @Nullable ServerNetworkHandler networkHandler;

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
        manager.registerEvents(new FakePlayerListener(), this);
        manager.registerEvents(new ScarpetListener(this), this);
        manager.registerEvents(new Listener() {
            @EventHandler
            public void onResourcesReloaded(final ServerResourcesReloadedEvent event) {
                CarpetScriptServer scriptServer = Vanilla.MinecraftServer_getScriptServer(MinecraftServer.getServer());
                if (scriptServer != null) {
                    scriptServer.reload(MinecraftServer.getServer());
                }
            }
        }, this);
        CarpetScriptServer.parseFunctionClasses();
        this.networkHandler = new ServerNetworkHandler(this);
        this.networkHandler.register();

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
            MinecraftServer server = MinecraftServer.getServer();
            CarpetScriptServer scriptServer = new CarpetScriptServer(server);
            Carpet.MinecraftServer_addScriptServer(server, scriptServer);
            scriptServer.initializeForWorld();
        });
    }

    @Override
    public void onDisable() {
        // Fake players are instances of this plugin's classes, they must not outlive it
        for (Player player : List.copyOf(this.getServer().getOnlinePlayers())) {
            if (((CraftPlayer) player).getHandle() instanceof FakePlayer fake) {
                fake.connection.onDisconnect(new DisconnectionDetails(net.minecraft.network.chat.Component.literal("Carpet disabled")));
            }
        }
        ActionPacks.clear();
        CarpetScriptServer scriptServer = Vanilla.MinecraftServer_getScriptServer(MinecraftServer.getServer());
        if (scriptServer != null) {
            scriptServer.onClose();
            Vanilla.MinecraftServer_setScriptServer(null);
        }
        HUDController.resetScarpetHUDs();
        if (this.networkHandler != null) {
            this.networkHandler.unregister();
        }
        for (Player player : this.getServer().getOnlinePlayers()) {
            PacketCounter.uninject(player);
        }
        LoggerRegistry.stopLoggers();
        SpawnReporter.stopTracking();
        CarpetServer.settingsManager.detach();
        instance = null;
    }

}
