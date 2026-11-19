package com.respectcraft.client.holo;

import com.respectcraft.client.ui.Ui;
import net.minecraft.util.RandomSource;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Particulas do holograma: um vetor fixo (sem alocar por quadro) de pontos no espaco de layout, desenhados como
 * sprites de brilho virados para a camera num unico lote aditivo. Como vivem em profundidades diferentes, tambem
 * ganham paralaxe. So no cliente; nada de particulas do mundo.
 */
final class HoloParticles {
    static final int MAX = 260;
    private static final byte MOTE = 0, SPARK = 1, EMBER = 2;

    private final float[] x = new float[MAX], y = new float[MAX], z = new float[MAX];
    private final float[] vx = new float[MAX], vy = new float[MAX], vz = new float[MAX];
    private final float[] age = new float[MAX], life = new float[MAX], size = new float[MAX], phase = new float[MAX];
    private final int[] color = new int[MAX];
    private final byte[] kind = new byte[MAX];
    private int count;
    private final RandomSource random = RandomSource.create();
    private final Vector3f eye = new Vector3f();

    int count() { return count; }

    void clear() { count = 0; }

    private int add(byte k, float px, float py, float pz, float pvx, float pvy, float pvz, float plife, float psize, int pcolor) {
        if (count >= MAX) return -1;
        int i = count++;
        kind[i] = k;
        x[i] = px; y[i] = py; z[i] = pz;
        vx[i] = pvx; vy[i] = pvy; vz[i] = pvz;
        age[i] = 0F;
        life[i] = plife;
        size[i] = psize;
        color[i] = pcolor;
        phase[i] = random.nextFloat() * 6.283F;
        return i;
    }

    /** Ponto de luz que sobe devagar no volume do menu (ambiente). */
    void mote(float minX, float maxX, float minY, float maxY, float minZ, float maxZ, int pcolor) {
        add(MOTE, minX + random.nextFloat() * (maxX - minX), minY + random.nextFloat() * (maxY - minY),
                minZ + random.nextFloat() * (maxZ - minZ), (random.nextFloat() - 0.5F) * 3F, -4F - random.nextFloat() * 9F,
                (random.nextFloat() - 0.5F) * 2F, 2.6F + random.nextFloat() * 3.4F, 1.2F + random.nextFloat() * 1.8F, pcolor);
    }

    /** Faiscas espalhadas a partir de um ponto (selecao, compra, montagem). */
    void burst(float px, float py, float pz, int n, int pcolor, float speed) {
        for (int i = 0; i < n; i++) {
            float a = random.nextFloat() * 6.283F, e = (random.nextFloat() - 0.5F) * 1.6F;
            float v = speed * (0.35F + random.nextFloat() * 0.85F);
            add(SPARK, px, py, pz, (float) Math.cos(a) * v, (float) Math.sin(a) * v - speed * 0.2F, e * v * 0.6F,
                    0.35F + random.nextFloat() * 0.55F, 1.1F + random.nextFloat() * 1.3F,
                    random.nextFloat() < 0.3F ? 0xFFFFFFFF : pcolor);
        }
    }

    /** Brasa curta e parada (rastro de linhas, contorno se dissolvendo). */
    void ember(float px, float py, float pz, int pcolor, float drift) {
        add(EMBER, px, py, pz, (random.nextFloat() - 0.5F) * drift, (random.nextFloat() - 0.5F) * drift - drift * 0.3F,
                (random.nextFloat() - 0.5F) * drift, 0.25F + random.nextFloat() * 0.45F, 1.0F + random.nextFloat() * 1.2F, pcolor);
    }

    /** Faiscas ao longo do contorno de um painel (montagem e dissolucao). */
    void alongEdges(HoloPanel panel, int n, int pcolor, float drift) {
        Vector3f p = new Vector3f();
        float per = 2F * (panel.w + panel.h);
        for (int i = 0; i < n; i++) {
            float d = random.nextFloat() * per;
            float lx, ly;
            if (d < panel.w) { lx = d; ly = 0; }
            else if (d < panel.w + panel.h) { lx = panel.w; ly = d - panel.w; }
            else if (d < 2 * panel.w + panel.h) { lx = d - panel.w - panel.h; ly = panel.h; }
            else { lx = 0; ly = d - 2 * panel.w - panel.h; }
            panel.toLayout(lx, ly, p);
            ember(p.x, p.y, p.z, pcolor, drift);
        }
    }

    void update(float dt) {
        for (int i = 0; i < count; i++) {
            age[i] += dt;
            if (age[i] >= life[i]) {
                remove(i);
                i--;
                continue;
            }
            switch (kind[i]) {
                case MOTE -> {
                    x[i] += (vx[i] + (float) Math.sin(age[i] * 1.7F + phase[i]) * 2.2F) * dt;
                    y[i] += vy[i] * dt;
                    z[i] += vz[i] * dt;
                }
                default -> {
                    float drag = (float) Math.exp(-3.2F * dt);
                    vx[i] *= drag;
                    vy[i] = vy[i] * drag + 30F * dt;
                    vz[i] *= drag;
                    x[i] += vx[i] * dt;
                    y[i] += vy[i] * dt;
                    z[i] += vz[i] * dt;
                }
            }
        }
    }

    private void remove(int i) {
        int last = --count;
        if (i == last) return;
        kind[i] = kind[last];
        x[i] = x[last]; y[i] = y[last]; z[i] = z[last];
        vx[i] = vx[last]; vy[i] = vy[last]; vz[i] = vz[last];
        age[i] = age[last]; life[i] = life[last]; size[i] = size[last]; phase[i] = phase[last];
        color[i] = color[last];
    }

    /** Desenha tudo num lote de brilho ja aberto ({@link Holo3D#beginGlow}). */
    void render(Holo3D r, Matrix4f viewLayout, float uiScale, float alphaScale) {
        for (int i = 0; i < count; i++) {
            float t = age[i] / life[i];
            float fade = kind[i] == MOTE ? (float) Math.sin(Math.PI * t) * 0.55F : 1F - t;
            float a = fade * alphaScale;
            if (a <= 0.01F) continue;
            viewLayout.transformPosition(eye.set(x[i], y[i], z[i]));
            float s = size[i] * uiScale;
            r.glow(eye.x, eye.y, eye.z, s * 3.2F, Ui.alpha(color[i], 0.45F * a));
            r.glow(eye.x, eye.y, eye.z, s * 1.1F, Ui.alpha(0xFFFFFFFF, 0.8F * a));
        }
    }
}
