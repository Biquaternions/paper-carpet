package me.biquaternions.carpet.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import lombok.experimental.UtilityClass;
import me.biquaternions.carpet.CarpetServer;
import me.biquaternions.carpet.utils.CommandHelper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.jspecify.annotations.NullMarked;

import java.util.Map;

/**
 * <p>Port of fabric-carpet's development {@code /testcarpet} command.</p>
 *
 * <p>fabric-carpet only registers it in a development environment, here it's registered when the
 * {@code carpet.debug} system property is set, and requires the {@code carpet.test} permission or op level 4.</p>
 */
@NullMarked
@UtilityClass
public class TestCommand {

    private final Map<Character, NamedTextColor> COLORS = Map.of(
            'w', NamedTextColor.WHITE, 'y', NamedTextColor.YELLOW, 'r', NamedTextColor.RED,
            'l', NamedTextColor.GREEN, 'c', NamedTextColor.AQUA, 'g', NamedTextColor.GRAY,
            'd', NamedTextColor.GOLD, 'm', NamedTextColor.LIGHT_PURPLE, 'b', NamedTextColor.BLUE
    );

    public boolean shouldRegister() {
        return Boolean.getBoolean("carpet.debug");
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("testcarpet")
                .requires(CommandHelper.commandPermission(() -> "4", "carpet.test"))
                .then(Commands.literal("dump")
                        .executes(c -> CarpetServer.settingsManager.dumpAllRulesToStream(System.out, null))
                        .then(Commands.argument("category", StringArgumentType.word())
                                .executes(c -> CarpetServer.settingsManager.dumpAllRulesToStream(System.out, StringArgumentType.getString(c, "category")))))
                .then(Commands.argument("first", StringArgumentType.word())
                        .executes(c -> test(c.getSource(), StringArgumentType.getString(c, "first") + " 1")))
                .build();
    }

    private int test(CommandSourceStack source, String term) {
        NamedTextColor color = COLORS.getOrDefault(term.charAt(0), NamedTextColor.WHITE);
        source.getSender().sendMessage(Component.text(term + ": how did you get here?", color));
        return Command.SINGLE_SUCCESS;
    }

}
