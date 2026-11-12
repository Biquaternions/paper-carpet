package me.biquaternions.carpet.logging;

import me.biquaternions.carpet.CarpetServer;
import me.biquaternions.carpet.CarpetSettings;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public class Logger {

    // The set of subscribed and online players.
    private final Map<String, String> subscribedOnlinePlayers = new HashMap<>();

    // The set of subscribed and offline players.
    private final Map<String, String> subscribedOfflinePlayers = new HashMap<>();

    // The logName of this log. Gets prepended to logged messages.
    private final String logName;

    private final @Nullable String defaultOption;

    private final String[] options;

    private final @Nullable Field acceleratorField;

    private final boolean strictOptions;

    static Logger standardLogger(String logName, @Nullable String def, String @Nullable [] options) {
        return standardLogger(logName, def, options, false);
    }

    static Logger standardLogger(String logName, @Nullable String def, String @Nullable [] options, boolean strictOptions) {
        try {
            return new Logger(LoggerRegistry.class.getField("__" + logName), logName, def, options, strictOptions);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("Failed to create logger " + logName);
        }
    }

    public Logger(@Nullable Field acceleratorField, String logName, @Nullable String def, String @Nullable [] options, boolean strictOptions) {
        this.acceleratorField = acceleratorField;
        this.logName = logName;
        this.defaultOption = def;
        this.options = options == null ? new String[0] : options;
        this.strictOptions = strictOptions;
        if (acceleratorField == null) {
            CarpetServer.LOG.severe("Logger " + this.getLogName() + " is missing a specified accelerator");
        }
    }

    public @Nullable String getDefault() {
        return this.defaultOption;
    }

    public String[] getOptions() {
        return this.options;
    }

    public String getLogName() {
        return this.logName;
    }

    /**
     * Subscribes the player with the given logName to the logger.
     */
    public void addPlayer(String playerName, @Nullable String option) {
        if (this.playerFromName(playerName) != null) {
            this.subscribedOnlinePlayers.put(playerName, option);
        } else {
            this.subscribedOfflinePlayers.put(playerName, option);
        }
        LoggerRegistry.setAccess(this);
    }

    /**
     * Unsubscribes the player with the given logName from the logger.
     */
    public void removePlayer(String playerName) {
        this.subscribedOnlinePlayers.remove(playerName);
        this.subscribedOfflinePlayers.remove(playerName);
        LoggerRegistry.setAccess(this);
    }

    /**
     * Returns true if there are any online subscribers for this log.
     */
    public boolean hasOnlineSubscribers() {
        return !this.subscribedOnlinePlayers.isEmpty();
    }

    public void serverStopped() {
        this.subscribedOnlinePlayers.clear();
        this.subscribedOfflinePlayers.clear();
    }

    public @Nullable Field getField() {
        return this.acceleratorField;
    }

    /**
     * serves messages to players fetching them from the promise
     * will repeat invocation for players that share the same option
     */
    @FunctionalInterface
    public interface PlayerMessage {
        Component @Nullable [] get(String playerOption, Player player);
    }

    public void log(PlayerMessage messagePromise) {
        for (Map.Entry<String, String> entry : Map.copyOf(this.subscribedOnlinePlayers).entrySet()) {
            Player player = this.playerFromName(entry.getKey());
            if (player != null) {
                Component[] messages = messagePromise.get(entry.getValue(), player);
                if (messages != null) {
                    this.sendPlayerMessage(player, messages);
                }
            }
        }
    }

    /**
     * guarantees that each message for each option will be evaluated once from the promise
     * and served the same way to all other players subscribed to the same option
     */
    @FunctionalInterface
    public interface OptionMessage {
        Component @Nullable [] get(String playerOption);
    }

    public void log(OptionMessage messagePromise) {
        Map<String, Component[]> cannedMessages = new HashMap<>();
        for (Map.Entry<String, String> entry : Map.copyOf(this.subscribedOnlinePlayers).entrySet()) {
            Player player = this.playerFromName(entry.getKey());
            if (player != null) {
                String option = entry.getValue();
                Component[] messages = cannedMessages.computeIfAbsent(option, messagePromise::get);
                if (messages != null) {
                    this.sendPlayerMessage(player, messages);
                }
            }
        }
    }

    /**
     * guarantees that message is evaluated once, so independent of the player and chosen option
     */
    public void log(Supplier<Component[]> messagePromise) {
        Component[] cannedMessages = null;
        for (String name : Set.copyOf(this.subscribedOnlinePlayers.keySet())) {
            Player player = this.playerFromName(name);
            if (player != null) {
                if (cannedMessages == null) {
                    cannedMessages = messagePromise.get();
                }
                this.sendPlayerMessage(player, cannedMessages);
            }
        }
    }

    public void sendPlayerMessage(Player player, Component... messages) {
        Arrays.stream(messages).forEach(player::sendMessage);
    }

    /**
     * Gets the {@link Player} instance for a player given their name. Returns null if they are offline.
     */
    protected @Nullable Player playerFromName(String name) {
        return Bukkit.getPlayerExact(name);
    }

    // ----- Event Handlers ----- //

    public void onPlayerConnect(Player player, boolean firstTime) {
        // If the player was subscribed to the log and offline, move them to the set of online subscribers.
        String playerName = player.getName();
        if (this.subscribedOfflinePlayers.containsKey(playerName)) {
            this.subscribedOnlinePlayers.put(playerName, this.subscribedOfflinePlayers.get(playerName));
            this.subscribedOfflinePlayers.remove(playerName);
        } else if (firstTime) {
            Set<String> loggingOptions = new HashSet<>(Arrays.asList(CarpetSettings.defaultLoggers.split(",")));
            String logName = this.getLogName();
            for (String str : loggingOptions) {
                String[] vars = str.trim().split(" ", 2);
                if (vars[0].equals(logName)) {
                    LoggerRegistry.subscribePlayer(playerName, this.getLogName(), vars.length == 1 ? this.getDefault() : vars[1]);
                    break;
                }
            }
        }
        LoggerRegistry.setAccess(this);
    }

    public void onPlayerDisconnect(Player player) {
        // If the player was subscribed to the log, move them to the set of offline subscribers.
        String playerName = player.getName();
        if (this.subscribedOnlinePlayers.containsKey(playerName)) {
            this.subscribedOfflinePlayers.put(playerName, this.subscribedOnlinePlayers.get(playerName));
            this.subscribedOnlinePlayers.remove(playerName);
        }
        LoggerRegistry.setAccess(this);
    }

    public @Nullable String getAcceptedOption(String arg) {
        if (Arrays.asList(this.getOptions()).contains(arg)) {
            return arg;
        }
        return null;
    }

    public boolean isOptionValid(@Nullable String option) {
        if (this.strictOptions) {
            return Arrays.asList(this.getOptions()).contains(option);
        }
        return option != null;
    }

}
