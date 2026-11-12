package me.biquaternions.carpet.network;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.biquaternions.carpet.CarpetServer;
import me.biquaternions.carpet.CarpetSettings;
import me.biquaternions.carpet.PaperCarpet;
import me.biquaternions.carpet.api.settings.CarpetRule;
import me.biquaternions.carpet.api.settings.RuleHelper;
import me.biquaternions.carpet.api.settings.SettingsManager;
import me.biquaternions.carpet.fakes.FakePlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.DiscardedPayload;
import net.minecraft.resources.Identifier;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * <p>Server side of fabric-carpet's client protocol, so players using fabric-carpet on their client get the rule values
 * of the server (which is what makes client side rules like {@code creativeFlySpeed} work).</p>
 *
 * <p>The protocol is a single plugin channel, {@code carpet:hello}, carrying an NBT compound:</p>
 * <ol>
 *     <li>On join, the server sends {@code {"69": version}}</li>
 *     <li>A carpet client answers with {@code {"420": version}}</li>
 *     <li>The server then sends every rule as {@code {"Rules": {name: {Value, Manager, Rule}}}}, and resends a rule whenever it changes</li>
 * </ol>
 *
 * <p>The carpet client doesn't register its channel through {@code minecraft:register}, so Bukkit's
 * {@link Player#sendPluginMessage} would drop the messages. When the channel isn't registered by the client, the custom
 * payload packet is sent directly. Vanilla clients ignore unknown payloads.</p>
 */
@NullMarked
public class ServerNetworkHandler implements Listener, PluginMessageListener {

    public static final String CHANNEL = "carpet:hello";
    private static final Identifier CHANNEL_ID = Identifier.parse(CHANNEL);
    private static final String HI = "69";
    private static final String HELLO = "420";

    private final PaperCarpet plugin;
    private final Map<UUID, String> carpetPlayers = new HashMap<>();

    public ServerNetworkHandler(PaperCarpet plugin) {
        this.plugin = plugin;
    }

    public void register() {
        Bukkit.getMessenger().registerOutgoingPluginChannel(this.plugin, CHANNEL);
        Bukkit.getMessenger().registerIncomingPluginChannel(this.plugin, CHANNEL, this);
        Bukkit.getPluginManager().registerEvents(this, this.plugin);
        SettingsManager.registerGlobalRuleObserver(this::onRuleChanged);
        for (Player player : Bukkit.getOnlinePlayers()) {
            this.sendHi(player);
        }
    }

    public void unregister() {
        Bukkit.getMessenger().unregisterOutgoingPluginChannel(this.plugin, CHANNEL);
        Bukkit.getMessenger().unregisterIncomingPluginChannel(this.plugin, CHANNEL, this);
        this.carpetPlayers.clear();
    }

    public boolean isCarpetPlayer(Player player) {
        return this.carpetPlayers.containsKey(player.getUniqueId());
    }

    public @Nullable String getCarpetVersion(Player player) {
        return this.carpetPlayers.get(player.getUniqueId());
    }

    /**
     * Sends custom data to a player running carpet on their client, like scarpet shapes.
     */
    public void sendCustomCommand(Player player, String command, Tag data) {
        if (CarpetSettings.superSecretSetting || !this.isCarpetPlayer(player)) {
            return;
        }
        CompoundTag tag = new CompoundTag();
        tag.put(command, data);
        this.send(player, tag);
    }

    // ----- Events ----- //

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(final PlayerJoinEvent event) {
        this.sendHi(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(final PlayerQuitEvent event) {
        this.carpetPlayers.remove(event.getPlayer().getUniqueId());
    }

    private void sendHi(Player player) {
        if (((CraftPlayer) player).getHandle() instanceof FakePlayer) {
            return;
        }
        CompoundTag data = new CompoundTag();
        data.putString(HI, CarpetServer.VERSION);
        this.send(player, data);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel)) {
            return;
        }
        CompoundTag data;
        try {
            data = decode(message);
        } catch (IOException e) {
            CarpetServer.LOG.warning("Invalid carpet client data from " + player.getName() + ": " + e.getMessage());
            return;
        }
        if (data == null) {
            return;
        }
        Runnable handle = () -> this.onClientData(player, data);
        if (Bukkit.isPrimaryThread()) {
            handle.run();
        } else {
            Bukkit.getScheduler().runTask(this.plugin, handle);
        }
    }

    private void onClientData(Player player, CompoundTag data) {
        for (String key : data.keySet()) {
            Tag tag = data.get(key);
            switch (key) {
                case HELLO -> this.onHello(player, tag == null ? "" : tag.asString().orElse(""));
                case "clientCommand" -> CarpetServer.LOG.fine("Ignoring client command from " + player.getName() + ", not supported");
                default -> CarpetServer.LOG.warning("Unknown carpet client data: " + key);
            }
        }
    }

    private void onHello(Player player, String version) {
        if (!player.isOnline()) {
            return;
        }
        this.carpetPlayers.put(player.getUniqueId(), version);
        CarpetServer.LOG.info("Player " + player.getName() + " joined with carpet client " + version);
        if (CarpetSettings.superSecretSetting) {
            return;
        }
        this.send(player, rulesData(CarpetServer.settingsManager.getCarpetRules()));
    }

    private void onRuleChanged(@Nullable CommandSourceStack source, CarpetRule<?> rule, String userInput) {
        if (CarpetSettings.superSecretSetting || this.carpetPlayers.isEmpty()) {
            return;
        }
        CompoundTag data = rulesData(java.util.List.of(rule));
        for (UUID uuid : this.carpetPlayers.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                this.send(player, data);
            }
        }
    }

    private static CompoundTag rulesData(Collection<CarpetRule<?>> rules) {
        CompoundTag tag = new CompoundTag();
        CompoundTag rulesTag = new CompoundTag();
        for (CarpetRule<?> rule : rules) {
            String key = rule.name();
            while (rulesTag.contains(key)) {
                key = key + "2";
            }
            CompoundTag ruleNBT = new CompoundTag();
            ruleNBT.putString("Value", RuleHelper.toRuleString(rule.value()));
            ruleNBT.putString("Manager", rule.settingsManager().identifier());
            ruleNBT.putString("Rule", rule.name());
            rulesTag.put(key, ruleNBT);
        }
        tag.put("Rules", rulesTag);
        return tag;
    }

    // ----- Encoding ----- //

    private void send(Player player, CompoundTag data) {
        byte[] bytes;
        try {
            bytes = encode(data);
        } catch (IOException e) {
            CarpetServer.LOG.warning("Failed to encode carpet data: " + e.getMessage());
            return;
        }
        if (player.getListeningPluginChannels().contains(CHANNEL)) {
            player.sendPluginMessage(this.plugin, CHANNEL, bytes);
            return;
        }
        // The carpet client doesn't register its channel, send the payload directly
        CraftPlayer craftPlayer = (CraftPlayer) player;
        if (craftPlayer.getHandle().connection != null) {
            craftPlayer.getHandle().connection.send(new ClientboundCustomPayloadPacket(new DiscardedPayload(CHANNEL_ID, bytes)));
        }
    }

    /**
     * Same format as {@code FriendlyByteBuf#writeNbt}.
     */
    private static byte[] encode(CompoundTag tag) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            NbtIo.writeAnyTag(tag, out);
        }
        return bytes.toByteArray();
    }

    private static @Nullable CompoundTag decode(byte[] message) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            return NbtIo.readAnyTag(in, NbtAccounter.create(2097152L)) instanceof CompoundTag compound ? compound : null;
        }
    }

}
