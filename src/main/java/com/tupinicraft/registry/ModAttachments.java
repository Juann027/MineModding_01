package com.tupinicraft.registry;

import com.tupinicraft.TupiniCraft;
import com.tupinicraft.animation.PlayerAnimationTracker;
import com.tupinicraft.needs.Needs;
import com.tupinicraft.profile.CharacterProfile;
import java.util.function.Supplier;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, TupiniCraft.MOD_ID);

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

    private ModAttachments() {}
}
