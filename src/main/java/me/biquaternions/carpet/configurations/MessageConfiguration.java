package me.biquaternions.carpet.configurations;

import de.bsommerfeld.jshepherd.annotation.Comment;
import de.bsommerfeld.jshepherd.annotation.Key;
import de.bsommerfeld.jshepherd.core.ConfigurablePojo;
import de.bsommerfeld.jshepherd.core.ConfigurationLoader;
import me.biquaternions.carpet.PaperCarpet;
import me.biquaternions.jshepherd.annotation.Order;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/**
 * <p>Configurable messages, written in <a href="https://docs.advntr.dev/minimessage/format.html">MiniMessage</a>.</p>
 *
 * <p>Every message has access to a {@code <prefix>} tag, which resolves to {@link #prefix}.</p>
 */
@Comment({
        "Paper Carpet messages, in MiniMessage format: https://docs.advntr.dev/minimessage/format.html",
        "Every message can use the <prefix> tag"
})
public class MessageConfiguration extends ConfigurablePojo<MessageConfiguration> {

    public static MessageConfiguration INSTANCE;

    @Order(0)
    @Key("version")
    @Comment("Do not touch")
    public String version = "1.0";

    @Order(1)
    @Key("prefix")
    public String prefix = "<color:#C7C7C7>[<gradient:#2EFFCE:#B8FEFF:#2EFFCE>Carpet</gradient>]</color>";

    @Order(2)
    @Key("cmd-draw-blocks-filled")
    public String cmdDrawBlocksFilled = "<prefix> Filled <blocks> blocks";

    public static MessageConfiguration load(PaperCarpet plugin) {
        INSTANCE = ConfigurationLoader.load(plugin.getDataPath().resolve("messages.yml"), MessageConfiguration::new);
        return INSTANCE;
    }

    /**
     * Deserializes a message from this configuration, resolving the {@code <prefix>} tag and the given resolvers.
     */
    public static Component format(String message, TagResolver... resolvers) {
        String prefix = INSTANCE == null ? "" : INSTANCE.prefix;
        TagResolver resolver = TagResolver.builder()
                .resolvers(resolvers)
                .resolver(TagResolver.resolver("prefix", (args, ctx) ->
                        net.kyori.adventure.text.minimessage.tag.Tag.selfClosingInserting(MiniMessage.miniMessage().deserialize(prefix))))
                .build();
        return MiniMessage.miniMessage().deserialize(message, resolver);
    }

}
