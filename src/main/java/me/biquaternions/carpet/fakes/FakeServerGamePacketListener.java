package me.biquaternions.carpet.fakes;

import io.papermc.paper.connection.DisconnectionReason;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Set;

/**
 * Packet listener of {@link FakePlayer fake players}, port of fabric-carpet's {@code NetHandlerPlayServerFake}.
 */
public class FakeServerGamePacketListener extends ServerGamePacketListenerImpl {

    public FakeServerGamePacketListener(MinecraftServer server, Connection connection, FakePlayer player, CommonListenerCookie cookie) {
        super(server, connection, player, cookie);
    }

    /**
     * Fake players are only removed when they idle or the real player logs in, the rest of disconnections (moved too quickly,
     * flying, etc...) are ignored, like in fabric-carpet. Kicks from {@code /kick} or plugins are honored.
     */
    @Override
    public void disconnect(DisconnectionDetails details) {
        Component message = details.reason();
        boolean kick = details.disconnectionReason()
                .flatMap(DisconnectionReason::game)
                .map(cause -> switch (cause) {
                    case KICKED, PLUGIN, IDLING, DUPLICATE_LOGIN, RESTART_COMMAND, BANNED, IP_BANNED, WHITELIST -> true;
                    default -> false;
                })
                .orElse(false);
        if (kick || !this.server.isRunning() || (message.getContents() instanceof TranslatableContents text
                && (text.getKey().equals("multiplayer.disconnect.idling") || text.getKey().equals("multiplayer.disconnect.duplicate_login")))) {
            ((FakePlayer) this.player).kill(message);
        }
    }

    @Override
    public boolean teleport(PositionMoveRotation positionMoveRotation, Set<Relative> relatives, PlayerTeleportEvent.TeleportCause cause) {
        boolean result = super.teleport(positionMoveRotation, relatives, cause);
        if (this.player.level().getPlayerByUUID(this.player.getUUID()) != null) {
            this.resetPosition();
            this.player.level().getChunkSource().move(this.player);
        }
        return result;
    }

}
