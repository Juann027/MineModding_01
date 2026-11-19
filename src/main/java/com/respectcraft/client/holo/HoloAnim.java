package com.respectcraft.client.holo;

/** Envelopes de animacao em tempo continuo (segundos), separados do desenho. */
final class HoloAnim {
    private HoloAnim() {}

    static float clamp01(float t) { return t < 0F ? 0F : t > 1F ? 1F : t; }

    /** 0..1 dentro da janela {@code [start, start + duration]}. */
    static float window(float time, float start, float duration) {
        return duration <= 0F ? (time >= start ? 1F : 0F) : clamp01((time - start) / duration);
    }

    static float smooth(float t) { t = clamp01(t); return t * t * (3F - 2F * t); }
    static float outCubic(float t) { t = clamp01(t); float u = 1F - t; return 1F - u * u * u; }
    static float inCubic(float t) { t = clamp01(t); return t * t * t; }
    static float inOutCubic(float t) {
        t = clamp01(t);
        return t < 0.5F ? 4F * t * t * t : 1F - (float) Math.pow(-2F * t + 2F, 3) / 2F;
    }

    /** Leve passada do ponto final (mola curta); {@code s} ~1.2 e discreto. */
    static float outBack(float t, float s) {
        t = clamp01(t);
        float u = t - 1F;
        return 1F + (s + 1F) * u * u * u + s * u * u;
    }

    static float lerp(float t, float a, float b) { return a + (b - a) * t; }

    /** Aproximacao exponencial independente da taxa de quadros. */
    static float approach(float current, float target, float rate, float dt) {
        return current + (target - current) * (1F - (float) Math.exp(-rate * dt));
    }

    /** Mola amortecida (posicao, velocidade). Devolve a nova posicao e grava a velocidade em {@code vel[0]}. */
    static float spring(float pos, float target, float[] vel, float stiffness, float damping, float dt) {
        float steps = Math.max(1, (int) Math.ceil(dt / 0.008F));
        float h = dt / steps;
        float v = vel[0];
        for (int i = 0; i < steps; i++) {
            float a = (target - pos) * stiffness - v * damping;
            v += a * h;
            pos += v * h;
        }
        vel[0] = v;
        return pos;
    }
}
