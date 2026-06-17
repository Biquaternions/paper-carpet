package me.biquaternions.carpet.api.settings;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import lombok.experimental.UtilityClass;

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
     * <p>A {@link Validator} that checks whether the entered number is equal or greater than {@code 0}.</p>
     */
    public class NonNegativeNumber<T extends Number> extends Validator<T> {

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
    public class Probability<T extends Number> extends Validator<T> {

        @Override
        public T validate(CommandSourceStack source, CarpetRule<T> currentRule, T newValue, String string) {
            return (newValue.doubleValue() >= 0 && newValue.doubleValue() <= 1 )? newValue : null;
        }

        @Override
        public String description() {
            return "Must be between 0 and 1";
        }

    }

}
