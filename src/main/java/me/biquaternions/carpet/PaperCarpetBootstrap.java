package me.biquaternions.carpet;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.biquaternions.carpet.commands.DrawCommand;
import net.j4c0b3y.api.config.ConfigHandler;
import net.j4c0b3y.api.config.platform.adventure.AdventureConfigHandler;
import org.jspecify.annotations.NonNull;

@SuppressWarnings({"UnstableApiUsage", "unused"})
class PaperCarpetBootstrap implements PluginBootstrap {

    @Override
    public void bootstrap(final @NonNull BootstrapContext context) {
        context.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands registrar = event.registrar();
            registrar.register(DrawCommand.build(), "Draw solids in your world");
        });
    }

}
