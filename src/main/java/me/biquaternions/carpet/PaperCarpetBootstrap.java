package me.biquaternions.carpet;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.biquaternions.carpet.commands.CounterCommand;
import me.biquaternions.carpet.commands.DistanceCommand;
import me.biquaternions.carpet.commands.DrawCommand;
import org.jspecify.annotations.NonNull;

@SuppressWarnings({"UnstableApiUsage", "unused"})
class PaperCarpetBootstrap implements PluginBootstrap {

    @Override
    public void bootstrap(final @NonNull BootstrapContext context) {
        context.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands registrar = event.registrar();
            registrar.register(DrawCommand.build(), "Draw solids in your world");
            registrar.register(CounterCommand.build(), "Count items pulled by a hopper");
            registrar.register(DistanceCommand.build(), "Measure distances between locations");
        });
    }

}
