package me.biquaternions.carpet.script.external;

import me.biquaternions.carpet.CarpetServer;
import me.biquaternions.carpet.CarpetSettings;
import me.biquaternions.carpet.PaperCarpet;
import me.biquaternions.carpet.api.settings.CarpetRule;
import me.biquaternions.carpet.api.settings.RuleHelper;
import me.biquaternions.carpet.api.settings.SettingsManager;
import me.biquaternions.carpet.fakes.FakePlayer;
import me.biquaternions.carpet.logging.HUDController;
import me.biquaternions.carpet.network.ServerNetworkHandler;
import me.biquaternions.carpet.script.CarpetEventServer;
import me.biquaternions.carpet.script.CarpetExpression;
import me.biquaternions.carpet.script.CarpetScriptHost;
import me.biquaternions.carpet.script.CarpetScriptServer;
import me.biquaternions.carpet.script.Module;
import me.biquaternions.carpet.script.exception.LoadException;
import me.biquaternions.carpet.script.utils.ScarpetMessenger;
import me.biquaternions.carpet.script.value.MapValue;
import me.biquaternions.carpet.script.value.StringValue;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.bukkit.Bukkit;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Bridge between scarpet and the rest of Paper Carpet.
 */
public class Carpet {

    public static Map<String, Component> getScarpetHeaders() {
        return HUDController.SCARPET_HEADERS;
    }

    public static Map<String, Component> getScarpetFooters() {
        return HUDController.SCARPET_FOOTERS;
    }

    public static void updateScarpetHUDs(MinecraftServer server, List<ServerPlayer> players) {
        HUDController.updateHud(server, players.stream().map(ServerPlayer::getBukkitEntity).map(p -> (org.bukkit.entity.Player) p).toList());
    }

    public static Component Messenger_compose(Object... messages) {
        return ScarpetMessenger.c(messages);
    }

    public static void Messenger_message(CommandSourceStack source, Object... messages) {
        ScarpetMessenger.m(source, messages);
    }

    public static ThreadLocal<Boolean> getImpendingFillSkipUpdates() {
        return CarpetSettings.impendingFillSkipUpdates;
    }

    /**
     * Paper Carpet's profiler doesn't measure sections, see {@link me.biquaternions.carpet.utils.CarpetProfiler}.
     */
    public static Runnable startProfilerSection(String name) {
        return () -> {
        };
    }

    public static void MinecraftServer_addScriptServer(MinecraftServer server, CarpetScriptServer scriptServer) {
        Vanilla.MinecraftServer_setScriptServer(scriptServer);
    }

    private static @Nullable ServerNetworkHandler networkHandler() {
        PaperCarpet plugin = PaperCarpet.getInstance();
        return plugin == null ? null : plugin.getNetworkHandler();
    }

    public static boolean isValidCarpetPlayer(ServerPlayer player) {
        ServerNetworkHandler handler = networkHandler();
        return !CarpetSettings.superSecretSetting && handler != null && handler.isCarpetPlayer(player.getBukkitEntity());
    }

    public static String getPlayerStatus(ServerPlayer player) {
        ServerNetworkHandler handler = networkHandler();
        String version = handler == null ? null : handler.getCarpetVersion(player.getBukkitEntity());
        return version == null ? "vanilla" : "carpet " + version;
    }

    public static MapValue getAllCarpetRules() {
        Collection<CarpetRule<?>> rules = CarpetServer.settingsManager.getCarpetRules();
        MapValue carpetRules = new MapValue(Collections.emptyList());
        rules.forEach(rule -> carpetRules.put(new StringValue(rule.name()), new StringValue(RuleHelper.toRuleString(rule.value()))));
        return carpetRules;
    }

    public static String getCarpetVersion() {
        return CarpetServer.VERSION;
    }

    public static @Nullable String isModdedPlayer(Player p) {
        if (p instanceof final FakePlayer fake) {
            return fake.isAShadow ? "shadow" : "fake";
        }
        return null;
    }

    /**
     * There are no carpet extensions on Paper.
     */
    public static void handleExtensionsAPI(CarpetExpression expression) {
    }

    public static boolean getFillUpdates() {
        return CarpetSettings.fillUpdates;
    }

    public static boolean isDebugEnabled() {
        return CarpetSettings.superSecretSetting;
    }

    /**
     * Global modules live in the client's config folder in fabric-carpet, there's no such thing on a server.
     */
    public static @Nullable Module fetchGlobalModule(String name, boolean allowLibraries) throws IOException {
        return null;
    }

    public static void addGlobalModules(final List<String> moduleNames, boolean includeBuiltIns) throws IOException {
    }

    /**
     * Mods are replaced by plugins. Versions can't be checked against fabric's version predicates, so only the presence
     * of the plugin is required. {@code minecraft} and {@code carpet} are always present.
     */
    public static void assertRequirementMet(CarpetScriptHost host, String requiredModId, String stringPredicate) {
        if (requiredModId.equals("minecraft") || requiredModId.equals("carpet") || requiredModId.equals("java")) {
            return;
        }
        boolean present = Arrays.stream(Bukkit.getPluginManager().getPlugins()).anyMatch(p -> p.getName().equalsIgnoreCase(requiredModId));
        if (!present) {
            throw new LoadException(String.format("%s requires a version of plugin '%s' matching '%s', which is missing!", host.getVisualName(), requiredModId, stringPredicate));
        }
    }

    // to be ran once during CarpetEventServer.Event static init
    public static void initCarpetEvents() {
        CarpetEventServer.Event carpetRuleChanges = new CarpetEventServer.Event("carpet_rule_changes", 2, true) {
            @Override
            public void handleAny(final Object... args) {
                CarpetRule<?> rule = (CarpetRule<?>) args[0];
                CommandSourceStack source = (CommandSourceStack) args[1];
                String id = rule.settingsManager().identifier();
                String namespace = id.equals("carpet") ? "" : id + ":";
                handler.call(() -> Arrays.asList(
                        new StringValue(namespace + rule.name()),
                        new StringValue(RuleHelper.toRuleString(rule.value()))
                ), () -> source);
            }
        };
        SettingsManager.registerGlobalRuleObserver((source, changedRule, userInput) -> {
            CommandSourceStack nms = source instanceof CommandSourceStack css ? css : MinecraftServer.getServer().createCommandSourceStack();
            carpetRuleChanges.handleAny(changedRule, nms);
        });
    }

}
