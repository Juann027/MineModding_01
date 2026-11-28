package com.respectcraft.quest;

import net.minecraft.resources.ResourceLocation;

/**
 * Estado persistente que as condicoes podem consultar (Etapa 4.14.2): conquistas que o jogador ja tem, dimensao
 * atual, relogio do servidor e estado das outras missoes. No servidor vem do jogador ({@code QuestService}); no
 * {@code SelfTest}, de um objeto de teste. O motor sobrepoe {@link #questState} com o registro que esta montando.
 */
public interface QuestContext {
    boolean hasAdvancement(ResourceLocation id);

    ResourceLocation dimension();

    /** Relogio de jogo do Overworld (ticks). */
    long now();

    default QuestState questState(ResourceLocation id) { return QuestState.HIDDEN; }
}
