package me.biquaternions.carpet.api.settings;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.biquaternions.carpet.api.exception.InvalidRuleValueException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.apache.commons.lang3.ClassUtils;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A Carpet rule parsed from a static field annotated with {@link Rule}.
 *
 * @param <T> The field's (and rule's) type
 */
final class ParsedRule<T> implements CarpetRule<T> {

    @FunctionalInterface
    interface FromStringConverter<T> {
        T convert(String value) throws InvalidRuleValueException;
    }

    private static final Map<Class<?>, FromStringConverter<?>> CONVERTER_MAP = Map.ofEntries(
            Map.entry(String.class, str -> str),
            Map.entry(Boolean.class, str -> switch (str) {
                case "true" -> true;
                case "false" -> false;
                default -> throw new InvalidRuleValueException("Invalid boolean value");
            }),
            numericalConverter(Integer.class, Integer::parseInt),
            numericalConverter(Double.class, Double::parseDouble),
            numericalConverter(Long.class, Long::parseLong),
            numericalConverter(Float.class, Float::parseFloat)
    );

    private final Field field;
    private final String name;
    private final String description;
    private final List<String> extraInfo;
    private final List<String> categories;
    private final List<String> options;
    private final Class<T> type;
    private final T defaultValue;
    private final SettingsManager settingsManager;
    final List<Validator<T>> validators;
    private final FromStringConverter<T> converter;

    static <T> ParsedRule<T> of(Field field, SettingsManager settingsManager) {
        return new ParsedRule<>(field, field.getAnnotation(Rule.class), settingsManager);
    }

    @SuppressWarnings("unchecked")
    private ParsedRule(Field field, Rule rule, SettingsManager settingsManager) {
        if (!Modifier.isStatic(field.getModifiers())) {
            throw new IllegalArgumentException("Rule field " + field.getName() + " must be static");
        }
        field.setAccessible(true);
        this.field = field;
        this.name = field.getName();
        this.type = (Class<T>) ClassUtils.primitiveToWrapper(field.getType());
        this.description = rule.desc();
        this.extraInfo = List.of(rule.extra());
        this.categories = List.of(rule.categories());
        this.settingsManager = settingsManager;
        this.validators = Stream.of(rule.validators()).map(cls -> (Validator<T>) instantiateValidator(cls)).collect(Collectors.toCollection(ArrayList::new));
        this.defaultValue = value();

        FromStringConverter<T> converter0 = null;
        if (this.categories.contains(RuleCategory.COMMAND)) {
            this.validators.add(new Validators.CommandRule<>());
            if (this.type == String.class) {
                this.validators.add((Validator<T>) instantiateValidator(Validators.CommandLevel.class));
            }
        }

        if (rule.options().length > 0) {
            this.options = List.of(rule.options());
        } else if (this.type == Boolean.class) {
            this.options = List.of("true", "false");
        } else if (this.type == String.class && this.categories.contains(RuleCategory.COMMAND)) {
            this.options = Validators.CommandLevel.OPTIONS;
        } else if (this.type.isEnum()) {
            this.options = Arrays.stream(this.type.getEnumConstants()).map(e -> ((Enum<?>) e).name().toLowerCase(Locale.ROOT)).toList();
            converter0 = str -> {
                try {
                    @SuppressWarnings({"rawtypes"})
                    T ret = (T) Enum.valueOf((Class<? extends Enum>) this.type, str.toUpperCase(Locale.ROOT));
                    return ret;
                } catch (IllegalArgumentException e) {
                    throw new InvalidRuleValueException("Valid values for this rule are: " + this.options);
                }
            };
        } else {
            this.options = List.of();
        }

        if (rule.strict() && !this.options.isEmpty()) {
            this.validators.addFirst(new Validators.Strict<>()); // at 0 prevents validators with side effects from running when invalid
        }

        if (converter0 == null) {
            converter0 = (FromStringConverter<T>) CONVERTER_MAP.get(this.type);
            if (converter0 == null) {
                throw new UnsupportedOperationException("Unsupported type for ParsedRule " + this.type);
            }
        }
        this.converter = converter0;
    }

    @SuppressWarnings("rawtypes")
    private static Validator<?> instantiateValidator(Class<? extends Validator> cls) {
        try {
            Constructor<? extends Validator> constructor = cls.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public void set(@Nullable CommandSourceStack source, String value) throws InvalidRuleValueException {
        this.set(source, this.converter.convert(value), value);
    }

    @Override
    public void set(@Nullable CommandSourceStack source, T value) throws InvalidRuleValueException {
        this.set(source, value, RuleHelper.toRuleString(value));
    }

    private void set(@Nullable CommandSourceStack source, T value, String userInput) throws InvalidRuleValueException {
        for (Validator<T> validator : this.validators) {
            value = validator.validate(source, this, value, userInput);
            if (value == null) {
                if (source != null) {
                    validator.notifyFailure(source, this, userInput);
                }
                throw new InvalidRuleValueException();
            }
        }
        if (!value.equals(this.value()) || source == null) {
            try {
                this.field.set(null, value);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Couldn't access field for rule: " + this.name, e);
            }
            this.settingsManager.notifyRuleChanged(source, this, userInput);
        }
    }

    @Override
    public String name() {
        return this.name;
    }

    @Override
    public String description() {
        return this.description;
    }

    @Override
    public List<Component> extraInfo() {
        return this.extraInfo.stream().map(str -> (Component) Component.text(str, NamedTextColor.GRAY)).toList();
    }

    @Override
    public Collection<String> categories() {
        return this.categories;
    }

    @Override
    public Collection<String> suggestions() {
        return this.options;
    }

    @Override
    public SettingsManager settingsManager() {
        return this.settingsManager;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T value() {
        try {
            return (T) this.field.get(null);
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException("Couldn't access field for rule: " + this.name, e);
        }
    }

    @Override
    public Class<T> type() {
        return this.type;
    }

    @Override
    public T defaultValue() {
        return this.defaultValue;
    }

    @Override
    public boolean strict() {
        return !this.validators.isEmpty() && this.validators.getFirst() instanceof Validators.Strict;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ParsedRule<?> other && other.name.equals(this.name);
    }

    @Override
    public int hashCode() {
        return this.name.hashCode();
    }

    @Override
    public String toString() {
        return this.name + ": " + RuleHelper.toRuleString(this.value());
    }

    private static <T> Map.Entry<Class<T>, FromStringConverter<T>> numericalConverter(Class<T> outputClass, Function<String, T> converter) {
        return Map.entry(outputClass, str -> {
            try {
                return converter.apply(str);
            } catch (NumberFormatException e) {
                throw new InvalidRuleValueException("Invalid number for rule");
            }
        });
    }

}
