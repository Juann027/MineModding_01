package com.respectcraft.registry;

import com.respectcraft.RespectCraft;
import com.respectcraft.animation.PlayerAnimationTracker;
import com.respectcraft.needs.ActiveNeedEffects;
import com.respectcraft.needs.Needs;
import com.respectcraft.profile.CharacterProfile;
import java.util.function.Supplier;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, RespectCraft.MOD_ID);

    /** Animacao em andamento do jogador (servidor). Sem serializador: nao e salva nem copiada na morte. */
    public static final Supplier<AttachmentType<PlayerAnimationTracker>> PLAYER_ANIMATION =
            ATTACHMENT_TYPES.register("player_animation",
                    () -> AttachmentType.builder(PlayerAnimationTracker::new).build());

    /** Ficha do personagem (nascimento, atributos, habilidades). Salva e copiada na morte. Id estavel. */
    public static final Supplier<AttachmentType<CharacterProfile>> CHARACTER_PROFILE =
            ATTACHMENT_TYPES.register("character_profile",
                    () -> AttachmentType.builder(() -> CharacterProfile.DEFAULT)
                            .serialize(CharacterProfile.CODEC).copyOnDeath().build());

    /** Necessidades do corpo. Salvas, mas NAO copiadas na morte: o jogador renasce com os valores padrao. */
    public static final Supplier<AttachmentType<Needs>> NEEDS =
            ATTACHMENT_TYPES.register("needs",
                    () -> AttachmentType.builder(() -> Needs.DEFAULT).serialize(Needs.CODEC).build());

    /** Efeitos de consumiveis em andamento (Etapa 3.1). Salvos, nao copiados na morte. Id estavel. */
    public static final Supplier<AttachmentType<ActiveNeedEffects>> NEED_EFFECTS =
            ATTACHMENT_TYPES.register("need_effects",
                    () -> AttachmentType.builder(() -> ActiveNeedEffects.EMPTY).serialize(ActiveNeedEffects.CODEC).build());

    /**
     * Etapa 4.10: efeitos do Calamidade agendados por keyframes {@code calamidade:} de uma animacao GeckoLib
     * ({@code fx.GeoFxKeyframes}). Sem serializador: nao e salvo; some com a entidade.
     */
    public static final Supplier<AttachmentType<com.respectcraft.fx.GeoFxKeyframes.Schedule>> GEO_FX_SCHEDULE =
            ATTACHMENT_TYPES.register("geo_fx_schedule",
                    () -> AttachmentType.builder(com.respectcraft.fx.GeoFxKeyframes.Schedule::new).build());

    /**
     * Etapa 4.12: partes ocultas/expostas de uma entidade com rig customizada ({@code rig.RigVisuals}). Sem
     * serializador: estado visual temporario, nunca salvo; some com a entidade.
     */
    public static final Supplier<AttachmentType<com.respectcraft.rig.RigVisuals.State>> RIG_VISUAL =
            ATTACHMENT_TYPES.register("rig_visual",
                    () -> AttachmentType.builder(com.respectcraft.rig.RigVisuals.State::new).build());

    /**
     * Etapa 4.14.2: missoes do jogador (Quest Engine). Salvo e copiado na morte; ids desconhecidos ficam guardados.
     * Id estavel.
     */
    public static final Supplier<AttachmentType<com.respectcraft.quest.QuestLog>> QUESTS =
            ATTACHMENT_TYPES.register("quests",
                    () -> AttachmentType.builder(() -> com.respectcraft.quest.QuestLog.EMPTY)
                            .serialize(com.respectcraft.quest.QuestLog.CODEC).copyOnDeath().build());

    /** Etapa 4.14.2: limite de pedidos de missao por segundo. Sem serializador: nunca salvo. */
    public static final Supplier<AttachmentType<com.respectcraft.quest.QuestService.Limiter>> QUEST_REQUESTS =
            ATTACHMENT_TYPES.register("quest_requests",
                    () -> AttachmentType.builder(com.respectcraft.quest.QuestService.Limiter::new).build());

    private ModAttachments() {}
}
