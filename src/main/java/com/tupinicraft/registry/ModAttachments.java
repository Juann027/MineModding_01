package com.tupinicraft.registry;

import com.tupinicraft.TupiniCraft;
import com.tupinicraft.animation.PlayerAnimationTracker;
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

    private ModAttachments() {}
}
