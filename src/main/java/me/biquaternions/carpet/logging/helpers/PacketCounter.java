package me.biquaternions.carpet.logging.helpers;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import lombok.experimental.UtilityClass;
import me.biquaternions.carpet.CarpetServer;
import net.minecraft.network.protocol.Packet;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.concurrent.atomic.AtomicLong;

/**
 * <p>Counts the packets sent and received by the server.</p>
 *
 * <p>fabric-carpet uses a mixin into {@code Connection}, on Paper a netty handler is injected into each player's channel
 * instead, which means only packets of players in the play phase are counted.</p>
 */
@UtilityClass
public class PacketCounter {

    private final String HANDLER_NAME = "carpet_packet_counter";
    private final AtomicLong TOTAL_OUT = new AtomicLong();
    private final AtomicLong TOTAL_IN = new AtomicLong();

    public long totalIn() {
        return TOTAL_IN.get();
    }

    public long totalOut() {
        return TOTAL_OUT.get();
    }

    public void reset() {
        TOTAL_IN.set(0L);
        TOTAL_OUT.set(0L);
    }

    public void inject(Player player) {
        try {
            Channel channel = ((CraftPlayer) player).getHandle().connection.connection.channel;
            if (channel == null) {
                return;
            }
            channel.eventLoop().execute(() -> {
                ChannelPipeline pipeline = channel.pipeline();
                if (pipeline.get(HANDLER_NAME) != null || pipeline.get("packet_handler") == null) {
                    return;
                }
                pipeline.addBefore("packet_handler", HANDLER_NAME, new Handler());
            });
        } catch (Exception e) {
            CarpetServer.LOG.warning("Failed to inject packet counter for " + player.getName() + ": " + e);
        }
    }

    public void uninject(Player player) {
        try {
            Channel channel = ((CraftPlayer) player).getHandle().connection.connection.channel;
            if (channel == null) {
                return;
            }
            channel.eventLoop().execute(() -> {
                if (channel.pipeline().get(HANDLER_NAME) != null) {
                    channel.pipeline().remove(HANDLER_NAME);
                }
            });
        } catch (Exception ignored) {
        }
    }

    @ChannelHandler.Sharable
    private static final class Handler extends ChannelDuplexHandler {

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof Packet<?>) {
                TOTAL_IN.incrementAndGet();
            }
            super.channelRead(ctx, msg);
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (msg instanceof Packet<?>) {
                TOTAL_OUT.incrementAndGet();
            }
            super.write(ctx, msg, promise);
        }

    }

}
