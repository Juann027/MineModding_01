package com.tupinicraft.registry;

import com.tupinicraft.TupiniCraft;
import com.tupinicraft.skilltree.SkillCredits;
import com.tupinicraft.party.PartyAffiliation;
import com.tupinicraft.player.PoliticalData;
import com.tupinicraft.skilltree.SkillTreeState;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, TupiniCraft.MOD_ID);

    /** Dados políticos do jogador: salvos no NBT do jogador e copiados na morte/respawn. */
    public static final Supplier<AttachmentType<PoliticalData>> POLITICAL_DATA = ATTACHMENT_TYPES.register(
            "political_data",
            () -> AttachmentType.builder(() -> PoliticalData.DEFAULT)
                    .serialize(PoliticalData.CODEC)
                    .copyOnDeath()
                    .build());

    /** Créditos internos legados da skill tree (sem economia geral). */
    public static final Supplier<AttachmentType<SkillCredits>> SKILL_CREDITS = ATTACHMENT_TYPES.register(
            "wallet",
            () -> AttachmentType.builder(() -> SkillCredits.EMPTY).serialize(SkillCredits.CODEC).copyOnDeath().build());

    /** Filiação partidária (hoje só por comando de teste). */
    public static final Supplier<AttachmentType<PartyAffiliation>> PARTY = ATTACHMENT_TYPES.register(
            "party",
            () -> AttachmentType.builder(() -> PartyAffiliation.NONE).serialize(PartyAffiliation.CODEC).copyOnDeath().build());

    /** Skills suspensas pelo TupiniCraft por requisito não atendido. */
    public static final Supplier<AttachmentType<SkillTreeState>> SKILL_TREE_STATE = ATTACHMENT_TYPES.register(
            "skill_tree_state",
            () -> AttachmentType.builder(() -> SkillTreeState.EMPTY).serialize(SkillTreeState.CODEC).copyOnDeath().build());

    private ModAttachments() {
    }
}
