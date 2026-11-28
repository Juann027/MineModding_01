package com.respectcraft.quest;

import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;

/**
 * Um acontecimento pontual (Etapa 4.14.2), montado no servidor a partir dos eventos do NeoForge/RespectCraft e
 * entregue ao {@link QuestEngine}. Sem referencia a entidades: so ids (o teste de tag vem pronto), para o motor
 * continuar puro e testavel no {@code SelfTest}.
 *
 * @param subject   tipo da entidade, id da conquista, da dimensao ou da missao
 * @param tags      teste de tag da entidade ({@code #ns:caminho}); sempre falso fora de KILL/FINISHER/BOSS_DEFEAT
 * @param detailId  id da finalizacao (FINISHER) ou nulo
 * @param detail    categoria da vitima (FINISHER: {@code player|mob|boss}), estado novo (QUEST) ou metodo
 *                  (BOSS_DEFEAT: {@code normal|finisher})
 */
public record QuestEvent(QuestTrigger trigger, ResourceLocation subject, Predicate<ResourceLocation> tags,
                         ResourceLocation detailId, String detail) {
    private static final Predicate<ResourceLocation> NO_TAGS = t -> false;

    public QuestEvent {
        tags = tags == null ? NO_TAGS : tags;
        detail = detail == null ? "" : detail;
    }

    public static QuestEvent advancement(ResourceLocation id) { return new QuestEvent(QuestTrigger.ADVANCEMENT, id, null, null, ""); }
    public static QuestEvent kill(ResourceLocation type, Predicate<ResourceLocation> tags) { return new QuestEvent(QuestTrigger.KILL, type, tags, null, ""); }
    public static QuestEvent finisher(ResourceLocation victimType, Predicate<ResourceLocation> tags, ResourceLocation finisher, String victimKind) {
        return new QuestEvent(QuestTrigger.FINISHER, victimType, tags, finisher, victimKind);
    }
    public static QuestEvent boss(ResourceLocation type, Predicate<ResourceLocation> tags, boolean byFinisher) {
        return new QuestEvent(QuestTrigger.BOSS_DEFEAT, type, tags, null, byFinisher ? "finisher" : "normal");
    }
    public static QuestEvent dimension(ResourceLocation dim) { return new QuestEvent(QuestTrigger.DIMENSION, dim, null, null, ""); }
    public static QuestEvent quest(ResourceLocation quest, QuestState state) { return new QuestEvent(QuestTrigger.QUEST, quest, null, null, state.id()); }
}
