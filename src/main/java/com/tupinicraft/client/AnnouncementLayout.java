package com.tupinicraft.client;

import java.util.List;

/**
 * Cálculo puro (sem classes do Minecraft) da posição da faixa de avisos, para que ela nunca cubra
 * os painéis do HUD (canto superior direito) nem as barras de chefe (topo central).
 * <p>
 * Ordem de tentativas:
 * <ol>
 *   <li>centralizada na altura preferida, com a largura cheia;</li>
 *   <li>na mesma altura, dentro do espaço livre ao lado dos painéis (o mais perto possível do centro,
 *       quebrando o texto em mais linhas se for preciso), desde que sobre uma largura legível;</li>
 *   <li>centralizada logo abaixo dos painéis que estiverem no caminho.</li>
 * </ol>
 * Coberto por {@code RulesSelfTest}.
 */
public final class AnnouncementLayout {
    /** Topo mínimo da faixa, como antes (abaixo de uma ou duas barras de chefe). */
    public static final int MIN_TOP = 36;
    /** Distância mínima entre a faixa e qualquer obstáculo. */
    public static final int GAP = 4;
    /** Margem lateral da tela. */
    public static final int SIDE_MARGIN = 10;
    /** Abaixo disso o texto fica quebrado demais; a faixa desce para baixo dos painéis. */
    public static final int MIN_TEXT_WIDTH = 120;

    private static final int MAX_ATTEMPTS = 6;

    /** Retângulo em coordenadas da GUI: {@code left/top} inclusivos, {@code right/bottom} exclusivos. */
    public record Rect(int left, int top, int right, int bottom) {
        public int width() {
            return right - left;
        }

        public int height() {
            return bottom - top;
        }

        /** Verdadeiro se os retângulos se tocam a menos de {@code margin} px. */
        public boolean intersects(Rect other, int margin) {
            return left < other.right + margin && other.left < right + margin
                    && top < other.bottom + margin && other.top < bottom + margin;
        }
    }

    /** Tamanho da caixa (já com o padding) quando o texto é quebrado em {@code maxTextWidth}. */
    @FunctionalInterface
    public interface Measure {
        int[] boxSize(int maxTextWidth);
    }

    /** Resultado: onde desenhar a caixa e com que largura máxima quebrar o texto. */
    public record Placement(Rect box, int maxTextWidth) {
    }

    private AnnouncementLayout() {
    }

    /**
     * @param guiWidth      largura escalada da GUI
     * @param guiHeight     altura escalada da GUI
     * @param bossBarBottom y logo abaixo da última barra de chefe desenhada (0 se não houver)
     * @param obstacles     áreas que a faixa não pode cobrir (painéis do HUD)
     * @param fullTextWidth largura de texto desejada quando há espaço
     * @param padding       padding horizontal da caixa (de cada lado)
     */
    public static Placement place(int guiWidth, int guiHeight, int bossBarBottom, List<Rect> obstacles,
                                  int fullTextWidth, int padding, Measure measure) {
        int y = preferredTop(guiHeight, bossBarBottom);
        int fullWidth = Math.max(1, Math.min(fullTextWidth, guiWidth - SIDE_MARGIN * 2 - padding * 2));

        // 1 e 2: mesma altura, encolhendo a região livre até estabilizar.
        int regionLeft = SIDE_MARGIN;
        int regionRight = guiWidth - SIDE_MARGIN;
        int maxText = fullWidth;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            Rect box = boxAt(guiWidth, regionLeft, regionRight, y, measure.boxSize(maxText));
            if (!collides(box, obstacles)) {
                return new Placement(box, maxText);
            }
            int center = guiWidth / 2;
            for (Rect obstacle : obstacles) {
                if (obstacle.top < box.bottom + GAP && box.top < obstacle.bottom + GAP) {
                    if ((obstacle.left + obstacle.right) / 2 >= center) {
                        regionRight = Math.min(regionRight, obstacle.left - GAP);
                    } else {
                        regionLeft = Math.max(regionLeft, obstacle.right + GAP);
                    }
                }
            }
            int available = regionRight - regionLeft - padding * 2;
            if (available < MIN_TEXT_WIDTH) {
                break;
            }
            maxText = Math.min(fullWidth, available);
        }

        // 3: centralizada abaixo de tudo que estiver no caminho.
        int[] size = measure.boxSize(fullWidth);
        Rect box = boxAt(guiWidth, SIDE_MARGIN, guiWidth - SIDE_MARGIN, y, size);
        for (int guard = 0; guard <= obstacles.size(); guard++) {
            int lowest = -1;
            for (Rect obstacle : obstacles) {
                if (box.intersects(obstacle, GAP)) {
                    lowest = Math.max(lowest, obstacle.bottom);
                }
            }
            if (lowest < 0) {
                break;
            }
            box = boxAt(guiWidth, SIDE_MARGIN, guiWidth - SIDE_MARGIN, lowest + GAP, size);
        }
        return new Placement(box, fullWidth);
    }

    /** Altura preferida: como antes (1/6 da tela, no mínimo 36), mas sempre abaixo das barras de chefe. */
    public static int preferredTop(int guiHeight, int bossBarBottom) {
        int y = Math.max(MIN_TOP, guiHeight / 6);
        return bossBarBottom > 0 ? Math.max(y, bossBarBottom + GAP) : y;
    }

    /** Caixa o mais perto possível do centro da tela, sem sair de [regionLeft, regionRight]. */
    private static Rect boxAt(int guiWidth, int regionLeft, int regionRight, int y, int[] size) {
        int width = size[0];
        int x = (guiWidth - width) / 2;
        x = Math.min(x, regionRight - width);
        x = Math.max(x, regionLeft);
        return new Rect(x, y, x + width, y + size[1]);
    }

    private static boolean collides(Rect box, List<Rect> obstacles) {
        for (Rect obstacle : obstacles) {
            if (box.intersects(obstacle, GAP)) {
                return true;
            }
        }
        return false;
    }
}
