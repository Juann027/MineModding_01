package com.tupinicraft.network;

import com.tupinicraft.TupiniCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Cliente → servidor: o jogador clicou numa skill bloqueada no painel do SkillLib (tecla K).
 * Carrega só a intenção; o servidor valida tudo, cobra e desbloqueia ({@code SkillTreeService.purchaseFromPanel}).
 */
public record SkillPurchaseRequestPayload(ResourceLocation skillId) implements CustomPacketPayload {
    public static final Type<SkillPurchaseRequestPayload> TYPE = new Type<>(TupiniCraft.id("skill_purchase"));
    public static final StreamCodec<ByteBuf, SkillPurchaseRequestPayload> STREAM_CODEC =
            ResourceLocation.STREAM_CODEC.map(SkillPurchaseRequestPayload::new, SkillPurchaseRequestPayload::skillId);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
