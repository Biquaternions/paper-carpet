package me.biquaternions.carpet.logging;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;

/**
 * A {@link Logger} that displays its messages in the player list footer instead of the chat.
 */
public class HUDLogger extends Logger {

    static Logger standardHUDLogger(String logName, @Nullable String def, String @Nullable [] options) {
        return standardHUDLogger(logName, def, options, false);
    }

    static Logger standardHUDLogger(String logName, @Nullable String def, String @Nullable [] options, boolean strictOptions) {
        try {
            return new HUDLogger(LoggerRegistry.class.getField("__" + logName), logName, def, options, strictOptions);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("Failed to create logger " + logName);
        }
    }

    public HUDLogger(@Nullable Field field, String logName, @Nullable String def, String @Nullable [] options, boolean strictOptions) {
        super(field, logName, def, options, strictOptions);
    }

    @Override
    public void removePlayer(String playerName) {
        Player player = this.playerFromName(playerName);
        if (player != null) {
            HUDController.clearPlayer(player);
        }
        super.removePlayer(playerName);
    }

    @Override
    public void sendPlayerMessage(Player player, Component... messages) {
        for (Component message : messages) {
            HUDController.addMessage(player, message);
        }
    }

}
