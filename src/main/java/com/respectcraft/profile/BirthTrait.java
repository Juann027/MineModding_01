package com.respectcraft.profile;

import com.respectcraft.skill.SkillEffect;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Tracos de nascimento: caracteristicas individuais (buff, debuff ou neutras) sorteadas junto com o nascimento.
 * Sao um sistema separado dos atributos ({@link CharacterAttribute}), das necessidades ({@code needs.Need}) e da
 * origem ({@link BirthOrigin}): nao derivam da origem e nao mudam os seis atributos.
 *
 * <p>Os ids sao gravados na ficha ({@code birth_traits}): nao renomeie. Os nomes atuais sao referencias iniciais
 * de design; <b>nenhum traco tem efeito de jogo aprovado ainda</b>, por isso todos tem {@code effects} vazio e a
 * interface diz "sem efeito no jogo por enquanto". Para dar um efeito a um traco, preencha {@code effects}
 * ({@link SkillEffect}: atributo vanilla, ritmo de necessidade ou bonus de emocao); {@code SkillService} aplica
 * esses efeitos do mesmo jeito que os das habilidades (modificadores transitorios).
 *
 * <p><b>Elegibilidade (Etapa 4.6).</b> {@link Eligibility} diz so quem pode <i>receber o traco num sorteio novo</i>:
 * {@code ALL} (universal) ou {@code MALE_ONLY} (exclusivo do sorteio masculino). Um traco ja gravado na ficha nunca
 * e apagado por causa da elegibilidade: personagens antigos e quem mudou a aparencia depois de confirmar o
 * nascimento continuam com o traco. Fertile/Infertile sao so classificacao (sem efeito, sem gravidez).
 */
public enum BirthTrait {
    //                    id                 categoria            polaridade       peso  elegibilidade
    HIGH_ENDURANCE("high_endurance", Category.ENDURANCE, Polarity.BUFF, 10, Eligibility.ALL),
    LIMP("limp", Category.ENDURANCE, Polarity.DEBUFF, 10, Eligibility.ALL),
    HEAVY_LOADER("heavy_loader", Category.LOAD, Polarity.BUFF, 10, Eligibility.MALE_ONLY),
    LOADLESS("loadless", Category.LOAD, Polarity.DEBUFF, 10, Eligibility.MALE_ONLY),
    HUNG("hung", Category.ENDOWMENT, Polarity.BUFF, 10, Eligibility.MALE_ONLY),
    TINY_DICK("tiny_dick", Category.ENDOWMENT, Polarity.DEBUFF, 10, Eligibility.MALE_ONLY),
    AVERAGE("average", Category.GENERAL, Polarity.NEUTRAL, 20, Eligibility.ALL),
    // Etapa 4.6: universais, so classificados e gravados (sem efeito; nada de gravidez ainda).
    FERTILE("fertile", Category.FERTILITY, Polarity.BUFF, 10, Eligibility.ALL),
    INFERTILE("infertile", Category.FERTILITY, Polarity.DEBUFF, 10, Eligibility.ALL);

    /** Quantos tracos cada nascimento sorteia. */
    public static final int PER_BIRTH = 1;

    public enum Category { ENDURANCE, LOAD, ENDOWMENT, GENERAL, FERTILITY }

    /** Corpo escolhido no Character Creator, visto pelo sorteio (indice {@code CharacterAppearance.BODY_IDS}). */
    public enum Body {
        MALE, FEMALE;

        /** 1 = "feminino"; qualquer outro valor (inclusive aparencia ainda nao criada) = masculino. */
        public static Body fromBodyType(int bodyType) { return bodyType == 1 ? FEMALE : MALE; }
    }

    /** Quem pode receber o traco num sorteio novo (nao afeta tracos ja gravados). */
    public enum Eligibility {
        ALL, MALE_ONLY;

        public boolean allows(Body body) { return this == ALL || body == Body.MALE; }
    }

