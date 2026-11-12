package me.biquaternions.carpet.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import lombok.experimental.UtilityClass;
import me.biquaternions.carpet.CarpetSettings;
import me.biquaternions.carpet.logging.Logger;
import me.biquaternions.carpet.logging.LoggerRegistry;
import me.biquaternions.carpet.utils.CommandHelper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@NullMarked
@UtilityClass
@SuppressWarnings("UnstableApiUsage")
public class LogCommand {

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("log")
                .requires(CommandHelper.commandPermission(() -> CarpetSettings.commandLog, "carpet.log"))
                .executes(c -> listLogs(c.getSource()))
                .then(Commands.literal("clear")
                        .executes(c -> unsubFromAll(c.getSource(), c.getSource().getSender().getName()))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((c, b) -> suggestPlayers(b))
                                .executes(c -> unsubFromAll(c.getSource(), StringArgumentType.getString(c, "player")))))
                .then(Commands.argument("log name", StringArgumentType.word())
                        .suggests((c, b) -> suggest(LoggerRegistry.getLoggerNames(), b))
                        .executes(c -> toggleSubscription(c.getSource(), c.getSource().getSender().getName(), StringArgumentType.getString(c, "log name")))
                        .then(Commands.literal("clear")
                                .executes(c -> unsubFromLogger(c.getSource(), c.getSource().getSender().getName(), StringArgumentType.getString(c, "log name"))))
                        .then(Commands.argument("option", StringArgumentType.string())
                                .suggests((c, b) -> {
                                    Logger logger = LoggerRegistry.getLogger(StringArgumentType.getString(c, "log name"));
                                    return suggest(logger == null ? List.of() : Arrays.asList(logger.getOptions()), b);
                                })
                                .executes(c -> subscribePlayer(c.getSource(), c.getSource().getSender().getName(),
                                        StringArgumentType.getString(c, "log name"), StringArgumentType.getString(c, "option")))
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests((c, b) -> suggestPlayers(b))
                                        .executes(c -> subscribePlayer(c.getSource(), StringArgumentType.getString(c, "player"),
                                                StringArgumentType.getString(c, "log name"), StringArgumentType.getString(c, "option"))))))
                .build();
    }

    private CompletableFuture<Suggestions> suggest(Iterable<String> values, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                builder.suggest(value);
            }
        }
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestPlayers(SuggestionsBuilder builder) {
        return suggest(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), builder);
    }

    private Component clickable(String text, NamedTextColor color, String hover, String command) {
        return Component.text(text, color)
                .hoverEvent(HoverEvent.showText(Component.text(hover, NamedTextColor.WHITE)))
                .clickEvent(ClickEvent.runCommand(command));
    }

    private int listLogs(CommandSourceStack source) {
        if (!(source.getSender() instanceof Player player)) {
            source.getSender().sendMessage(Component.text("For players only"));
            return 0;
        }
        Map<String, String> subs = LoggerRegistry.getPlayerSubscriptions(player.getName());
        if (subs == null) {
            subs = new HashMap<>();
        }
        List<String> allLogs = new ArrayList<>(LoggerRegistry.getLoggerNames());
        allLogs.sort(null);
        player.sendMessage(Component.text("_____________________", NamedTextColor.WHITE));
        player.sendMessage(Component.text("Available logging options:", NamedTextColor.WHITE));
        for (String lname : allLogs) {
            Component line = Component.text(" - " + lname + ": ", NamedTextColor.WHITE);
            NamedTextColor color = subs.containsKey(lname) ? NamedTextColor.WHITE : NamedTextColor.GRAY;
            Logger logger = LoggerRegistry.getLogger(lname);
            String[] options = logger.getOptions();
            if (options.length == 0) {
                if (subs.containsKey(lname)) {
                    line = line.append(Component.text("Subscribed ", NamedTextColor.GREEN));
                } else {
                    line = line.append(clickable("[Subscribe] ", color, "subscribe to " + lname, "/log " + lname));
                }
            } else {
                for (String option : options) {
                    if (subs.containsKey(lname) && option.equalsIgnoreCase(subs.get(lname))) {
                        line = line.append(Component.text("[" + option + "] ", NamedTextColor.GREEN));
                    } else {
                        line = line.append(clickable("[" + option + "] ", color, "subscribe to " + lname + " " + option, "/log " + lname + " " + option));
                    }
                }
            }
            if (subs.containsKey(lname)) {
                line = line.append(clickable("[X]", NamedTextColor.DARK_RED, "Click to unsubscribe", "/log " + lname).decorate(TextDecoration.BOLD));
            }
            player.sendMessage(line);
        }
        return Command.SINGLE_SUCCESS;
    }

    private @Nullable Player findPlayer(CommandSender sender, String playerName) {
        Player player = Bukkit.getPlayerExact(playerName);
        if (player == null) {
            sender.sendMessage(Component.text("No player specified", NamedTextColor.RED));
        }
        return player;
    }

    private boolean checkLogger(CommandSender sender, String logName) {
        if (LoggerRegistry.getLogger(logName) == null) {
            sender.sendMessage(Component.text("Unknown logger: ", NamedTextColor.RED).append(Component.text(logName, NamedTextColor.RED, TextDecoration.BOLD)));
            return false;
        }
        return true;
    }

    private int unsubFromAll(CommandSourceStack source, String playerName) {
        CommandSender sender = source.getSender();
        if (findPlayer(sender, playerName) == null) {
            return 0;
        }
        for (String logname : List.copyOf(LoggerRegistry.getLoggerNames())) {
            LoggerRegistry.unsubscribePlayer(playerName, logname);
        }
        sender.sendMessage(Component.text("Unsubscribed from all logs", NamedTextColor.GRAY, TextDecoration.ITALIC));
        return Command.SINGLE_SUCCESS;
    }

    private int unsubFromLogger(CommandSourceStack source, String playerName, String logname) {
        CommandSender sender = source.getSender();
        if (findPlayer(sender, playerName) == null || !checkLogger(sender, logname)) {
            return 0;
        }
        LoggerRegistry.unsubscribePlayer(playerName, logname);
        sender.sendMessage(Component.text("Unsubscribed from " + logname, NamedTextColor.GRAY, TextDecoration.ITALIC));
        return Command.SINGLE_SUCCESS;
    }

    private int toggleSubscription(CommandSourceStack source, String playerName, String logName) {
        CommandSender sender = source.getSender();
        if (findPlayer(sender, playerName) == null || !checkLogger(sender, logName)) {
            return 0;
        }
        boolean subscribed = LoggerRegistry.togglePlayerSubscription(playerName, logName);
        if (subscribed) {
            sender.sendMessage(Component.text(playerName + " subscribed to " + logName + ".", NamedTextColor.GRAY, TextDecoration.ITALIC));
        } else {
            sender.sendMessage(Component.text(playerName + " unsubscribed from " + logName + ".", NamedTextColor.GRAY, TextDecoration.ITALIC));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int subscribePlayer(CommandSourceStack source, String playerName, String logname, @Nullable String option) {
        CommandSender sender = source.getSender();
        if (findPlayer(sender, playerName) == null || !checkLogger(sender, logname)) {
            return 0;
        }
        if (!LoggerRegistry.getLogger(logname).isOptionValid(option)) {
            sender.sendMessage(Component.text("Invalid option: ", NamedTextColor.RED).append(Component.text(String.valueOf(option), NamedTextColor.RED, TextDecoration.BOLD)));
            return 0;
        }
        LoggerRegistry.subscribePlayer(playerName, logname, option);
        if (option != null) {
            sender.sendMessage(Component.text("Subscribed to " + logname + "(" + option + ")", NamedTextColor.GRAY, TextDecoration.ITALIC));
        } else {
            sender.sendMessage(Component.text("Subscribed to " + logname, NamedTextColor.GRAY, TextDecoration.ITALIC));
        }
        return Command.SINGLE_SUCCESS;
    }

}
