package com.respectcraft.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.respectcraft.notice.NoticeStyle;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * Regras puras do Quest Engine (Etapa 4.14.2), cobertas pelo {@code SelfTest}: limites, transicoes validas e
 * validacoes de definicao.
 */
public final class QuestRules {
    private QuestRules() {}

    /** Versao do formato JSON lida por esta versao do mod. */
    public static final int FORMAT = 1;
    /** Versao do formato salvo no attachment. */
    public static final int SAVE_VERSION = 1;

    public static final int MAX_CONDITION_DEPTH = 6, MAX_CONDITION_NODES = 24;
    public static final int MAX_OBJECTIVES = 8, MAX_REWARDS = 8, MAX_COUNT = 10_000, MAX_ITEM_REWARD = 576;
    public static final int MAX_DEFINITIONS = 512;
    /** Missoes rastreadas ao mesmo tempo na HUD. */
    public static final int MAX_TRACKED = 3;
    /** Encadeamento maximo de eventos de missao por acontecimento (cadeias). */
    public static final int MAX_CHAIN = 4;
    /** Pedidos do cliente: no maximo este tanto por segundo (o resto e ignorado). */
    public static final int MAX_REQUESTS_PER_SECOND = 8;
    /** Intervalo da varredura de tempo (limite, intervalo de repeticao), por jogador online. */
    public static final int SWEEP_TICKS = 100;

    public static final List<String> VICTIM_KINDS = List.of("player", "mob", "boss");

    public static final Codec<String> VICTIM_KIND = Codec.STRING.comapFlatMap(s -> VICTIM_KINDS.contains(s)
            ? DataResult.success(s) : DataResult.error(() -> "victim_kind must be player, mob or boss: " + s), s -> s);

    /** "auto" = true, "manual" = false. */
    public static final Codec<Boolean> MODE = Codec.STRING.comapFlatMap(s -> switch (s) {
        case "auto" -> DataResult.success(true);
        case "manual" -> DataResult.success(false);
        default -> DataResult.error(() -> "expected auto or manual: " + s);
    }, b -> b ? "auto" : "manual");

    public static final Codec<NoticeStyle> STYLE = Codec.STRING.comapFlatMap(s -> {
        NoticeStyle st = NoticeStyle.byId(s);
        return st == null ? DataResult.error(() -> "unknown notice style " + s) : DataResult.success(st);
    }, NoticeStyle::id);

    /**
     * Transicoes que o motor aceita. Acoes do cliente sao so pedidos: o servidor confere aqui e nas regras da
     * definicao (aceitacao manual, abandono permitido, resgate manual, repeticao).
     */
    public static boolean canTransition(QuestState from, QuestState to, QuestDef.Flags f) {
        return switch (from) {
            case HIDDEN -> to == QuestState.AVAILABLE || to == QuestState.ACTIVE;
            case AVAILABLE -> to == QuestState.ACTIVE;
            case ACTIVE -> to == QuestState.COMPLETED || to == QuestState.FAILED || to == QuestState.EXPIRED
                    || (to == QuestState.CANCELLED && f.abandonable());
            case COMPLETED -> to == QuestState.CLAIMED;
            case CLAIMED -> f.repeatable() && (to == QuestState.AVAILABLE || to == QuestState.ACTIVE);
            case FAILED, EXPIRED, CANCELLED -> f.retry() && to == QuestState.ACTIVE;
        };
    }

    /** Pode aceitar a partir deste estado (aceitacao manual ou nova tentativa)? */
    public static boolean canAccept(QuestState s, QuestDef.Flags f) {
        return s == QuestState.AVAILABLE || ((s == QuestState.FAILED || s == QuestState.EXPIRED || s == QuestState.CANCELLED) && f.retry());
    }

    /** O tempo limite acabou? */
    public static boolean expired(long acceptedAt, long limit, long now) {
        return limit > 0 && now - acceptedAt >= limit;
    }

    /** Repetivel pronta para reabrir? */
    public static boolean reopens(QuestState s, QuestDef.Flags f, long cooldownUntil, long now) {
        return s == QuestState.CLAIMED && f.repeatable() && now >= cooldownUntil;
    }

    public static boolean finisherMatches(Optional<ResourceLocation> finisher, QuestMatcher victim, Optional<String> kind, QuestEvent e) {
        if (finisher.isPresent() && !finisher.get().equals(e.detailId())) return false;
        if (kind.isPresent() && !kind.get().equals(e.detail())) return false;
        return victim.matches(e.subject(), e.tags());
    }

    public static boolean validKey(String s) {
        return s != null && !s.isEmpty() && s.length() <= 32 && s.chars().allMatch(c -> (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-');
    }

    static String validMatcher(Optional<String> target, boolean required) {
        if (target.isEmpty()) return required ? "needs target (entity id, #tag or *)" : null;
        return QuestMatcher.parse(target.get()).error().map(err -> err.message()).orElse(null);
    }

    static String validId(QuestObjective o, String what) {
        if (o.target().isEmpty() || o.targetId() == null) return "needs target (" + what + " id)";
        return null;
    }

    static void checkComplexity(String field, QuestCondition c, List<String> errors) {
        if (QuestCondition.depth(c) > MAX_CONDITION_DEPTH) errors.add(field + ": conditions nested deeper than " + MAX_CONDITION_DEPTH);
        if (QuestCondition.nodes(c) > MAX_CONDITION_NODES) errors.add(field + ": more than " + MAX_CONDITION_NODES + " conditions");
    }
}