    public enum Polarity {
        BUFF(0xFF6BD37E), DEBUFF(0xFFE06464), NEUTRAL(0xFFB8C0C8);

        private final int color;
        Polarity(int color) { this.color = color; }
        public int color() { return color; }
        public String id() { return name().toLowerCase(java.util.Locale.ROOT); }
    }

    private final String id;
    private final Category category;
    private final Polarity polarity;
    private final int weight;
    private final Eligibility eligibility;
    private final List<SkillEffect> effects;

    BirthTrait(String id, Category category, Polarity polarity, int weight, Eligibility eligibility, SkillEffect... effects) {
        this.id = id;
        this.category = category;
        this.polarity = polarity;
        this.weight = weight;
        this.eligibility = eligibility;
        this.effects = List.of(effects);
    }

    public String id() { return id; }
    public Category category() { return category; }
    public Polarity polarity() { return polarity; }
    public int weight() { return weight; }
    public Eligibility eligibility() { return eligibility; }
    /** Pode sair num sorteio novo para esse corpo? */
    public boolean eligibleFor(Body body) { return eligibility.allows(body); }
    /** Efeitos de jogo reais (vazio = so descricao, nenhum bonus oculto). */
    public List<SkillEffect> effects() { return effects; }
    public boolean hasEffect() { return !effects.isEmpty(); }
    public String translationKey() { return "trait.respectcraft." + id; }

    /** Null se o id nao existe (traco removido ou de versao futura). */
    public static BirthTrait byId(String id) {
        for (BirthTrait trait : values()) if (trait.id.equals(id)) return trait;
        return null;
    }

    /** Tracos que podem sair num sorteio novo para esse corpo, na ordem do enum. */
    public static List<BirthTrait> pool(Body body) {
        java.util.List<BirthTrait> list = new java.util.ArrayList<>();
        for (BirthTrait trait : values()) if (trait.eligibleFor(body)) list.add(trait);
        return List.copyOf(list);
    }

    public static int totalWeight(Body body) {
        int total = 0;
        for (BirthTrait trait : pool(body)) total += trait.weight;
        return total;
    }

    /** Sorteio ponderado de {@link #PER_BIRTH} tracos distintos do conjunto do corpo, no maximo um por categoria. */
    public static List<BirthTrait> roll(RandomGenerator random, Body body) {
        List<BirthTrait> pool = pool(body);
        int total = totalWeight(body);
        java.util.List<BirthTrait> picked = new java.util.ArrayList<>();
        int guard = 0;
        while (picked.size() < PER_BIRTH && guard++ < 100) {
            int roll = random.nextInt(total);
            for (BirthTrait trait : pool) {
                roll -= trait.weight;
                if (roll < 0) {
                    boolean clash = picked.stream().anyMatch(p -> p.category == trait.category);
                    if (!clash) picked.add(trait);
                    break;
                }
            }
        }
        return List.copyOf(picked);
    }

    /**
     * Traco coerente com o corpo para um resultado <b>ainda nao confirmado</b> quando o jogador troca o corpo:
     * o proprio traco se ele for elegivel; senao um traco elegivel de <b>mesma polaridade</b>, escolhido de forma
     * deterministica por {@code salt} (o {@code rollCount} da ficha). Nao sorteia de novo: alternar o corpo varias
     * vezes nunca gera tracos extras nem troca debuff por buff, e o traco substituto (universal) continua valido
     * ao voltar ao corpo anterior.
     */
    public static BirthTrait coherentWith(BirthTrait trait, Body body, int salt) {
        if (trait.eligibleFor(body)) return trait;
        java.util.List<BirthTrait> same = new java.util.ArrayList<>();
        for (BirthTrait candidate : pool(body)) if (candidate.polarity == trait.polarity) same.add(candidate);
        if (same.isEmpty()) return AVERAGE;
        return same.get(Math.floorMod(salt * 31 + trait.ordinal(), same.size()));
    }
}
