package me.biquaternions.carpet.api.settings;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;

/**
 * <p>A {@link SettingsManager} is a class that manages {@link CarpetRule carpet rules} in a {@link MinecraftServer},
 * including a command to manage it, and allowing other code to hook into rule changes by using {@link RuleObserver rule observers}.</p>
 *
 * <p>You can create your own {@link SettingsManager} if you want to have your own command that will be handled the same way as Carpet's
 * by using {@link #SettingsManager(String, String, String)} and returning it in your {@link CarpetExtension}'s
 * {@link CarpetExtension#extensionSettingsManager()} method.</p>
 */
@SuppressWarnings({"deprecation", "removal"}) // remove after removing old system
public class SettingsManager {
    private final Map<String, CarpetRule<?>> rules = new HashMap<>();
    private final String version;
    private final String identifier;
    private final String fancyName;
    private boolean locked;
    private MinecraftServer server;
    private final List<RuleObserver> observers = new ArrayList<>();
    private static final List<RuleObserver> staticObservers = new ArrayList<>();
    static record ConfigReadResult(Map<String, String> ruleMap, boolean locked) {}

    /**
     * <p>Defines a class that can be notified about a {@link CarpetRule} changing.</p>
     *
     * @see #ruleChanged(CommandSourceStack, CarpetRule, String)
     * @see SettingsManager#registerRuleObserver(RuleObserver)
     * @see SettingsManager#registerGlobalRuleObserver(RuleObserver)
     */
    @FunctionalInterface
    public static interface RuleObserver {
        /**
         * <p>Notifies this {@link RuleObserver} about the change of a {@link CarpetRule}.</p>
         *
         * <p>When this is called, the {@link CarpetRule} value has already been set.</p>
         *
         * @param source The {@link CommandSourceStack} that likely originated this change, and should be the notified source for further
         *               messages. Can be {@code null} if there was none and the operation shouldn't send feedback.
         * @param changedRule The {@link CarpetRule} that changed. Use {@link CarpetRule#value() changedRule.value()} to get the rule's value,
         *                    and pass it to {@link RuleHelper#toRuleString(Object)} to get the {@link String} version of it
         * @param userInput The {@link String} that the user entered when changing the rule, or a best-effort representation of it in case that is
         *                  is not available at the time (such as loading from disk or a rule being changed programmatically). Note that this value
         *                  may not represent the same string as converting the current value to a {@link String} via {@link RuleHelper#toRuleString(Object)},
         *                  given the rule implementation may have adapted the input into a different value, for example with the use of a {@link Validator}
         */
        void ruleChanged(CommandSourceStack source, CarpetRule<?> changedRule, String userInput);
    }

    /**
     * Creates a new {@link SettingsManager} with the given version, identifier and fancy name
     *
     * @param version A {@link String} with the mod's version
     * @param identifier A {@link String} with the mod's id, will be the command name
     * @param fancyName A {@link String} being the mod's fancy name.
     */
    public SettingsManager(String version, String identifier, String fancyName) {
        this.version = version;
        this.identifier = identifier;
        this.fancyName = fancyName;
    }

    /**
     * <p>Registers a {@link RuleObserver} to changes in rules from
     * this {@link SettingsManager} instance.</p>
     *
     * @see SettingsManager#registerGlobalRuleObserver(RuleObserver)
     *
     * @param observer A {@link RuleObserver} that will be called with
     *                 the used {@link CommandSourceStack} and the changed
     *                 {@link CarpetRule}.
     */
    public void registerRuleObserver(RuleObserver observer) {
        observers.add(observer);
    }

    /**
     * Registers a {@link RuleObserver} to changes in rules from
     * <b>any</b> {@link SettingsManager} instance (unless their implementation disallows it).
     * @see SettingsManager#registerRuleObserver(RuleObserver)
     *
     * @param observer A {@link RuleObserver} that will be called with
     *                 the used {@link CommandSourceStack}, and the changed
     *                 {@link CarpetRule}.
     */
    public static void registerGlobalRuleObserver(RuleObserver observer) {
        staticObservers.add(observer);
    }

    /**
     * @return A {@link String} being this {@link SettingsManager}'s
     *         identifier, which is also the command name
     */
    public String identifier() {
        return identifier;
    }

    /**
     * <p>Returns whether this {@link SettingsManager} is locked, and any rules in it should therefore not be
     * toggleable and its management command should not be available.</p>
     * @return {@code true} if this {@link SettingsManager} is locked
     */
    public boolean locked() {
        return locked;
    }

    /**
     * Adds all annotated fields with the {@link Rule} annotation
     * to this {@link SettingsManager} in order to handle them.
     * @param settingsClass The class that will be analyzed
     */
    public void parseSettingsClass(Class<?> settingsClass) {
        nextRule: for (Field field : settingsClass.getDeclaredFields()) {
            Rule newAnnotation = field.getAnnotation(Rule.class);
            Class<? extends Rule.Condition>[] conditions = newAnnotation.conditions();
            for (Class<? extends Rule.Condition> condition : conditions) { //Should this be moved to ParsedRule.of?
                try
                {
                    Constructor<? extends Rule.Condition> constr = condition.getDeclaredConstructor();
                    constr.setAccessible(true);
                    if (!(constr.newInstance()).shouldRegister())
                        continue nextRule;
                }
                catch (ReflectiveOperationException e)
                {
                    throw new IllegalArgumentException(e);
                }
            }
            // FIXME: CarpetRule<?> parsed = ParsedRule.of(field, this);
            //  rules.put(parsed.name(), parsed);
        }
    }

    /**
     * @return A String {@link Iterable} with all categories
     *         that the rules in this {@link SettingsManager} have.
     * @implNote This method doesn't cache the result, so each call loops through all rules and finds all present categories
     */
    public Iterable<String> getCategories()
    {
        return getCarpetRules().stream().map(CarpetRule::categories).<String>mapMulti(Collection::forEach).collect(Collectors.toSet());
    }

    /**
     * <p>Gets a registered rule in this {@link SettingsManager}.</p>
     *
     * @param name The name of the rule to get
     * @return A {@link CarpetRule} with the provided name or {@code null} if none in this {@link SettingsManager} matches
     */
    public CarpetRule<?> getCarpetRule(String name)
    {
        return rules.get(name);
    }

    /**
     * @return An unmodifiable {@link Collection} of the registered rules in this {@link SettingsManager}.
     */
    public Collection<CarpetRule<?>> getCarpetRules()
    {
        return Collections.unmodifiableCollection(rules.values());
    }

    /**
     * <p>Adds a {@link CarpetRule} to this {@link SettingsManager}.</p>
     *
     * <p>Useful when having different {@link CarpetRule} implementations instead of a class of {@code static},
     * annotated fields.</p>
     *
     * @param rule The {@link CarpetRule} to add
     * @throws UnsupportedOperationException If a rule with that name is already present in this {@link SettingsManager}
     */
    public void addCarpetRule(CarpetRule<?> rule) {
        if (rules.containsKey(rule.name()))
            throw new UnsupportedOperationException(fancyName + " settings manager already contains a rule with that name!");
        rules.put(rule.name(), rule);
    }

    public void notifyRuleChanged(CommandSourceStack source, CarpetRule<?> rule, String userInput)
    {
        observers.forEach(observer -> observer.ruleChanged(source, rule, userInput));
        staticObservers.forEach(observer -> observer.ruleChanged(source, rule, userInput));
        // FIXME: ServerNetworkHandler.updateRuleWithConnectedClients(rule);
        //  switchScarpetRuleIfNeeded(source, rule); //TODO move into rule
    }


}
