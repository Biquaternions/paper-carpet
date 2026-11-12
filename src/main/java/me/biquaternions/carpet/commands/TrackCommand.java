package me.biquaternions.carpet.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.MessageComponentSerializer;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.registry.RegistryKey;
import lombok.experimental.UtilityClass;
import me.biquaternions.carpet.CarpetSettings;
import me.biquaternions.carpet.utils.CommandHelper;
import me.biquaternions.carpet.utils.MobAI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.EntityType;
import org.jspecify.annotations.NullMarked;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Port of fabric-carpet's {@code MobAICommand}, registered as {@code /track}.
 */
@NullMarked
@UtilityClass
@SuppressWarnings("UnstableApiUsage")
public class TrackCommand {

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("track")
                .requires(CommandHelper.commandPermission(() -> CarpetSettings.commandTrackAI, "carpet.track"))
                .then(Commands.argument("entity type", ArgumentTypes.resource(RegistryKey.ENTITY_TYPE))
                        .suggests((c, b) -> suggest(MobAI.availableTypes(), b))
                        .then(Commands.literal("clear")
                                .executes(c -> {
                                    MobAI.clearTracking(c.getArgument("entity type", EntityType.class));
                                    return Command.SINGLE_SUCCESS;
                                }))
                        .then(Commands.argument("aspect", StringArgumentType.word())
                                .suggests((c, b) -> suggest(MobAI.availableFor(c.getArgument("entity type", EntityType.class)), b))
                                .executes(c -> {
                                    EntityType type = c.getArgument("entity type", EntityType.class);
                                    MobAI.startTracking(type, aspect(type, StringArgumentType.getString(c, "aspect")));
                                    return Command.SINGLE_SUCCESS;
                                })))
                .build();
    }

    private MobAI.TrackingType aspect(EntityType type, String aspect) throws CommandSyntaxException {
        try {
            MobAI.TrackingType trackingType = MobAI.TrackingType.valueOf(aspect.toUpperCase(Locale.ROOT));
            if (trackingType.types.contains(type)) {
                return trackingType;
            }
        } catch (IllegalArgumentException ignored) {
        }
        throw new SimpleCommandExceptionType(MessageComponentSerializer.message().serialize(
                Component.text("Can't track " + aspect + " for " + type.getKey().getKey(), NamedTextColor.RED))).create();
    }

    private CompletableFuture<Suggestions> suggest(Stream<String> values, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        values.filter(value -> value.toLowerCase(Locale.ROOT).startsWith(remaining)).forEach(builder::suggest);
        return builder.buildFuture();
    }

}
