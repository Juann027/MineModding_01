package com.tupinicraft.profile;

import com.tupinicraft.skill.SkillEffect;
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
 */
public enum BirthTrait {
    //                    id                 categoria            polaridade         peso
    HIGH_ENDURANCE("high_endurance", Category.ENDURANCE, Polarity.BUFF, 10),
    LIMP("limp", Category.ENDURANCE, Polarity.DEBUFF, 10),
    HEAVY_LOADER("heavy_loader", Category.LOAD, Polarity.BUFF, 10),
    LOADLESS("loadless", Category.LOAD, Polarity.DEBUFF, 10),
    HUNG("hung", Category.ENDOWMENT, Polarity.BUFF, 10),
    TINY_DICK("tiny_dick", Category.ENDOWMENT, Polarity.DEBUFF, 10),
    AVERAGE("average", Category.GENERAL, Polarity.NEUTRAL, 20);

    /** Quantos tracos cada nascimento sorteia. */
    public static final int PER_BIRTH = 1;

    public enum Category { ENDURANCE, LOAD, ENDOWMENT, GENERAL }

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
    private final List<SkillEffect> effects;

    BirthTrait(String id, Category category, Polarity polarity, int weight, SkillEffect... effects) {
        this.id = id;
        this.category = category;
        this.polarity = polarity;
        this.weight = weight;
        this.effects = List.of(effects);
    }

    public String id() { return id; }
    public Category category() { return category; }
    public Polarity polarity() { return polarity; }
    public int weight() { return weight; }
    /** Efeitos de jogo reais (vazio = so descricao, nenhum bonus oculto). */
    public List<SkillEffect> effects() { return effects; }
    public boolean hasEffect() { return !effects.isEmpty(); }
    public String translationKey() { return "trait.tupinicraft." + id; }

    /** Null se o id nao existe (traco removido ou de versao futura). */
    public static BirthTrait byId(String id) {
        for (BirthTrait trait : values()) if (trait.id.equals(id)) return trait;
        return null;
    }

    public static int totalWeight() {
        int total = 0;
        for (BirthTrait trait : values()) total += trait.weight;
        return total;
    }

    /** Sorteio ponderado de {@link #PER_BIRTH} tracos distintos, no maximo um por categoria. */
    public static List<BirthTrait> roll(RandomGenerator random) {
        java.util.List<BirthTrait> picked = new java.util.ArrayList<>();
        int guard = 0;
        while (picked.size() < PER_BIRTH && guard++ < 100) {
            int roll = random.nextInt(totalWeight());
            for (BirthTrait trait : values()) {
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
}
