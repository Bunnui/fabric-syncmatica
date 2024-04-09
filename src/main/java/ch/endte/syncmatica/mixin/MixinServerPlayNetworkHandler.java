package ch.endte.syncmatica.mixin;

import ch.endte.syncmatica.Context;
import ch.endte.syncmatica.Syncmatica;
import ch.endte.syncmatica.communication.ExchangeTarget;
import ch.endte.syncmatica.communication.PacketType;
import ch.endte.syncmatica.communication.ServerCommunicationManager;
import ch.endte.syncmatica.network.ChannelManager;
import ch.endte.syncmatica.network.IServerPlayerNetworkHandler;
import ch.endte.syncmatica.network.SyncmaticaPayload;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkThreadUtils;
import net.minecraft.network.listener.ServerPlayPacketListener;
import net.minecraft.network.listener.TickablePacketListener;
import net.minecraft.network.packet.c2s.common.CustomPayloadC2SPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.*;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;


@Mixin(value = ServerPlayNetworkHandler.class, priority = 998)
public abstract class MixinServerPlayNetworkHandler extends ServerCommonNetworkHandler implements IServerPlayerNetworkHandler, ServerPlayPacketListener, PlayerAssociatedNetworkHandler, TickablePacketListener {
    @Shadow
    public ServerPlayerEntity player;

    @Unique
    private ExchangeTarget exTarget = null;

    @Unique
    private ServerCommunicationManager comManager = null;

    public MixinServerPlayNetworkHandler(MinecraftServer server, ClientConnection connection, ConnectedClientData clientData) {
        super(server, connection, clientData);
    }

    @Inject(method = "onDisconnected", at = @At("HEAD"))
    public void onDisconnected(final Text reason, final CallbackInfo ci) {
        ChannelManager.onServerPlayerDisconnected(syncmatica$getExchangeTarget());
        syncmatica$operateComms(sm -> sm.onPlayerLeave(syncmatica$getExchangeTarget()));
    }

    @Override
    public void onCustomPayload(CustomPayloadC2SPacket packet) {
        if (packet.payload() instanceof SyncmaticaPayload payload) {
            ChannelManager.onChannelRegisterHandle(syncmatica$getExchangeTarget(), payload.id(), payload.byteBuf());
            if (PacketType.containsIdentifier(payload.id())) {
                NetworkThreadUtils.forceMainThread(packet, this, player.getServerWorld());
                syncmatica$operateComms(sm -> sm.onPacket(syncmatica$getExchangeTarget(), payload.id(), payload.byteBuf()));
            }
        }
    }

    public void syncmatica$operateComms(final Consumer<ServerCommunicationManager> operation) {
        if (comManager == null) {
            final Context con = Syncmatica.getContext(Syncmatica.SERVER_CONTEXT);
            if (con != null) {
                comManager = (ServerCommunicationManager) con.getCommunicationManager();
            }
        }
        if (comManager != null) {
            operation.accept(comManager);
        }
    }

    public ExchangeTarget syncmatica$getExchangeTarget() {
        if (exTarget == null) {
            exTarget = new ExchangeTarget((ServerPlayNetworkHandler) (Object) this);
        }
        return exTarget;
    }
}
