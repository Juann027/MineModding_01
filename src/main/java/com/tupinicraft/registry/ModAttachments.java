package com.tupinicraft.registry;

import com.tupinicraft.TupiniCraft;
import com.tupinicraft.player.PoliticalData;
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

    private ModAttachments() {
    }
}
