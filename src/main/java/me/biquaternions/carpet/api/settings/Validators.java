package me.biquaternions.carpet.api.settings;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import lombok.experimental.UtilityClass;
import me.biquaternions.carpet.utils.CommandHelper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.List;

/**
 * <p>A collection of standard {@link Validator validators} you can use in your rules.</p>
 *
 * @see Rule
 * @see Rule#validators()
 *
 */
@UtilityClass
public final class Validators {

    /**
     * <p>A {@link Validator} that checks whether the given {@link String} value was a valid command level as Carpet allows them,
     * so either a number from 0 to 4, or one of the keywords {@code true}, {@code false} or {@code ops} </p>
     */
    public static class CommandLevel extends Validator<String> {

        public static final List<String> OPTIONS = List.of("true", "false", "ops", "0", "1", "2", "3", "4");

        @Override
        public String validate(CommandSourceStack source, CarpetRule<String> currentRule, String newValue, String userString) {
            if (!OPTIONS.contains(newValue)) {
                return null;
            }
            return newValue;
        }

        @Override
        public String description() {
            return "Can be limited to 'ops' only, true/false for everyone/no one, or a custom permission level";
        }

    }

    /**
     * <p>A {@link Validator} that checks whether the entered number is equal or greater than {@code 0}.</p>
     */
    public static class NonNegativeNumber<T extends Number> extends Validator<T> {

        @Override
        public T validate(CommandSourceStack source, CarpetRule<T> currentRule, T newValue, String string) {
            return newValue.doubleValue() >= 0 ? newValue : null;
        }

        @Override
        public String description() {
            return "Must be a positive number or 0";
        }

    }

    /**
     * <p>A {@link Validator} that checks whether the entered number is between 0 and 1, inclusive.</p>
     */
    public static class Probability<T extends Number> extends Validator<T> {

        @Override
        public T validate(CommandSourceStack source, CarpetRule<T> currentRule, T newValue, String string) {
            return (newValue.doubleValue() >= 0 && newValue.doubleValue() <= 1 )? newValue : null;
        }

        @Override
        public String description() {
            return "Must be between 0 and 1";
        }

    }

    /**
     * Internal validator added to every {@link RuleCategory#COMMAND} rule, resends the command tree to players.
     */
    static class CommandRule<T> extends Validator<T> {

        @Override
        public T validate(CommandSourceStack source, CarpetRule<T> currentRule, T newValue, String string) {
            if (source != null) {
                CommandHelper.notifyPlayersCommandsChanged();
            }
            return newValue;
        }

        @Override
        public String description() {
            return "It has an accompanying command";
        }

    }

    /**
     * Internal validator added to strict rules, only allows the rule suggestions.
     */
    static class Strict<T> extends Validator<T> {

        @Override
        public T validate(CommandSourceStack source, CarpetRule<T> currentRule, T newValue, String string) {
            if (!currentRule.suggestions().contains(string)) {
                if (source != null) {
                    source.getSender().sendMessage(Component.text("Valid options: " + currentRule.suggestions(), NamedTextColor.RED));
                }
                return null;
            }
            return newValue;
        }

    }

}
