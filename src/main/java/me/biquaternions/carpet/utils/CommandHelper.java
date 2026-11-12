package me.biquaternions.carpet.utils;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import lombok.experimental.UtilityClass;
import me.biquaternions.carpet.PaperCarpet;
import net.minecraft.commands.Commands;
import net.minecraft.server.permissions.PermissionSet;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * <p>A few helpful methods to work with settings and commands.</p>
 *
 * <p>Command rules in fabric-carpet take a "command level" ({@code true}, {@code false}, {@code ops} or {@code 0}-{@code 4}).
 * On Paper, a permission node can additionally be granted to bypass the level check, as long as the rule is not
 * set to {@code false}.</p>
 */
@NullMarked
@UtilityClass
public class CommandHelper {

    /**
     * Creates a command requirement that reads the rule value on every check.
     *
     * @param commandLevel supplier of the rule value
     * @param permission permission node that grants access regardless of the permission level, unless the rule is {@code false}
     */
    public Predicate<CommandSourceStack> commandPermission(final Supplier<?> commandLevel, final String permission) {
        return source -> canUseCommand(source, commandLevel.get(), permission);
    }

    /**
     * Whether the given source can use a command that requires the given command level.
     */
    public boolean canUseCommand(final CommandSourceStack source, final Object commandLevel, final String permission) {
        String level = commandLevel.toString();
        if (level.equals("false")) {
            return false;
        }
        CommandSender sender = source.getSender();
        if (sender.isPermissionSet(permission)) {
            return sender.hasPermission(permission);
        }
        return canUseCommand(source, commandLevel);
    }

    /**
     * Whether the given source has enough permission level to run a command that requires the given commandLevel
     */
    public boolean canUseCommand(final CommandSourceStack source, final Object commandLevel) {
        if (commandLevel instanceof Boolean bool) {
            return bool;
        }
        PermissionSet permissions = ((net.minecraft.commands.CommandSourceStack) source).permissions();
        return switch (commandLevel.toString()) {
            case "true", "0" -> true;
            case "false" -> false;
            case "1" -> Commands.LEVEL_MODERATORS.check(permissions);
            case "ops", "2" -> Commands.LEVEL_GAMEMASTERS.check(permissions); // typical for other cheaty commands
            case "3" -> Commands.LEVEL_ADMINS.check(permissions);
            case "4" -> Commands.LEVEL_OWNERS.check(permissions);
            default -> false;
        };
    }

    /**
     * Whether the given source has the given permission level, from 0 to 4.
     */
    public boolean hasPermissionLevel(final CommandSourceStack source, final int level) {
        return canUseCommand(source, String.valueOf(level));
    }

    /**
     * Notifies all players that the commands changed by resending the command tree.
     */
    public void notifyPlayersCommandsChanged() {
        PaperCarpet plugin = PaperCarpet.getInstance();
        if (plugin == null || !plugin.isEnabled()) {
            return;
        }
        Bukkit.getGlobalRegionScheduler().execute(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.updateCommands();
            }
        });
    }

}
