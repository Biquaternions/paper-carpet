package me.biquaternions.carpet.api.settings;

import java.util.Collection;
import java.util.List;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.biquaternions.carpet.api.exception.InvalidRuleValueException;
import net.kyori.adventure.text.Component;
import org.apache.commons.lang3.ClassUtils;

/**
 * <p>A Carpet rule, that can return its required properties and stores a value.</p>
 *
 * <p>Name and description translations are picked up from the translation system.</p>
 *
 * @param <T> The value's type
 */
public interface CarpetRule<T> {

    /**
     * <p>Returns this rule's name</p>
     *
     * <p>This is also the rule's id, for changing rules in-game and for creation
     * of translation keys for the rule.</p>
     *
     * <p>This method must always return the same value for the same {@link CarpetRule}.</p>
     */
    String name();

    /**
     * <p>Returns a {@link List} of {@link Component} with extra information about this rule, that is,
     * the lines after the rule's description.</p>
     *
     * <p>Handling of translation of the result of this method is responsibility of the rule implementation.</p>
     */
    List<Component> extraInfo();

    /**
     * <p>Returns a {@link Collection} of categories this rule is on.</p>
     */
    Collection<String> categories();

    /**
     * <p>Returns a {@link Collection} of suggestions for values that this rule will be able
     * to accept as {@link String strings}. The rule must be able to accept all the suggestions in the returned {@link Collection} as a value,
     * though it may have requirements for those to be applicable.</p>
     *
     * <p>The returned collection must contain the rule's default value.</p>
     */
    Collection<String> suggestions();

    /**
     * <p>Returns this rule's value</p>
     */
    T value();

    /**
     * <p>Returns the type of this rule's value.</p>
     *
     * <p>If this rule's type is primitive, it returns a wrapped version of it (such as the result of running
     * {@link ClassUtils#primitiveToWrapper(Class)} on it) </p>
     */
    Class<T> type();

    /**
     * <p>Returns this rule's default value.</p>
     *
     * <p>This value will never be {@code null}, and will always be a valid value for {@link #set(CommandSourceStack, Object)}.</p>
     */
    T defaultValue();

    /**
     * <p>Returns whether this rule is strict.</p>
     *
     * <p>A rule being strict means that it will only accept the suggestions returned by {@link #suggestions()} as valid values.</p>
     *
     * <p>Note that a rule implementation may return {@code false} in this method but still not accept options other than those
     * returned in {@link #suggestions()}, only the opposite is guaranteed.</p>
     */
    default boolean strict() {
        return false;
    }

    /**
     * <p>Sets this rule's value to the provided {@link String}, after first converting the {@link String} into a suitable type.</p>
     *
     * <p>This methods run any required validation on the value first, and throws {@link InvalidRuleValueException} if the value is not suitable for
     * this rule, regardless of whether it was impossible to convert the value to the required type, the rule doesn't accept the value, or the rule is
     * immutable.</p>
     *
     * <p>This method must not throw any exception other than the documented {@link InvalidRuleValueException}.</p>
     *
     * @param source The {@link CommandSourceStack} to notify about the result of this rule change or {@code null} in order to not notify
     * @param value The new value for this rule as a {@link String}
     * @throws InvalidRuleValueException if the value passed to the method was not valid as a value to this rule, either because of incompatible type,
     *                                   because the rule can't accept that value or because there was some requirement missing for that value to be allowed
     */
    void set(CommandSourceStack source, String value) throws InvalidRuleValueException;

    /**
     * <p>This method follows the same contract as {@link #set(CommandSourceStack, String)}, but accepts a value already parsed (though not verified).</p>
     * @see #set(CommandSourceStack, String)
     */
    void set(CommandSourceStack source, T value) throws InvalidRuleValueException;

}
