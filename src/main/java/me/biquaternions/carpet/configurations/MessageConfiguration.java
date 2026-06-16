package me.biquaternions.carpet.configurations;

import me.biquaternions.carpet.PaperCarpet;
import net.j4c0b3y.api.config.StaticConfig;
import net.j4c0b3y.api.config.platform.adventure.types.PrefixedComponent;

public class MessageConfiguration extends StaticConfig {

    @Ignore
    public static MessageConfiguration INSTANCE;

    public MessageConfiguration(PaperCarpet plugin) {
        super(plugin.getDataPath().resolve("messages.yml"), plugin.getConfigHandler());
        INSTANCE = this;
    }

    @Priority(1)
    @SuppressWarnings("unused")
    public static class INFO {
        public static String VERSION = "1.0";
    }

    public static PrefixedComponent CMD_DRAW_BLOCKS_FILLED = new PrefixedComponent("Filled <blocks> blocks");

}
