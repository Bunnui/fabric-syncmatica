package ch.endte.syncmatica.network;

import ch.endte.syncmatica.communication.ExchangeTarget;
import ch.endte.syncmatica.communication.PacketType;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

import java.nio.charset.StandardCharsets;
import java.util.*;

public class ChannelManager {
    public static final Identifier MINECRAFT_REGISTER = new Identifier("minecraft:register");
    public static final Identifier MINECRAFT_UNREGISTER = new Identifier("minecraft:unregister");
    private static final Map<ExchangeTarget, List<Identifier>> serverRegisteredChannels = new HashMap<>();
    private static final List<Identifier> clientRegisteredChannels = new ArrayList<>();

    public static void initSendRegister(ExchangeTarget target) {
        // 构建数据
        PacketByteBuf byteBuf = new PacketByteBuf(Unpooled.buffer());
        for (PacketType identifierType : PacketType.values()) {
            Identifier identifier = identifierType.identifier;
            byte[] bytes = identifier.toString().getBytes(StandardCharsets.UTF_8);
            byteBuf.writeBytes(bytes);
            byteBuf.writeByte(0x00);
        }
        // 有数据时才有意义发送
        if (byteBuf.writerIndex() > 0) {
            target.sendPacket(MINECRAFT_REGISTER, byteBuf, null);
        }
    }

    private static List<Identifier> onReadRegisterIdentifier(PacketByteBuf data) {
        List<Identifier> identifiers = new ArrayList<>();
        int start = 0;
        while (data.isReadable()) {
            byte b = data.readByte();
            if (b == 0x00) {
                String string = data.toString(start, data.readerIndex() - start - 1, StandardCharsets.UTF_8);
                string = string.split("/")[0].split("\\\\")[0];
                identifiers.add(new Identifier(string));
                start = data.readerIndex();
            }
        }
        return identifiers;
    }

    public static void onChannelRegisterHandle(ExchangeTarget target, Identifier channel, PacketByteBuf data) {
        if (!channel.equals(MINECRAFT_REGISTER)) {
            return;
        }
        // 拷贝一份数据进行处理, 因为可能其他插件(fabric-api)也存在通道
        List<Identifier> identifiers = onReadRegisterIdentifier(new PacketByteBuf(data.copy()));
        // 获取已当前目标已注册的标识符
        List<Identifier> registeredChannels = target.isClient() ? clientRegisteredChannels : serverRegisteredChannels.computeIfAbsent(target, value -> new ArrayList<>());
        // 构建数据
        PacketByteBuf byteBuf2 = new PacketByteBuf(Unpooled.buffer());
        for (Identifier identifier : identifiers) {
            // 当前模组支持该通道且未被注册
            if (PacketType.containsIdentifier(identifier) && !registeredChannels.contains(identifier)) {
                // LoggerFactory.getLogger("").info(identifier.toString());
                byte[] bytes = identifier.toString().getBytes(StandardCharsets.UTF_8);
                byteBuf2.writeBytes(bytes);
                byteBuf2.writeByte(0x00);
            }
        }
        // 有数据时才有意义发送
        if (byteBuf2.writerIndex() > 0) {
            target.sendPacket(MINECRAFT_REGISTER, byteBuf2, null);
        }
    }

    public static void onChannelUnRegisterHandle(ExchangeTarget target, Identifier channel, PacketByteBuf data) {
        if (!channel.equals(MINECRAFT_UNREGISTER)) {
            return;
        }
        // 拷贝一份数据进行处理, 因为可能其他插件(fabric-api)也存在通道
        List<Identifier> identifiers = onReadRegisterIdentifier(new PacketByteBuf(data.copy()));
        // 获取已当前目标已注册的标识符
        List<Identifier> registeredChannels = target.isClient() ? clientRegisteredChannels : serverRegisteredChannels.computeIfAbsent(target, value -> new ArrayList<>());
        // 删除当前模组已注册的通道
        identifiers.removeIf(registeredChannels::contains);
    }

    public static void onClientDisconnected() {
        clientRegisteredChannels.clear();
    }

    public static void onServerDisconnected() {
        serverRegisteredChannels.clear();
    }

    public static void onServerPlayerDisconnected(ExchangeTarget target) {
        serverRegisteredChannels.remove(target);
    }
}
