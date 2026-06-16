package me.biquaternions.carpet;

import lombok.Getter;
import net.j4c0b3y.api.config.ConfigHandler;
import net.j4c0b3y.api.config.platform.adventure.AdventureConfigHandler;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.plugin.java.JavaPlugin;

public final class PaperCarpet extends JavaPlugin {

    @Getter
    private ConfigHandler configHandler;

    @Override
    public void onLoad() {
        this.configHandler = new AdventureConfigHandler(this.getLogger(), MiniMessage.miniMessage().deserialize("[Carpet]"));
    }

    @Override
    public void onEnable() {
        // Plugin startup logic
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
