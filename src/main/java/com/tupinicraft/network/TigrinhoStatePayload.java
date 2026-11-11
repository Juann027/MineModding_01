package com.tupinicraft.network;
import com.tupinicraft.TupiniCraft;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
/** Immutable authoritative snapshot; bounded strings keep packet size below 1 MiB. */
public record TigrinhoStatePayload(UUID session,int sequence,boolean open,long balance,
        int a,int b,int c,long prize,int animation,int waitTicks,boolean sounds,String message,String symbols,String bets)
        implements CustomPacketPayload {
    public static final Type<TigrinhoStatePayload> TYPE=new Type<>(TupiniCraft.id("tigrinho_state"));
    public static final StreamCodec<FriendlyByteBuf,TigrinhoStatePayload> STREAM_CODEC=new StreamCodec<>() {
        public TigrinhoStatePayload decode(FriendlyByteBuf b) {
            return new TigrinhoStatePayload(b.readUUID(),b.readVarInt(),b.readBoolean(),b.readLong(),
                b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readLong(),b.readVarInt(),b.readVarInt(),
                b.readBoolean(),b.readUtf(256),b.readUtf(8192),b.readUtf(256));
        }
        public void encode(FriendlyByteBuf b,TigrinhoStatePayload p) {
            b.writeUUID(p.session()); b.writeVarInt(p.sequence()); b.writeBoolean(p.open()); b.writeLong(p.balance());
            b.writeVarInt(p.a()); b.writeVarInt(p.b()); b.writeVarInt(p.c()); b.writeLong(p.prize());
            b.writeVarInt(p.animation()); b.writeVarInt(p.waitTicks()); b.writeBoolean(p.sounds());
            b.writeUtf(p.message(),256); b.writeUtf(p.symbols(),8192); b.writeUtf(p.bets(),256);
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
