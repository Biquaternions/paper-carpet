package me.biquaternions.carpet.api.settings;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.MessageComponentSerializer;
import me.biquaternions.carpet.api.exception.InvalidRuleValueException;
import me.biquaternions.carpet.configurations.CarpetConfiguration;
import me.biquaternions.carpet.utils.CommandHelper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.jspecify.annotations.Nullable;

import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Comparator.comparing;

/**
 * <p>A {@link SettingsManager} is a class that manages {@link CarpetRule carpet rules},
 * including a command to manage it, and allowing other code to hook into rule changes by using {@link RuleObserver rule observers}.</p>
 *
 * <p>Unlike fabric-carpet, rule defaults are not stored in a {@code carpet.conf} world file but in the plugin's
 * {@code config.yml}, see {@link CarpetConfiguration}.</p>
 */
@SuppressWarnings("UnstableApiUsage")
public class SettingsManager {

    private static final List<RuleObserver> STATIC_OBSERVERS = new ArrayList<>();

    private final Map<String, CarpetRule<?>> rules = new HashMap<>();
    private final List<RuleObserver> observers = new ArrayList<>();
    private final String version;
    private final String identifier;
    private final String fancyName;
    private final Logger logger;
    private final Supplier<String> commandPermissionLevel;
    private boolean locked;

    /**
     * Defines a class that can be notified about a {@link CarpetRule} changing.
     */
    @FunctionalInterface
    public interface RuleObserver {
        /**
         * <p>Notifies this {@link RuleObserver} about the change of a {@link CarpetRule}.</p>
         *
         * <p>When this is called, the {@link CarpetRule} value has already been set.</p>
         *
         * @param source The {@link CommandSourceStack} that originated this change, {@code null} when the rule is being loaded
         * @param changedRule The {@link CarpetRule} that changed.
         * @param userInput The {@link String} that the user entered when changing the rule
         */
        void ruleChanged(@Nullable CommandSourceStack source, CarpetRule<?> changedRule, String userInput);
    }

    /**
     * Creates a new {@link SettingsManager} with the given version, identifier and fancy name
     *
     * @param version A {@link String} with the plugin's version
     * @param identifier A {@link String} with the plugin's id, will be the command name
     * @param fancyName A {@link String} being the plugin's fancy name.
     * @param logger the logger to log messages to
     * @param commandPermissionLevel supplier for the command level required to use the settings command
     */
    public SettingsManager(String version, String identifier, String fancyName, Logger logger, Supplier<String> commandPermissionLevel) {
        this.version = version;
        this.identifier = identifier;
        this.fancyName = fancyName;
        this.logger = logger;
        this.commandPermissionLevel = commandPermissionLevel;
    }

    /**
     * Registers a {@link RuleObserver} to changes in rules from this {@link SettingsManager} instance.
     */
    public void registerRuleObserver(RuleObserver observer) {
        this.observers.add(observer);
    }

    /**
     * Registers a {@link RuleObserver} to changes in rules from <b>any</b> {@link SettingsManager} instance.
     */
    public static void registerGlobalRuleObserver(RuleObserver observer) {
        STATIC_OBSERVERS.add(observer);
    }

    /**
     * @return This {@link SettingsManager}'s identifier, which is also the command name
     */
    public String identifier() {
        return this.identifier;
    }

    /**
     * <p>Returns whether this {@link SettingsManager} is locked, and any rules in it should therefore not be
     * toggleable and its management command should not be available.</p>
     */
    public boolean locked() {
        return this.locked;
    }

    /**
     * Adds all annotated fields with the {@link Rule} annotation to this {@link SettingsManager} in order to handle them.
     *
     * @param settingsClass The class that will be analyzed
     */
    public void parseSettingsClass(Class<?> settingsClass) {
        nextRule:
        for (Field field : settingsClass.getDeclaredFields()) {
            Rule annotation = field.getAnnotation(Rule.class);
            if (annotation == null) {
                continue;
            }
            for (Class<? extends Rule.Condition> condition : annotation.conditions()) {
                try {
                    Constructor<? extends Rule.Condition> constructor = condition.getDeclaredConstructor();
                    constructor.setAccessible(true);
                    if (!constructor.newInstance().shouldRegister()) {
                        continue nextRule;
                    }
                } catch (ReflectiveOperationException e) {
                    throw new IllegalArgumentException(e);
                }
            }
            CarpetRule<?> parsed = ParsedRule.of(field, this);
            this.rules.put(parsed.name(), parsed);
        }
    }

