package me.biquaternions.carpet.logging;

import me.biquaternions.carpet.CarpetServer;
import org.bukkit.DyeColor;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class LoggerRegistry {

    // Map from logger names to loggers.
    private static final Map<String, Logger> LOGGER_REGISTRY = new HashMap<>();
    // Map from player names to the set of names of the logs that player is subscribed to.
    private static final Map<String, Map<String, String>> PLAYER_SUBSCRIPTIONS = new HashMap<>();
    private static final Set<String> SEEN_PLAYERS = new HashSet<>();

    //statics to quickly asses if its worth even to call each one
    public static boolean __tnt;
    public static boolean __projectiles;
    public static boolean __fallingBlocks;
    public static boolean __tps;
    public static boolean __counter;
    public static boolean __mobcaps;
    public static boolean __packets;
    public static boolean __explosions;

    public static void initLoggers() {
        stopLoggers();
        registerLoggers();
    }

    public static void registerLoggers() {
        registerLogger("tnt", Logger.standardLogger("tnt", "brief", new String[]{"brief", "full"}, true));
        registerLogger("projectiles", Logger.standardLogger("projectiles", "brief", new String[]{"brief", "full"}));
        registerLogger("fallingBlocks", Logger.standardLogger("fallingBlocks", "brief", new String[]{"brief", "full"}));
        registerLogger("tps", HUDLogger.standardHUDLogger("tps", null, null));
        registerLogger("packets", HUDLogger.standardHUDLogger("packets", null, null));
        registerLogger("counter", HUDLogger.standardHUDLogger("counter", "white", Arrays.stream(DyeColor.values()).map(c -> c.name().toLowerCase(Locale.ROOT)).toArray(String[]::new)));
        registerLogger("mobcaps", HUDLogger.standardHUDLogger("mobcaps", "dynamic", new String[]{"dynamic", "overworld", "nether", "end"}));
        registerLogger("explosions", Logger.standardLogger("explosions", "brief", new String[]{"brief", "full"}, true));
    }

    /**
     * Gets the logger with the given name. Returns null if no such logger exists.
     */
    public static @Nullable Logger getLogger(String name) {
        return LOGGER_REGISTRY.get(name);
    }

    /**
     * Gets the set of logger names.
     */
    public static Set<String> getLoggerNames() {
        return LOGGER_REGISTRY.keySet();
    }

    /**
     * Subscribes the player with name playerName to the log with name logName.
     */
    public static void subscribePlayer(String playerName, String logName, @Nullable String option) {
        Logger log = LOGGER_REGISTRY.get(logName);
        if (option == null) {
            option = log.getDefault();
        }
        PLAYER_SUBSCRIPTIONS.computeIfAbsent(playerName, k -> new HashMap<>()).put(logName, option);
        log.addPlayer(playerName, option);
    }

    /**
     * Unsubscribes the player with name playerName from the log with name logName.
     */
    public static void unsubscribePlayer(String playerName, String logName) {
        Map<String, String> subscriptions = PLAYER_SUBSCRIPTIONS.get(playerName);
        if (subscriptions != null) {
            subscriptions.remove(logName);
            LOGGER_REGISTRY.get(logName).removePlayer(playerName);
            if (subscriptions.isEmpty()) {
                PLAYER_SUBSCRIPTIONS.remove(playerName);
            }
        }
    }

    /**
     * If the player is not subscribed to the log, then subscribe them. Otherwise, unsubscribe them.
     */
    public static boolean togglePlayerSubscription(String playerName, String logName) {
        if (PLAYER_SUBSCRIPTIONS.containsKey(playerName) && PLAYER_SUBSCRIPTIONS.get(playerName).containsKey(logName)) {
            unsubscribePlayer(playerName, logName);
            return false;
        } else {
            subscribePlayer(playerName, logName, null);
            return true;
        }
    }

    /**
     * Get the set of logs the current player is subscribed to.
     */
    public static @Nullable Map<String, String> getPlayerSubscriptions(String playerName) {
        return PLAYER_SUBSCRIPTIONS.get(playerName);
    }

    static void setAccess(Logger logger) {
        boolean value = logger.hasOnlineSubscribers();
        try {
            Field f = logger.getField();
            if (f != null) {
                f.setBoolean(null, value);
            }
        } catch (IllegalAccessException e) {
            CarpetServer.LOG.severe("Cannot change logger quick access field");
        }
    }

    /**
     * Registers a logger.
     */
    public static void registerLogger(String name, Logger logger) {
        LOGGER_REGISTRY.put(name, logger);
        setAccess(logger);
    }

    public static void stopLoggers() {
        for (Logger log : LOGGER_REGISTRY.values()) {
            log.serverStopped();
            Field field = log.getField();
            if (field != null) {
                try {
                    field.setBoolean(null, false);
                } catch (IllegalAccessException ignored) {
                }
            }
        }
        SEEN_PLAYERS.clear();
        LOGGER_REGISTRY.clear();
        PLAYER_SUBSCRIPTIONS.clear();
    }

    public static void playerConnected(Player player) {
        boolean firstTime = SEEN_PLAYERS.add(player.getName());
        for (Logger log : LOGGER_REGISTRY.values()) {
            log.onPlayerConnect(player, firstTime);
        }
    }

    public static void playerDisconnected(Player player) {
        for (Logger log : LOGGER_REGISTRY.values()) {
            log.onPlayerDisconnect(player);
        }
    }

}
