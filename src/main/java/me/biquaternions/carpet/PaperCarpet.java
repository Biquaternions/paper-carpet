package me.biquaternions.carpet;

import lombok.Getter;
import me.biquaternions.carpet.configurations.CarpetConfiguration;
import me.biquaternions.carpet.configurations.MessageConfiguration;
import me.biquaternions.carpet.listener.HopperListener;
import me.biquaternions.carpet.listener.WorldListener;
import net.j4c0b3y.api.config.ConfigHandler;
import net.j4c0b3y.api.config.platform.adventure.AdventureConfigHandler;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class PaperCarpet extends JavaPlugin {

    @Getter
    private ConfigHandler configHandler;

    @Override
    public void onLoad() {
        this.configHandler = new AdventureConfigHandler(this.getLogger(), MiniMessage.miniMessage().deserialize(
                "<color:#C7C7C7>[<gradient:#2EFFCE:#B8FEFF:#2EFFCE>Carpet</gradient>]</color>"
        ));
        new CarpetConfiguration(this).load();
        new MessageConfiguration(this).load();
    }

    @Override
    public void onEnable() {
        PluginManager manager = this.getServer().getPluginManager();
        manager.registerEvents(new HopperListener(), this);
        manager.registerEvents(new WorldListener(), this);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

}