    /**
     * @return All categories that the rules in this {@link SettingsManager} have.
     */
    public Iterable<String> getCategories() {
        return this.getCarpetRules().stream().map(CarpetRule::categories).<String>mapMulti(Collection::forEach).collect(Collectors.toCollection(TreeSet::new));
    }

    /**
     * Gets a registered rule in this {@link SettingsManager}.
     *
     * @param name The name of the rule to get
     * @return A {@link CarpetRule} with the provided name or {@code null} if none in this {@link SettingsManager} matches
     */
    public @Nullable CarpetRule<?> getCarpetRule(String name) {
        return this.rules.get(name);
    }

    /**
     * @return An unmodifiable {@link Collection} of the registered rules in this {@link SettingsManager}.
     */
    public Collection<CarpetRule<?>> getCarpetRules() {
        return Collections.unmodifiableCollection(this.rules.values());
    }

    /**
     * Adds a {@link CarpetRule} to this {@link SettingsManager}.
     *
     * @throws UnsupportedOperationException If a rule with that name is already present in this {@link SettingsManager}
     */
    public void addCarpetRule(CarpetRule<?> rule) {
        if (this.rules.containsKey(rule.name())) {
            throw new UnsupportedOperationException(this.fancyName + " settings manager already contains a rule with that name!");
        }
        this.rules.put(rule.name(), rule);
    }

