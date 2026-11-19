package com.respectcraft.profile;

/**
 * Desenho da roda da roleta de nascimento (Etapa 4.7.1): seis fatias <b>iguais</b>, uma por raridade, na ordem do
 * ranking (Common primeiro, a partir do ponteiro, sentido horario). O tamanho da fatia e so visual e nao
 * representa a chance: as chances reais vem do servidor ({@link SpecialityOdds}) e a tela as escreve em numeros.
 * Nada aqui participa do sorteio; a roda so para na raridade que o servidor ja gravou na ficha.
 *
 * <p>Angulos em graus; rotacao 0 = inicio da primeira fatia sob o ponteiro (topo). Puro: sem classes do Minecraft
 * (conferido pelo {@code SelfTest}).
 */
public final class RouletteLayout {
    public static final int SLICES = SpecialityRarity.values().length;
    public static final float SLICE = 360F / SLICES;
    /** Folga a partir da divisoria: a roda nunca para encostada numa borda. */
    private static final float EDGE_MARGIN = SLICE * 0.2F;

    private RouletteLayout() {}

    /** Inicio da fatia da raridade (graus a partir do inicio da roda). */
    public static float sliceStart(SpecialityRarity rarity) {
        return rarity.ordinal() * SLICE;
    }

    /** Raridade sob o ponteiro quando a roda esta girada {@code rotation} graus. */
    public static SpecialityRarity rarityAtPointer(float rotation) {
        float pos = positiveModulo(-rotation, 360F);
        int index = Math.min(SLICES - 1, (int) (pos / SLICE));
        return SpecialityRarity.values()[index];
    }

    /**
     * Rotacao de repouso para mostrar {@code rarity} sob o ponteiro. O deslocamento dentro da fatia vem do
     * {@code rollCount} (deterministico: reabrir a tela mostra a roda no mesmo lugar) e fica longe das divisorias.
     */
    public static float restingRotation(SpecialityRarity rarity, int rollCount) {
        int h = Math.floorMod(rollCount * 0x9E3779B1, 1000);
        float inside = EDGE_MARGIN + (SLICE - 2F * EDGE_MARGIN) * (h / 999F);
        return -(sliceStart(rarity) + inside);
    }

    private static float positiveModulo(float value, float modulus) {
        float r = value % modulus;
        return r < 0 ? r + modulus : r;
    }
}
