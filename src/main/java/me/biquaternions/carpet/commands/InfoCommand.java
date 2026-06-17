package me.biquaternions.carpet.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.BlockPositionResolver;
import io.papermc.paper.math.BlockPosition;
import lombok.experimental.UtilityClass;
import me.biquaternions.carpet.utils.BlockInfo;
import me.biquaternions.carpet.utils.Messenger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@NullMarked
@UtilityClass
@SuppressWarnings("UnstableApiUsage")
public class InfoCommand {

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("info")
                .requires(s -> s.getSender() instanceof Player player && player.hasPermission("carpet.info"))
                .then(Commands.literal("block")
                        .then(Commands.argument("position", ArgumentTypes.blockPosition())
                                .executes(c -> {
                                    Player player = (Player) c.getSource().getSender();
                                    BlockPositionResolver positionResolver = c.getArgument("position", BlockPositionResolver.class);
                                    BlockPosition position = positionResolver.resolve(c.getSource());
                                    Location location = position.toLocation(player.getWorld());
                                    InfoCommand.infoBlock(player, location, null);
                                    return Command.SINGLE_SUCCESS;
                                })
                                .then(Commands.literal("grep")
                                        .then(Commands.argument("regexp", StringArgumentType.greedyString())
                                                .executes(c -> {
                                                    Player player = (Player) c.getSource().getSender();
                                                    BlockPositionResolver positionResolver = c.getArgument("position", BlockPositionResolver.class);
                                                    BlockPosition position = positionResolver.resolve(c.getSource());
                                                    Location location = position.toLocation(player.getWorld());
                                                    String regexp = c.getArgument("regexp", String.class);
                                                    InfoCommand.infoBlock(player, location, regexp);
                                                    return Command.SINGLE_SUCCESS;
                                                })
                                        )
                                )
                        )
                )
                .build();
    }

    public void printBlock(List<Component> messages, CommandSender sender, @Nullable String grep) {
        Messenger.sendRaw(sender, "");
        if (grep != null) {
            Pattern p = Pattern.compile(grep);
            Messenger.send(sender, messages.getFirst());
            for (int i = 1; i < messages.size(); i++) {
                Component line = messages.get(i);
                if (!(line instanceof TextComponent text)) {
                    continue;
                }

                Matcher m = p.matcher(text.content());
                if (m.find()) {
                    Messenger.send(sender, line);
                }
            }
        } else {
            Messenger.send(sender, messages);
        }
    }

    private void infoBlock(CommandSender sender, Location location, @Nullable String grep) {
        if (!sender.hasPermission("carpet.info.extra")) {
            //check id pos is loaded
            World world = location.getWorld();
            if (world == null) {
                Messenger.sendRaw(sender, "World does not exist");
                return;
            }

            if (!world.isChunkLoaded(location.blockX() >> 4, location.blockZ() >> 4)) {
                Messenger.sendRaw(sender, "Chunk is not loaded");
                return;
            }
            // verify it is in world bounds
            if (!world.getWorldBorder().isInside(location)) {
                Messenger.sendRaw(sender, "Position is outside of world bounds");
                return;
            }
        }
        InfoCommand.printBlock(BlockInfo.blockInfo(location), sender, grep);
    }

}
