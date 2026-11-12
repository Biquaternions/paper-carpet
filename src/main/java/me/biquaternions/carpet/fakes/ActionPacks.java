package me.biquaternions.carpet.fakes;

import lombok.experimental.UtilityClass;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * <p>Holds the {@link PlayerActionPack} of every player.</p>
 *
 * <p>fabric-carpet adds a field to {@code ServerPlayer} through a mixin. On Paper, fake players own their action pack, and
 * the action packs of real players (controlled with {@code /player <name> ...}) are kept here and ticked at the start of
 * every server tick instead of at the start of the player tick.</p>
 */
@NullMarked
@UtilityClass
public class ActionPacks {

    private final Map<UUID, PlayerActionPack> REAL_PLAYERS = new HashMap<>();

    public PlayerActionPack get(final ServerPlayer player) {
        if (player instanceof FakePlayer fake) {
            return fake.getActionPack();
        }
        PlayerActionPack pack = REAL_PLAYERS.get(player.getUUID());
        if (pack == null || pack.player() != player) {
            pack = new PlayerActionPack(player);
            REAL_PLAYERS.put(player.getUUID(), pack);
        }
        return pack;
    }

    public void remove(final UUID uuid) {
        PlayerActionPack pack = REAL_PLAYERS.remove(uuid);
        if (pack != null) {
            pack.stopAll();
        }
    }

    /**
     * Ticks the action packs of real players.
     */
    public void tickRealPlayers() {
        if (REAL_PLAYERS.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, PlayerActionPack> entry : Map.copyOf(REAL_PLAYERS).entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || ((CraftPlayer) player).getHandle() != entry.getValue().player()) {
                REAL_PLAYERS.remove(entry.getKey());
                continue;
            }
            entry.getValue().onUpdate();
        }
    }

    public void clear() {
        REAL_PLAYERS.values().forEach(PlayerActionPack::stopAll);
        REAL_PLAYERS.clear();
    }

}
