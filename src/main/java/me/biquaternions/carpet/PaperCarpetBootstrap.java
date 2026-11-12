package me.biquaternions.carpet;

import de.bsommerfeld.jshepherd.core.PersistenceDelegateFactoryRegistry;
import de.bsommerfeld.jshepherd.yaml.YamlPersistenceDelegateFactory;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.bootstrap.PluginProviderContext;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.biquaternions.carpet.commands.CounterCommand;
import me.biquaternions.carpet.commands.DistanceCommand;
import me.biquaternions.carpet.commands.DrawCommand;
import me.biquaternions.carpet.commands.InfoCommand;
import me.biquaternions.carpet.commands.LogCommand;
import me.biquaternions.carpet.commands.PerimeterInfoCommand;
import me.biquaternions.carpet.commands.PlayerCommand;
import me.biquaternions.carpet.commands.ProfileCommand;
import me.biquaternions.carpet.commands.SpawnCommand;
import me.biquaternions.carpet.commands.TestCommand;
import me.biquaternions.carpet.commands.TrackCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NonNull;

@SuppressWarnings({"UnstableApiUsage", "unused"})
class PaperCarpetBootstrap implements PluginBootstrap {

    @Override
    public void bootstrap(final @NonNull BootstrapContext context) {
        context.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands registrar = event.registrar();
            registrar.register(CarpetServer.settingsManager.buildCommand(), "Manage the Carpet rules");
            registrar.register(CounterCommand.build(), "Count items pulled by a hopper");
            registrar.register(DistanceCommand.build(), "Measure distances between locations");
            registrar.register(DrawCommand.build(), "Draw solids in your world");
            registrar.register(InfoCommand.build(), "Get information about blocks");
            registrar.register(LogCommand.build(), "Subscribe to loggers");
            registrar.register(PerimeterInfoCommand.build(), "Scan the area around a block for potential spawning spots");
            registrar.register(ProfileCommand.build(), "Monitor the server performance");
            registrar.register(SpawnCommand.build(), "Track mob spawning");
            registrar.register(TrackCommand.build(), "Track mobs AI");
            registrar.register(PlayerCommand.build(), "Spawn and control players");
            if (TestCommand.shouldRegister()) {
                registrar.register(TestCommand.build(), "Carpet development command");
            }
        });
    }

    @Override
    public JavaPlugin createPlugin(PluginProviderContext context) {
        PersistenceDelegateFactoryRegistry.registerFactory(new YamlPersistenceDelegateFactory());
        return PluginBootstrap.super.createPlugin(context);
    }
}
