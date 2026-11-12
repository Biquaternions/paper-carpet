package me.biquaternions.carpet.configurations;

import de.bsommerfeld.jshepherd.annotation.Comment;
import de.bsommerfeld.jshepherd.annotation.Key;
import de.bsommerfeld.jshepherd.core.ConfigurablePojo;
import de.bsommerfeld.jshepherd.core.ConfigurationLoader;
import me.biquaternions.carpet.PaperCarpet;
import me.biquaternions.jshepherd.annotation.Order;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * <p>Main configuration of the plugin.</p>
 *
 * <p>This replaces fabric-carpet's {@code carpet.conf} world file. The rules stored in {@link #rules} are the
 * "defaults" set through {@code /carpet setDefault} and are applied on every startup.</p>
 */
@Comment({
        "Paper Carpet main configuration",
        "Rules stored here are applied on every startup, use '/carpet setDefault <rule> <value>' to add them from in-game"
})
public class CarpetConfiguration extends ConfigurablePojo<CarpetConfiguration> {

    public static CarpetConfiguration INSTANCE;

    @Order(0)
    @Key("version")
    @Comment("Do not touch")
    public String version = "1.0";

    @Order(1)
    @Key("locked")
    @Comment({
            "Locks the carpet settings, making /carpet unavailable and disabling every command rule",
            "Equivalent to having 'locked' in fabric-carpet's carpet.conf"
    })
    public boolean locked = false;

    @Order(2)
    @Key("rules")
    @Comment("Rule values applied on startup, in the form 'ruleName: value'")
    public Map<String, String> rules = new LinkedHashMap<>();

    public static CarpetConfiguration load(PaperCarpet plugin) {
        INSTANCE = ConfigurationLoader.load(plugin.getDataPath().resolve("config.yml"), CarpetConfiguration::new);
        if (INSTANCE.rules == null) {
            INSTANCE.rules = new LinkedHashMap<>();
        }
        return INSTANCE;
    }

}
