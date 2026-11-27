package com.respectcraft.network;

import com.respectcraft.RespectCraft;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Pacote das partes de rigs customizadas (Etapa 4.12): servidor → quem ve a entidade, so quando o estado muda e no
 * {@code StartTracking}. Leva so as partes que diferem do padrao (nomes curtos), nunca transformacoes de bones.
 */
public final class RigNetworking {
    private RigNetworking() {}

    /** Estado visual das partes de uma entidade (parte → visivel), so o que difere do padrao do contrato. */
    public record RigPartsSync(int entityId, Map<String, Boolean> overrides) implements CustomPacketPayload {
        public static final Type<RigPartsSync> TYPE = new Type<>(RespectCraft.id("rig_parts"));
        public static final StreamCodec<FriendlyByteBuf, RigPartsSync> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.entityId());
                    buf.writeVarInt(p.overrides().size());
                    p.overrides().forEach((part, visible) -> {
                        buf.writeUtf(part, 64);
                        buf.writeBoolean(visible);
                    });
                },
                buf -> {
                    int id = buf.readVarInt();
                    int n = Math.min(buf.readVarInt(), 64);
                    Map<String, Boolean> map = new LinkedHashMap<>();
                    for (int i = 0; i < n; i++) map.put(buf.readUtf(64), buf.readBoolean());
                    return new RigPartsSync(id, map);
                });

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(RigPartsSync.TYPE, RigPartsSync.STREAM_CODEC,
                (payload, context) -> RigVisualClientCache.receive(payload.entityId(), payload.overrides()));
    }
}
