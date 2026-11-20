package com.respectcraft.client.holo;

/**
 * Regiao interativa registrada durante o desenho de um painel (coordenadas locais do painel). O clique usa a lista
 * do ultimo quadro desenhado, entao o que o jogador ve e exatamente o que responde ao cursor.
 */
final class HoloZone {
    /** Arrasto capturado: continua recebendo o cursor (no plano do painel) ate soltar, mesmo fora da regiao. */
    interface Drag {
        void press(float x, float y);
        void move(float x, float y);
        /** {@code click} = soltou quase sem mover. */
        void release(float x, float y, boolean click);
    }

    float x, y, w, h;
    Runnable click;
    Drag drag;
    /** Roda sobre a regiao (Etapa 4.8.4-C: seletor do criador troca o valor); recebe o passo da roda. */
    java.util.function.DoubleConsumer scroll;
    boolean enabled;
    float pitch;

    HoloZone set(float x, float y, float w, float h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.click = null;
        this.drag = null;
        this.scroll = null;
        this.enabled = true;
        this.pitch = 1F;
        return this;
    }

    boolean contains(float px, float py) {
        return px >= x && py >= y && px < x + w && py < y + h;
    }
}
