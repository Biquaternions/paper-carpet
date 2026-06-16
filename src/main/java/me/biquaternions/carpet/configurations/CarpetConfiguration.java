package me.biquaternions.carpet.configurations;

import me.biquaternions.carpet.PaperCarpet;
import net.j4c0b3y.api.config.StaticConfig;

public class CarpetConfiguration extends StaticConfig {

    @Ignore
    public static CarpetConfiguration INSTANCE;

    public CarpetConfiguration(PaperCarpet plugin) {
        super(plugin.getDataPath().resolve("config.yml"), plugin.getConfigHandler());
        INSTANCE = this;
    }

    @Priority(1)
    @SuppressWarnings("unused")
    public static class INFO {
        public static String VERSION = "1.0";
    }

}
