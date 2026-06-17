package me.biquaternions.carpet.utils;

import lombok.experimental.UtilityClass;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.StreamSupport;

@UtilityClass
public class Messenger {

    public void send(final Audience audience, final Component component) {
        audience.sendMessage(component);
    }

    public void send(final Audience audience, final Component component, final Component ...more) {
        List<Component> list = new ArrayList<>();
        list.add(component);
        list.addAll(Arrays.asList(more));
        list.forEach(audience::sendMessage);
    }

    public void send(final Audience audience, final Iterable<? extends Component> components) {
        components.forEach(audience::sendMessage);
    }

    public void sendRaw(final Audience audience, final String string) {
        Messenger.send(audience, Component.text(string));
    }

    public void sendRaw(final Audience audience, final String string, final String ...more) {
        List<Component> list = new ArrayList<>();
        list.add(Component.text(string));
        list.addAll(Arrays.stream(more).map(Component::text).toList());
        Messenger.send(audience, list);
    }

    public void sendRaw(final Audience audience, final Iterable<String> strings) {
        Messenger.send(audience, StreamSupport.stream(strings.spliterator(), false).map(Component::text).toList());
    }

    public void sendMiniMessage(final Audience audience, final String string) {
        audience.sendMessage(MiniMessage.miniMessage().deserialize(string));
    }

    public void sendMiniMessage(final Audience audience, final String string, final String ...more) {
        List<Component> list = new ArrayList<>();
        list.add(MiniMessage.miniMessage().deserialize(string));
        list.addAll(Arrays.stream(more).map(MiniMessage.miniMessage()::deserialize).toList());
        Messenger.send(audience, list);
    }

    public void sendMiniMessage(final Audience audience, final Iterable<String> strings) {
        Messenger.send(audience, StreamSupport.stream(strings.spliterator(), false).map(MiniMessage.miniMessage()::deserialize).toList());
    }

}
