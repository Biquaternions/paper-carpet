package me.biquaternions.carpet.api.settings;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import lombok.experimental.UtilityClass;
import me.biquaternions.carpet.api.exception.InvalidRuleValueException;

import java.util.Locale;

/**
 * <p>A helper class for operating with {@link CarpetRule} instances and values.</p>
 *
 * <p>If a method is visible and has javadocs it's probably API</p>
 */
@UtilityClass
public final class RuleHelper {

    /**
     * <p>Gets a {@code boolean} value for a given {@link CarpetRule}.</p>
     *
     * <p>The current implementation is as follows:</p>
     * <ul>
     *   <li>If the rule's type is a {@code boolean}, it will return the value directly.</li>
     *   <li>If the rule's type is a {@link Number}, it will return {@code true} if the value is greater than zero.</li>
     *   <li>In any other case, it will return {@code false}.</li>
     * </ul>
     * @param rule The rule to get the {@code boolean} value from
     * @return A {@code boolean} representation of this rule's value
     */
    public boolean getBooleanValue(CarpetRule<?> rule) {
        if (rule.type() == Boolean.class) return (boolean) rule.value();
        if (Number.class.isAssignableFrom(rule.type())) return ((Number) rule.value()).doubleValue() > 0;
        return false;
    }

    /**
     * <p>Converts a rule's value into its similar {@link String} representation.</p>
     *
     * <p>If the value is an {@link Enum}, this method returns the name of the enum constant lowercased, else
     * it returns the result of running {@link #toString()} on the value.</p>
     * @param value A rule's value
     * @return A {@link String} representation of the given value
     */
    public String toRuleString(Object value) {
        if (value instanceof Enum) return ((Enum<?>) value).name().toLowerCase(Locale.ROOT);
        return value.toString();
    }

    /**
     * <p>Checks whether the given {@code CarpetRule rule} is in its default value</p>
     *
     * @param rule The rule to check
     * @return {@code true} if the rule's default value equals its current value
     */
    public boolean isInDefaultValue(CarpetRule<?> rule) {
        return rule.defaultValue().equals(rule.value());
    }

    /**
     * <p>Resets the given {@link CarpetRule rule} to its default value, and notifies the given source, if provided.</p>
     * @param rule The {@link CarpetRule} to reset to its default value
     * @param source A {@link CommandSourceStack} to notify about this change, or {@code null}
     *
     * @param <T> The type of the {@link CarpetRule}
     */
    public <T> void resetToDefault(CarpetRule<T> rule, CommandSourceStack source) {
        try {
            rule.set(source, rule.defaultValue());
        } catch (InvalidRuleValueException e) {
            throw new IllegalStateException("Rule couldn't be set to default value!", e);
        }
    }

}
