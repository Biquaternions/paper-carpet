package me.biquaternions.carpet.fakes;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import org.jspecify.annotations.Nullable;

import java.net.InetAddress;
import java.net.InetSocketAddress;

/**
 * A connection that goes nowhere, used by {@link FakePlayer fake players}.
 */
public class FakeClientConnection extends Connection {

    public FakeClientConnection() {
        super(PacketFlow.SERVERBOUND);
        // makes isConnected return true, allowing ender pearls to teleport fake players and the join event to complete
        this.channel = new EmbeddedChannel();
        this.address = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
    }

    @Override
    public void setReadOnly() {
    }

    @Override
    public void send(Packet<?> packet) {
    }

    @Override
    public void send(Packet<?> packet, @Nullable ChannelFutureListener listener) {
    }

    @Override
    public void send(Packet<?> packet, @Nullable ChannelFutureListener listener, boolean flush) {
    }

    @Override
    public void sendPacketAndScheduleNettyTask(Packet<?> packet, boolean flush, Runnable task) {
        task.run();
    }

    @Override
    public void flushChannel() {
    }

    @Override
    public void handleDisconnection() {
    }

    @Override
    public void setListenerForServerboundHandshake(PacketListener packetListener) {
    }

    @Override
    public <T extends PacketListener> void setupInboundProtocol(ProtocolInfo<T> protocolInfo, T packetListener) {
    }

}
