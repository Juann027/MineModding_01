package com.tupinicraft.profile;

import java.util.Arrays;
import java.util.random.RandomGenerator;

/**
 * Distribuicao de raridades do Family Legacy: chances mutuamente exclusivas em milesimos, somando exatamente
 * {@link #TOTAL} (100,0%). O sorteio tira um bilhete inteiro de 0 a 999 e o converte na raridade pela soma
 * acumulada, na ordem do ranking (Common primeiro) — cada raridade ocupa exatamente {@code permille} bilhetes.
 *
 * <p>Nunca e normalizada nem redistribuida: uma distribuicao que nao soma 100,0% e recusada em {@link #of}, e uma
 * raridade sem familias cadastradas continua com a sua chance (ver {@link FamilyLegacyRoll}). Puro.
 */
public final class FamilyLegacyOdds {
    /** 1000 milesimos = 100,0%. */
    public static final int TOTAL = 1000;

    /** Distribuicao aprovada da Etapa 4.7: 51,5 / 30 / 10 / 5 / 3 / 0,5. */
    public static final FamilyLegacyOdds DEFAULT = defaults();

    private final int[] permille;

    private FamilyLegacyOdds(int[] permille) {
        this.permille = permille;
    }

    private static FamilyLegacyOdds defaults() {
        FamilyLegacyRarity[] all = FamilyLegacyRarity.values();
        int[] values = new int[all.length];
        for (FamilyLegacyRarity rarity : all) values[rarity.ordinal()] = rarity.defaultPermille();
        return of(values);
    }

    /**
     * Distribuicao em milesimos, na ordem de {@link FamilyLegacyRarity}. Lanca {@link IllegalArgumentException} se
     * faltar raridade, houver valor negativo ou a soma nao for exatamente {@link #TOTAL}.
     */
    public static FamilyLegacyOdds of(int... permille) {
        String error = validate(permille);
        if (error != null) throw new IllegalArgumentException(error);
        return new FamilyLegacyOdds(permille.clone());
    }

    /**
     * Distribuicao em porcentagens com no maximo uma casa decimal (ex.: 51.5), na ordem de
     * {@link FamilyLegacyRarity}. Mesmas regras de {@link #of}; valores com mais de uma casa sao recusados.
     */
    public static FamilyLegacyOdds ofPercent(double... percent) {
        if (percent.length != FamilyLegacyRarity.values().length) {
            throw new IllegalArgumentException("expected " + FamilyLegacyRarity.values().length + " rarities, got "
                    + percent.length);
        }
        int[] values = new int[percent.length];
        for (int i = 0; i < percent.length; i++) {
            double tenths = percent[i] * 10.0;
            long rounded = Math.round(tenths);
            if (!Double.isFinite(tenths) || Math.abs(tenths - rounded) > 1e-6) {
                throw new IllegalArgumentException(FamilyLegacyRarity.values()[i].id() + ": " + percent[i]
                        + "% has more than one decimal place");
            }
            values[i] = (int) rounded;
        }
        return of(values);
    }

    /** Null se valida; senao, a mensagem do problema. */
    public static String validate(int[] permille) {
        FamilyLegacyRarity[] all = FamilyLegacyRarity.values();
        if (permille == null || permille.length != all.length) {
            return "expected " + all.length + " rarities, got " + (permille == null ? 0 : permille.length);
        }
        long total = 0;
        for (int i = 0; i < permille.length; i++) {
            if (permille[i] < 0) return all[i].id() + ": negative chance";
            total += permille[i];
        }
        if (total != TOTAL) return "chances add up to " + formatPercent(total) + ", expected 100.0%";
        return null;
    }

    public int permille(FamilyLegacyRarity rarity) { return permille[rarity.ordinal()]; }

    public double percent(FamilyLegacyRarity rarity) { return permille(rarity) / 10.0; }

    /** Raridade de um bilhete de 0 a {@code TOTAL - 1}. */
    public FamilyLegacyRarity rarityAt(int ticket) {
        if (ticket < 0 || ticket >= TOTAL) throw new IllegalArgumentException("ticket out of range: " + ticket);
        int remaining = ticket;
        for (FamilyLegacyRarity rarity : FamilyLegacyRarity.values()) {
            remaining -= permille(rarity);
            if (remaining < 0) return rarity;
        }
        throw new IllegalStateException("unreachable: odds add up to " + TOTAL);
    }

    /** Sorteia a raridade (o servidor chama; o cliente nunca sorteia). */
    public FamilyLegacyRarity roll(RandomGenerator random) {
        return rarityAt(random.nextInt(TOTAL));
    }

    public static String formatPercent(long permille) {
        return (permille / 10) + "." + Math.abs(permille % 10) + "%";
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof FamilyLegacyOdds other && Arrays.equals(permille, other.permille);
    }

    @Override
    public int hashCode() { return Arrays.hashCode(permille); }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (FamilyLegacyRarity rarity : FamilyLegacyRarity.values()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(rarity.id()).append('=').append(formatPercent(permille(rarity)));
        }
        return sb.toString();
    }
}
