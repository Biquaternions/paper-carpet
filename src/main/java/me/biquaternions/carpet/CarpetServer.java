package me.biquaternions.carpet;

import lombok.experimental.UtilityClass;
import me.biquaternions.carpet.api.settings.SettingsManager;

import java.util.logging.Logger;

/**
 * <p>Static holder of the carpet state, equivalent to fabric-carpet's {@code CarpetServer}.</p>
 *
 * <p>The {@link SettingsManager} needs to exist before the plugin is enabled, since the {@code /carpet} command is
 * registered from the bootstrapper.</p>
 */
@UtilityClass
public class CarpetServer {

    public final String VERSION = "1.0-SNAPSHOT";
    public final Logger LOG = Logger.getLogger("Carpet");
    public final org.slf4j.Logger SLF4J = org.slf4j.LoggerFactory.getLogger("Carpet");

    public final SettingsManager settingsManager = createSettingsManager();

    private SettingsManager createSettingsManager() {
        SettingsManager manager = new SettingsManager(VERSION, "carpet", "Carpet Mod", LOG, () -> CarpetSettings.carpetCommandPermissionLevel);
        manager.parseSettingsClass(CarpetSettings.class);
        return manager;
    }

}
