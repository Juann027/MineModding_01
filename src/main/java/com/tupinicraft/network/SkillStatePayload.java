package com.tupinicraft.network;

import com.tupinicraft.TupiniCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/**
 * Servidor → cliente: estado das skills do próprio jogador (compradas, suspensas) e quais abas ele vê
 * (só a árvore da sua região e a do seu partido, além das pessoais e de cargo). Enviado quando algo muda.
 */
public record SkillStatePayload(List<String> unlocked, List<String> suspended, List<String> visibleTrees)
        implements CustomPacketPayload {
    public static final Type<SkillStatePayload> TYPE = new Type<>(TupiniCraft.id("skill_state"));
    private static final StreamCodec<ByteBuf, List<String>> STRINGS = ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list());
    public static final StreamCodec<ByteBuf, SkillStatePayload> STREAM_CODEC = StreamCodec.composite(
            STRINGS, SkillStatePayload::unlocked,
            STRINGS, SkillStatePayload::suspended,
            STRINGS, SkillStatePayload::visibleTrees,
            SkillStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
