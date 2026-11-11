package com.tupinicraft.network;
import com.tupinicraft.TupiniCraft;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
/** Only intent, nonce and sequence; never a client balance or result. bet=-1 closes. */
public record TigrinhoSpinPayload(UUID session,int sequence,int bet) implements CustomPacketPayload {
    public static final Type<TigrinhoSpinPayload> TYPE=new Type<>(TupiniCraft.id("tigrinho_spin"));
    public static final StreamCodec<FriendlyByteBuf,TigrinhoSpinPayload> STREAM_CODEC=new StreamCodec<>() {
        public TigrinhoSpinPayload decode(FriendlyByteBuf b) { return new TigrinhoSpinPayload(b.readUUID(),b.readVarInt(),b.readVarInt()); }
        public void encode(FriendlyByteBuf b,TigrinhoSpinPayload p) { b.writeUUID(p.session()); b.writeVarInt(p.sequence()); b.writeVarInt(p.bet()); }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