    public void notifyRuleChanged(@Nullable CommandSourceStack source, CarpetRule<?> rule, String userInput) {
        this.observers.forEach(observer -> observer.ruleChanged(source, rule, userInput));
        STATIC_OBSERVERS.forEach(observer -> observer.ruleChanged(source, rule, userInput));
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Persistence
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * Loads the rule values from the {@link CarpetConfiguration}. Called once the server has finished loading.
     */
    public void loadConfiguration() {
        CarpetConfiguration config = CarpetConfiguration.INSTANCE;
        for (CarpetRule<?> rule : this.rules.values()) {
            RuleHelper.resetToDefault(rule, null);
        }
        this.locked = false;
        if (config.locked) {
            this.logger.info(this.fancyName + " features are locked by the administrator");
            this.disableBooleanCommands();
        }
        int loadedCount = 0;
        // YAML may have parsed unquoted values as booleans or numbers, so don't trust the generic types
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) config.rules).entrySet()) {
            String key = String.valueOf(entry.getKey());
            CarpetRule<?> rule = this.rules.get(key);
            if (rule == null) {
                this.logger.warning("Setting " + key + " is not a valid rule - ignoring...");
                continue;
            }
            try {
                rule.set(null, String.valueOf(entry.getValue()));
                loadedCount++;
            } catch (InvalidRuleValueException e) {
                this.logger.severe("Failed to load setting " + entry.getKey() + ": " + e.getMessage());
            }
        }
        if (loadedCount > 0) {
            this.logger.info("Loaded " + loadedCount + " settings from config.yml");
        }
        this.locked = config.locked;
    }

    /**
     * Resets every rule to its default value, used when the plugin is being disabled.
     */
    public void detach() {
        for (CarpetRule<?> rule : this.rules.values()) {
            RuleHelper.resetToDefault(rule, null);
        }
    }

    private void disableBooleanCommands() {
        for (CarpetRule<?> rule : this.rules.values()) {
            if (!rule.categories().contains(RuleCategory.COMMAND)) {
                continue;
            }
            try {
                if (rule.suggestions().contains("false")) {
                    rule.set(null, "false");
                } else {
                    this.logger.warning("Couldn't disable command rule " + rule.name() + ": it doesn't suggest false as a valid option");
                }
            } catch (InvalidRuleValueException e) {
                throw new IllegalStateException(e); // contract of CarpetRule.suggestions()
            }
        }
    }

    private void saveRuleDefault(String rule, @Nullable String value) {
        if (this.locked) {
            return;
        }
        CarpetConfiguration config = CarpetConfiguration.INSTANCE;
        Map<String, String> copy = new LinkedHashMap<>(config.rules);
        if (value == null) {
            copy.remove(rule);
        } else {
            copy.put(rule, value);
        }
        config.rules = new TreeMap<>(copy);
        config.save();
    }

    private Collection<CarpetRule<?>> getRulesSorted() {
        return this.rules.values().stream().sorted(comparing(CarpetRule::name)).toList();
    }

    private Collection<CarpetRule<?>> findStartupOverrides() {
        Set<String> defaults = CarpetConfiguration.INSTANCE.rules.keySet();
        return this.rules.values().stream().filter(r -> defaults.contains(r.name())).sorted(comparing(CarpetRule::name)).toList();
    }

    private Collection<CarpetRule<?>> getNonDefault() {
        return this.rules.values().stream().filter(Predicate.not(RuleHelper::isInDefaultValue)).sorted(comparing(CarpetRule::name)).toList();
    }

    private Collection<CarpetRule<?>> getRulesMatching(String search) {
        String lcSearch = search.toLowerCase(Locale.ROOT);
        return this.rules.values().stream().filter(rule -> {
            if (rule.name().toLowerCase(Locale.ROOT).contains(lcSearch)) return true; // substring match, case insensitive
            for (String c : rule.categories()) if (c.equals(search)) return true; // category exactly, case sensitive
            return new HashSet<>(Arrays.asList(rule.description().toLowerCase(Locale.ROOT).split("\\W+"))).contains(lcSearch); // contains full term in description, but case insensitive
        }).sorted(comparing(CarpetRule::name)).toList();
    }

    /**
     * Prints in markdown the current registered rules for a category. Contains the name, description,
     * categories, type, defaults, whether or not they are strict, their suggested values, etc.
     *
     * @param ps A {@link PrintStream} to dump the rules to, such as {@link System#out}
     * @param category A {@link String} being the category to print, {@code null} to print all registered rules.
     */
    public int dumpAllRulesToStream(PrintStream ps, @Nullable String category) {
        ps.println("# " + this.fancyName + " Settings");
        for (CarpetRule<?> rule : new TreeMap<>(this.rules).values()) {
            if (category != null && !rule.categories().contains(category)) {
                continue;
            }
            ps.println("## " + rule.name());
            ps.println(rule.description() + "  ");
            for (Component extra : rule.extraInfo()) {
                ps.println(((TextComponent) extra).content() + "  ");
            }
            ps.println("* Type: `" + rule.type().getSimpleName() + "`  ");
            ps.println("* Default value: `" + RuleHelper.toRuleString(rule.defaultValue()) + "`  ");
            String options = rule.suggestions().stream().map(s -> "`" + s + "`").collect(Collectors.joining(", "));
            if (!options.isEmpty()) {
                ps.println((rule.strict() ? "* Allowed" : "* Suggested") + " options: " + options + "  ");
            }
            ps.println("* Categories: " + rule.categories().stream().map(s -> "`" + s.toUpperCase(Locale.ROOT) + "`").collect(Collectors.joining(", ")) + "  ");
            if (rule instanceof ParsedRule<?> parsed) {
                boolean preamble = false;
                for (Validator<?> validator : parsed.validators) {
                    if (validator.description() != null) {
                        if (!preamble) {
                            ps.println("* Additional notes:  ");
                            preamble = true;
                        }
                        ps.println("  * " + validator.description() + "  ");
                    }
                }
            }
            ps.println("  ");
        }
        return 1;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Command
    // ---------------------------------------------------------------------------------------------------------------

    private CarpetRule<?> contextRule(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String ruleName = StringArgumentType.getString(ctx, "rule");
        CarpetRule<?> rule = this.getCarpetRule(ruleName);
        if (rule == null) {
            throw new SimpleCommandExceptionType(MessageComponentSerializer.message().serialize(
                    Component.text("Unknown rule: " + ruleName, NamedTextColor.RED, TextDecoration.BOLD))).create();
        }
        return rule;
    }

    private static CompletableFuture<Suggestions> suggest(Iterable<String> values, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                builder.suggest(value);
            }
        }
        return builder.buildFuture();
    }

    static CompletableFuture<Suggestions> suggestMatchingContains(Stream<String> stream, SuggestionsBuilder suggestionsBuilder) {
        List<String> regularSuggestionList = new ArrayList<>();
        List<String> smartSuggestionList = new ArrayList<>();
        String query = suggestionsBuilder.getRemaining().toLowerCase(Locale.ROOT);
        stream.forEach((listItem) -> {
            // Regex camelCase Search
            List<String> words = Arrays.stream(listItem.split("(?<!^)(?=[A-Z])")).map(s -> s.toLowerCase(Locale.ROOT)).toList();
            List<String> prefixes = new ArrayList<>(words.size());
            for (int i = 0; i < words.size(); i++) {
                prefixes.add(String.join("", words.subList(i, words.size())));
            }
            if (prefixes.stream().anyMatch(s -> s.startsWith(query))) {
                smartSuggestionList.add(listItem);
            }
            // Regular prefix matching
            if (listItem.toLowerCase(Locale.ROOT).startsWith(query) || listItem.toLowerCase(Locale.ROOT).contains("_" + query)) {
                regularSuggestionList.add(listItem);
            }
        });
        List<String> filteredSuggestionList = regularSuggestionList.isEmpty() ? smartSuggestionList : regularSuggestionList;
        filteredSuggestionList.forEach(suggestionsBuilder::suggest);
        return suggestionsBuilder.buildFuture();
    }

    /**
     * Builds the settings command for this {@link SettingsManager}.
     */
    public LiteralCommandNode<CommandSourceStack> buildCommand() {
        Predicate<CommandSourceStack> permission = CommandHelper.commandPermission(this.commandPermissionLevel, this.identifier + ".command");
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal(this.identifier)
                .requires(s -> !this.locked() && permission.test(s));

        builder.executes(c -> this.listAllSettings(c.getSource()))
                .then(Commands.literal("list")
                        .executes(c -> this.listSettings(c.getSource(), String.format("All %s Settings", this.fancyName), this.getRulesSorted()))
                        .then(Commands.literal("defaults")
                                .executes(c -> this.listSettings(c.getSource(),
                                        String.format("Current %s Startup Settings from %s", this.fancyName, "config.yml"),
                                        this.findStartupOverrides())))
                        .then(Commands.argument("tag", StringArgumentType.word())
                                .suggests((c, b) -> suggest(this.getCategories(), b))
                                .executes(c -> this.listSettings(c.getSource(),
                                        String.format("%s Settings matching '%s'", this.fancyName, StringArgumentType.getString(c, "tag")),
                                        this.getRulesMatching(StringArgumentType.getString(c, "tag"))))))
                .then(Commands.literal("removeDefault")
                        .then(Commands.argument("rule", StringArgumentType.word())
                                .suggests((c, b) -> suggestMatchingContains(this.getRulesSorted().stream().map(CarpetRule::name), b))
                                .executes(c -> this.removeDefault(c.getSource(), this.contextRule(c)))))
                .then(Commands.literal("setDefault")
                        .then(Commands.argument("rule", StringArgumentType.word())
                                .suggests((c, b) -> suggestMatchingContains(this.getRulesSorted().stream().map(CarpetRule::name), b))
                                .then(Commands.argument("value", StringArgumentType.greedyString())
                                        .suggests((c, b) -> suggest(this.contextRule(c).suggestions(), b))
                                        .executes(c -> this.setDefault(c.getSource(), this.contextRule(c), StringArgumentType.getString(c, "value"))))))
                .then(Commands.argument("rule", StringArgumentType.word())
                        .suggests((c, b) -> suggestMatchingContains(this.getRulesSorted().stream().map(CarpetRule::name), b))
                        .executes(c -> this.displayRuleMenu(c.getSource(), this.contextRule(c)))
                        .then(Commands.argument("value", StringArgumentType.greedyString())
                                .suggests((c, b) -> suggest(this.contextRule(c).suggestions(), b))
                                .executes(c -> this.setRule(c.getSource(), this.contextRule(c), StringArgumentType.getString(c, "value")))));

        return builder.build();
    }

    private static Component hover(Component component, Component hover) {
        return component.hoverEvent(HoverEvent.showText(hover));
    }

    private static Component run(Component component, String command) {
        return component.clickEvent(ClickEvent.runCommand(command));
    }

    private static Component suggestCommand(Component component, String command) {
        return component.clickEvent(ClickEvent.suggestCommand(command));
    }

    private int displayRuleMenu(CommandSourceStack source, CarpetRule<?> rule) {
        CommandSender sender = source.getSender();
        sender.sendMessage(Component.empty());
        sender.sendMessage(run(hover(Component.text(rule.name(), NamedTextColor.WHITE, TextDecoration.BOLD),
                Component.text("refresh", NamedTextColor.GRAY)), "/" + this.identifier + " " + rule.name()));
        sender.sendMessage(Component.text(rule.description(), NamedTextColor.WHITE));
        rule.extraInfo().forEach(sender::sendMessage);

        Component tags = Component.text("Tags: ", NamedTextColor.WHITE);
        boolean first = true;
        for (String category : rule.categories()) {
            if (!first) {
                tags = tags.append(Component.text(", ", NamedTextColor.WHITE));
            }
            first = false;
            tags = tags.append(run(hover(Component.text("[" + category + "]", NamedTextColor.DARK_AQUA),
                    Component.text("list all " + category + " settings", NamedTextColor.GRAY)), "/" + this.identifier + " list " + category));
        }
        sender.sendMessage(tags);

        Style valueStyle = Style.style(RuleHelper.getBooleanValue(rule) ? NamedTextColor.GREEN : NamedTextColor.DARK_RED, TextDecoration.BOLD);
        sender.sendMessage(Component.text("Current value: ", NamedTextColor.WHITE)
                .append(Component.text(String.format("%s (%s value)", RuleHelper.toRuleString(rule.value()), RuleHelper.isInDefaultValue(rule) ? "default" : "modified"), valueStyle)));

        Component options = Component.text("Options: ", NamedTextColor.WHITE).append(Component.text("[ ", NamedTextColor.YELLOW));
        first = true;
        for (String option : rule.suggestions()) {
            if (!first) {
                options = options.appendSpace();
            }
            first = false;
            options = options.append(this.makeSetRuleButton(rule, option, false));
        }
        options = options.append(Component.text(" ]", NamedTextColor.YELLOW));
        sender.sendMessage(options);
        return Command.SINGLE_SUCCESS;
    }

    private int setRule(CommandSourceStack source, CarpetRule<?> rule, String newValue) {
        try {
            rule.set(source, newValue);
            source.getSender().sendMessage(Component.text(rule + ", ", NamedTextColor.WHITE)
                    .append(suggestCommand(hover(Component.text("[change permanently?]", NamedTextColor.DARK_AQUA),
                                    Component.text("Click to keep the settings in config.yml to save across restarts", NamedTextColor.WHITE)),
                            "/" + this.identifier + " setDefault " + rule.name() + " " + RuleHelper.toRuleString(rule.value()))));
        } catch (InvalidRuleValueException e) {
            e.notifySource(rule.name(), source);
        }
        return Command.SINGLE_SUCCESS;
    }

    // stores different defaults in the file
    private int setDefault(CommandSourceStack source, CarpetRule<?> rule, String stringValue) {
        if (this.locked()) {
            return 0;
        }
        try {
            rule.set(source, stringValue);
            source.getSender().sendMessage(Component.text(String.format("Rule %s will now default to %s", rule.name(), stringValue), NamedTextColor.GRAY, TextDecoration.ITALIC));
        } catch (InvalidRuleValueException e) {
            e.notifySource(rule.name(), source);
            return 0;
        }
        this.saveRuleDefault(rule.name(), stringValue);
        return Command.SINGLE_SUCCESS;
    }

    // removes overrides of the default values in the file
    private int removeDefault(CommandSourceStack source, CarpetRule<?> rule) {
        if (this.locked()) {
            return 0;
        }
        this.saveRuleDefault(rule.name(), null);
        RuleHelper.resetToDefault(rule, source);
        source.getSender().sendMessage(Component.text(String.format("Rule %s defaults to Vanilla", rule.name()), NamedTextColor.GRAY, TextDecoration.ITALIC));
        return Command.SINGLE_SUCCESS;
    }

    private Component displayInteractiveSetting(CarpetRule<?> rule) {
        Component line = run(hover(Component.text("- " + rule.name() + " ", NamedTextColor.WHITE),
                Component.text(rule.description(), NamedTextColor.YELLOW)), "/" + this.identifier + " " + rule.name());
        List<String> options = new ArrayList<>(rule.suggestions());
        String current = RuleHelper.toRuleString(rule.value());
        if (!options.contains(current)) {
            options.add(current);
        }
        boolean first = true;
        for (String option : options) {
            if (!first) {
                line = line.appendSpace();
            }
            first = false;
            line = line.append(this.makeSetRuleButton(rule, option, true));
        }
        return line;
    }

    private Component makeSetRuleButton(CarpetRule<?> rule, String option, boolean brackets) {
        String current = RuleHelper.toRuleString(rule.value());
        String defaultValue = RuleHelper.toRuleString(rule.defaultValue());
        NamedTextColor color = RuleHelper.isInDefaultValue(rule) ? NamedTextColor.GRAY : (option.equalsIgnoreCase(defaultValue) ? NamedTextColor.DARK_GRAY : NamedTextColor.YELLOW);
        Style.Builder style = Style.style().color(color);
        boolean isCurrent = option.equalsIgnoreCase(current);
        if (isCurrent) {
            style.decorate(TextDecoration.UNDERLINED);
            if (option.equalsIgnoreCase(defaultValue)) {
                style.decorate(TextDecoration.BOLD);
            }
        }
        Component component = Component.text(brackets ? "[" + option + "]" : option, style.build());
        if (isCurrent) {
            return component;
        }
        return suggestCommand(hover(component, Component.text("Switch to " + option + (option.equals(defaultValue) ? " (default)" : ""), NamedTextColor.GRAY)),
                "/" + this.identifier + " " + rule.name() + " " + option);
    }

    private int listSettings(CommandSourceStack source, String title, Collection<CarpetRule<?>> settings) {
        source.getSender().sendMessage(Component.text(title + ":", NamedTextColor.WHITE, TextDecoration.BOLD));
        settings.forEach(rule -> source.getSender().sendMessage(this.displayInteractiveSetting(rule)));
        return settings.size();
    }

    private int listAllSettings(CommandSourceStack source) {
        int count = this.listSettings(source, String.format("Current %s Settings", this.fancyName), this.getNonDefault());

        if (this.version != null) {
            source.getSender().sendMessage(Component.text(this.fancyName + " version: " + this.version, NamedTextColor.GRAY));
        }

        Component tags = Component.text("Browse Categories:", NamedTextColor.WHITE).appendNewline();
        boolean first = true;
        for (String category : this.getCategories()) {
            if (!first) {
                tags = tags.appendSpace();
            }
            first = false;
            tags = tags.append(run(hover(Component.text("[" + category + "]", NamedTextColor.DARK_AQUA),
                    Component.text("list all " + category + " settings", NamedTextColor.GRAY)), "/" + this.identifier + " list " + category));
        }
        source.getSender().sendMessage(tags);
        return count;
    }

}
