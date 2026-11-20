package com.respectcraft.client.roulette;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.respectcraft.client.ui.Ui;
import com.respectcraft.profile.RouletteLayout;
import com.respectcraft.profile.SpecialityRarity;
import java.util.function.IntUnaryOperator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * Desenho da roda de premios da roleta de nascimento (so visual): fatias na cor da propria raridade com degrade do
 * miolo escuro ate a borda clara, luz fixa de cima/esquerda (o brilho fica parado enquanto a roda gira), divisorias
 * douradas com pinos, aro de metal com lampadas, miolo com joia e ponteiro tipo "lingueta" que bate nos pinos.
 * A fatia vencedora acende, ganha contorno e as outras escurecem.
 *
 * <p>Nada aqui le ou muda o sorteio: angulo, raridades e fatias iguais vem de {@link RouletteLayout} e da
 * {@code RouletteMachine}. Usado pela janela holografica ({@code RouletteView}) e pela tela de reserva
 * ({@link BirthRouletteScreen}); cada casca escreve os textos das fatias pelo {@link Labeler}.
 *
 * <p>Geometria com cor por vertice no tipo de render da GUI (sem textura nova, sem framebuffer). Um objeto por
 * casca: guarda so animacao visual (destaque do vencedor, relogio).
 */
public final class PrizeWheel {
    /** Texto de uma fatia: a casca desenha nome e chance centrados em (x, y), na cor e sombra indicadas. */
    public interface Labeler {
        void label(SpecialityRarity rarity, float x, float y, int textColor, boolean shadow);
    }

    /** Estado visual do quadro. */
    public enum Mode { IDLE, SPINNING, WINNER }

    private static final int INK = 0xFF06080A;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int BLACK = 0xFF000000;
    private static final int GOLD_HI = 0xFFFFF0B8;
    private static final int GOLD = 0xFFE0B04A;
    private static final int GOLD_LO = 0xFF7A5414;
    private static final int GOLD_DEEP = 0xFF3A2808;
    private static final int BULB = 0xFFFFF1C8;
    /** Direcao da luz fixa (graus, 0 = topo, sentido horario): cima/esquerda. */
    private static final float LIGHT = 315F;
    private static final int BULBS = RouletteLayout.SLICES * 3;
    /** Lampadas por volta, aro e miolo, em fracao do raio das fatias. */
    private static final float BEZEL = 0.095F, HUB = 0.2F;

    private float focus;
    private float lastTime = Float.NaN;

    /**
     * Desenha a roda inteira.
     *
     * @param tint     opacidade da casca aplicada a cada cor (holograma: revelacao do canvas; tela: identidade)
     * @param r        raio externo (aro incluso); as fatias ficam por dentro
     * @param rotation rotacao da roda em graus ({@code RouletteMachine.angle})
     * @param time     relogio em segundos (pulsos e lampadas)
     * @param winner   raridade sob o ponteiro quando o resultado esta exposto, ou {@code null}
     * @param boner    efeito especial da raridade BONER em curso
     * @param motion   0..1: intensidade das animacoes (0 = parado, so cores)
     */
    public void draw(GuiGraphics g, IntUnaryOperator tint, float cx, float cy, float r, float rotation, float time,
                     Mode mode, SpecialityRarity winner, boolean boner, float motion, Labeler labeler) {
        float dt = Float.isNaN(lastTime) ? 0F : Mth.clamp(time - lastTime, 0F, 0.25F);
        lastTime = time;
        float want = mode == Mode.WINNER && winner != null ? 1F : 0F;
        focus = motion <= 0F ? want : Mth.approach(focus, want, dt * 2.4F);
        float t = time * motion;

        float rs = r / (1F + BEZEL);
        Painter p = new Painter(g, tint);
        // sombra do conjunto e aro de metal
        p.ring(cx, cy, r, r + 6F, 0, 360, Ui.alpha(BLACK, 0.45F), Ui.alpha(BLACK, 0F));
        bezel(p, cx, cy, rs, r);
        // fatias
        int win = winner == null ? -1 : winner.ordinal();
        for (SpecialityRarity rarity : SpecialityRarity.values()) slice(p, cx, cy, rs, rotation, rarity, t, win, boner);
        sheen(p, cx, cy, rs);
        // contorno da vencedora por baixo das divisorias
        if (win >= 0 && focus > 0.01F) winnerOutline(p, cx, cy, rs, rotation, winner, t);
        dividers(p, cx, cy, rs, rotation);
        bulbs(p, cx, cy, rs, r, rotation, t, mode, winner, boner);
        g.flush();

        // textos: na cor que contrasta com a fatia no raio do texto
        for (SpecialityRarity rarity : SpecialityRarity.values()) {
            float mid = rotation + RouletteLayout.sliceStart(rarity) + RouletteLayout.SLICE / 2F;
            float lr = rs * 0.62F;
            float a = rad(mid);
            int bg = sliceColor(rarity, 0.62F, 0.5F, abs(mid), t, win, boner);
            int text = readableOn(bg);
            boolean shadow = text == WHITE;
            if (win >= 0 && rarity.ordinal() != win) text = Ui.lerpColor(text, bg, 0.25F * focus);
            labeler.label(rarity, cx + Mth.cos(a) * lr, cy + Mth.sin(a) * lr, text, shadow);
        }
        g.flush();

        p = new Painter(g, tint);
        hub(p, cx, cy, rs * HUB, rotation, winner, boner, t);
        pointer(p, cx, cy, rs, r, rotation, mode, winner, t);
        g.flush();
    }

    // ================================================================ partes

    private static void bezel(Painter p, float cx, float cy, float rs, float r) {
        float w = r - rs;
        // faixas do aro (de dentro pra fora): fenda escura, metal subindo, metal descendo, contorno
        float[] radii = {rs - 0.6F, rs + w * 0.14F, rs + w * 0.5F, rs + w * 0.86F, r};
        int[] colors = {INK, GOLD_LO, GOLD_HI, GOLD_LO, INK};
        int steps = 90;
        for (int i = 0; i < steps; i++) {
            float a0 = 360F * i / steps, a1 = 360F * (i + 1) / steps;
            float l0 = lightAt(a0), l1 = lightAt(a1);
            for (int k = 0; k < radii.length - 1; k++) {
                int ci0 = shade(colors[k], l0), co0 = shade(colors[k + 1], l0);
                int ci1 = shade(colors[k], l1), co1 = shade(colors[k + 1], l1);
                p.band(cx, cy, radii[k], radii[k + 1], a0, a1, ci0, co0, ci1, co1);
            }
        }
    }

    /** Fatia em malha (anel x passo de angulo) com cor por vertice. */
    private void slice(Painter p, float cx, float cy, float rs, float rotation, SpecialityRarity rarity, float t,
                       int win, boolean boner) {
        float start = rotation + RouletteLayout.sliceStart(rarity);
        float[] ts = {0F, 0.3F, 0.55F, 0.75F, 0.88F, 0.95F, 1F};
        int steps = 12;
        int[] prev = new int[ts.length];
        for (int i = 0; i <= steps; i++) {
            float u = i / (float) steps;
            float ang = start + RouletteLayout.SLICE * u;
            int[] cur = new int[ts.length];
            for (int k = 0; k < ts.length; k++) cur[k] = sliceColor(rarity, ts[k], u, abs(ang), t, win, boner);
            if (i > 0) {
                float a0 = start + RouletteLayout.SLICE * (i - 1) / steps;
                for (int k = 0; k < ts.length - 1; k++) {
                    p.band(cx, cy, rs * ts[k], rs * ts[k + 1], a0, ang, prev[k], prev[k + 1], cur[k], cur[k + 1]);
                }
            }
            prev = cur;
        }
        if (rarity == SpecialityRarity.BONER) sparkles(p, cx, cy, rs, start, t, boner);
    }

    /**
     * Cor da fatia no raio relativo {@code tr} (0 = centro, 1 = borda), posicao {@code u} dentro da fatia (0..1) e
     * angulo absoluto na tela (luz fixa).
     */
    private int sliceColor(SpecialityRarity rarity, float tr, float u, float absAngle, float t, int win, boolean boner) {
        int base = rarity.color();
        int deep, lip;
        switch (rarity) {
            case LEGENDARY -> { deep = 0xFF6A3A08; lip = 0xFFFFF2B8; }
            case BONER -> { deep = 0xFF4A0838; lip = 0xFFFFC6E6; }
            case COMMON -> { deep = 0xFF3A4048; lip = 0xFFF2F5F8; }
            default -> { deep = Ui.lerpColor(base, BLACK, 0.66F); lip = Ui.lerpColor(base, WHITE, 0.4F); }
        }
        int col;
        if (tr < 0.88F) col = Ui.lerpColor(deep, base, smooth(tr / 0.88F));
        else if (tr < 0.95F) col = Ui.lerpColor(base, lip, (tr - 0.88F) / 0.07F);
        else col = Ui.lerpColor(lip, Ui.lerpColor(base, BLACK, 0.25F), (tr - 0.95F) / 0.05F);
        // "almofada": bordas da fatia um pouco mais escuras que o meio
        float e = 2F * u - 1F;
        col = Ui.lerpColor(col, BLACK, 0.22F * e * e);
        // luz fixa: reflexo largo na parte de cima/esquerda da roda
        float spec = (float) Math.pow(Math.max(0F, toLight(absAngle)), 6) * bell(tr, 0.7F, 0.28F);
        col = Ui.lerpColor(col, WHITE, 0.3F * spec);
        // brilho que corre do centro pra borda: Legendary suave, BONER forte (e mais forte no efeito especial)
        if (rarity == SpecialityRarity.LEGENDARY || rarity == SpecialityRarity.BONER) {
            float speed = rarity == SpecialityRarity.BONER ? (boner ? 1.4F : 0.55F) : 0.35F;
            float phase = frac(t * speed + u * 0.35F) * 1.4F - 0.2F;
            float band = (float) Math.exp(-sq(tr - phase) / 0.008F);
            float amount = rarity == SpecialityRarity.BONER ? (boner ? 0.6F : 0.4F) : 0.28F;
            col = Ui.lerpColor(col, rarity == SpecialityRarity.BONER ? 0xFFFFE0F0 : 0xFFFFF8DC, amount * band);
            if (rarity == SpecialityRarity.BONER) col = Ui.lerpColor(col, 0xFFB65CFF, 0.18F * (1F - tr) * (0.5F + 0.5F * Mth.sin(t * 2F)));
        }
        if (win >= 0 && focus > 0F) {
            if (rarity.ordinal() == win) col = Ui.lerpColor(col, WHITE, focus * (0.1F + 0.08F * Mth.sin(t * 6F)));
            else col = Ui.lerpColor(col, BLACK, focus * 0.4F);
        }
        return col;
    }

    /** Reflexo de vidro parado sobre a roda (meia-lua em cima/esquerda). */
    private static void sheen(Painter p, float cx, float cy, float rs) {
        float from = 240F, to = 350F;
        float[] ts = {0.5F, 0.72F, 0.93F};
        int steps = 18;
        for (int i = 0; i < steps; i++) {
            float u0 = i / (float) steps, u1 = (i + 1) / (float) steps;
            float a0 = from + (to - from) * u0, a1 = from + (to - from) * u1;
            float s0 = Mth.sin(Mth.PI * u0), s1 = Mth.sin(Mth.PI * u1);
            int c0 = Ui.alpha(WHITE, 0.11F * s0), c1 = Ui.alpha(WHITE, 0.11F * s1);
            int z = Ui.alpha(WHITE, 0F);
            p.band(cx, cy, rs * ts[0], rs * ts[1], a0, a1, z, c0, z, c1);
            p.band(cx, cy, rs * ts[1], rs * ts[2], a0, a1, c0, z, c1, z);
        }
    }

    private static void dividers(Painter p, float cx, float cy, float rs, float rotation) {
        float hub = rs * HUB;
        for (int s = 0; s < RouletteLayout.SLICES; s++) {
            float a = rad(rotation + s * RouletteLayout.SLICE);
            float cos = Mth.cos(a), sin = Mth.sin(a);
            float x0 = cx + cos * hub, y0 = cy + sin * hub, x1 = cx + cos * (rs - 0.5F), y1 = cy + sin * (rs - 0.5F);
            p.line(x0, y0, x1, y1, 2.2F, Ui.alpha(INK, 0.75F), Ui.alpha(INK, 0.95F));
            p.line(x0, y0, x1, y1, 0.7F, Ui.alpha(GOLD_HI, 0.35F), Ui.alpha(GOLD_HI, 0.95F));
            // pino na ponta (o ponteiro bate nele)
            float px = cx + cos * (rs - 3.4F), py = cy + sin * (rs - 3.4F);
            p.disc(px, py, 2.1F, INK, INK);
            p.discLit(px, py, 1.55F, GOLD_HI, GOLD_LO);
            p.disc(px - 0.45F, py - 0.45F, 0.5F, Ui.alpha(WHITE, 0.9F), Ui.alpha(WHITE, 0.3F));
        }
    }

    private void winnerOutline(Painter p, float cx, float cy, float rs, float rotation, SpecialityRarity winner, float t) {
        float start = rotation + RouletteLayout.sliceStart(winner), end = start + RouletteLayout.SLICE;
        float pulse = focus * (0.75F + 0.25F * Mth.sin(t * 6F));
        int hot = Ui.lerpColor(winner.color(), WHITE, 0.55F);
        // luz por dentro da borda externa e das duas divisorias
        p.ring(cx, cy, rs - 7F, rs - 0.5F, start, end, Ui.alpha(hot, 0F), Ui.alpha(hot, 0.65F * pulse));
        for (float edge : new float[] {start, end}) {
            float a = rad(edge);
            float x0 = cx + Mth.cos(a) * rs * HUB, y0 = cy + Mth.sin(a) * rs * HUB;
            float x1 = cx + Mth.cos(a) * rs, y1 = cy + Mth.sin(a) * rs;
            p.line(x0, y0, x1, y1, 5F, Ui.alpha(hot, 0.1F * pulse), Ui.alpha(hot, 0.45F * pulse));
        }
        p.ring(cx, cy, rs - 1.6F, rs - 0.4F, start, end, Ui.alpha(WHITE, 0.9F * pulse), Ui.alpha(WHITE, 0.9F * pulse));
    }

    /** Lampadas fixas no aro: brilho lento parado, perseguicao no giro, pisca na cor da vencedora. */
    private void bulbs(Painter p, float cx, float cy, float rs, float r, float rotation, float t, Mode mode,
                       SpecialityRarity winner, boolean boner) {
        float br = (r - rs) * 0.5F + rs;
        float size = Math.max(1.2F, (r - rs) * 0.28F);
        int chase = Math.floorMod((int) Math.floor(rotation / (360F / BULBS)), 3);
        for (int i = 0; i < BULBS; i++) {
            float ang = 360F * (i + 0.5F) / BULBS;
            float a = rad(ang);
            float x = cx + Mth.cos(a) * br, y = cy + Mth.sin(a) * br;
            float on;
            int color = BULB;
            switch (mode) {
                case SPINNING -> on = (i + chase) % 3 == 0 ? 1F : 0.18F;
                case WINNER -> {
                    float k = focus;
                    boolean even = ((int) Math.floor(t * 3F) + i) % 2 == 0;
                    on = Mth.lerp(k, 0.55F + 0.45F * Mth.sin(t * 1.5F + i * 0.6F), even ? 1F : 0.3F);
                    if (winner != null) color = Ui.lerpColor(BULB, Ui.lerpColor(winner.color(), WHITE, 0.35F), k);
                }
                default -> on = 0.55F + 0.45F * Mth.sin(t * 1.5F + i * 0.6F);
            }
            if (boner) {
                color = Ui.lerpColor(color, SpecialityRarity.BONER.color(), 0.7F);
                on = Math.max(on, ((int) Math.floor(t * 8F) + i) % 2 == 0 ? 1F : 0.4F);
            }
            p.disc(x, y, size + 0.7F, INK, INK);
            p.disc(x, y, size, Ui.lerpColor(WHITE, color, 0.4F + 0.6F * (1F - on)),
                    Ui.lerpColor(Ui.lerpColor(color, BLACK, 0.55F), color, on));
            if (on > 0.5F) p.disc(x, y, size * 3.2F, Ui.alpha(color, 0.5F * (on - 0.5F) * 2F), Ui.alpha(color, 0F));
        }
    }

    /** Faiscas da fatia BONER: (posicao na fatia, raio relativo), longe do texto do meio. */
    private static final float[][] SPARKLES = {{0.24F, 0.36F}, {0.76F, 0.42F}, {0.5F, 0.3F}, {0.3F, 0.86F}, {0.7F, 0.84F}, {0.5F, 0.9F}};

    private static void sparkles(Painter p, float cx, float cy, float rs, float start, float t, boolean boner) {
        for (int i = 0; i < SPARKLES.length; i++) {
            float tw = 0.5F + 0.5F * Mth.sin(t * (boner ? 9F : 3.2F) + i * 2.1F);
            if (tw < 0.15F) continue;
            float a = rad(start + RouletteLayout.SLICE * SPARKLES[i][0]);
            float x = cx + Mth.cos(a) * rs * SPARKLES[i][1], y = cy + Mth.sin(a) * rs * SPARKLES[i][1];
            float s = (1.4F + 1.4F * tw) * (boner ? 1.4F : 1F);
            int core = Ui.alpha(WHITE, 0.95F * tw), tip = Ui.alpha(0xFFFFC6E6, 0F);
            p.disc(x, y, s * 0.9F, Ui.alpha(0xFFFFC6E6, 0.35F * tw), Ui.alpha(0xFFFFC6E6, 0F));
            p.line(x - s, y, x, y, 0.7F, tip, core);
            p.line(x, y, x + s, y, 0.7F, core, tip);
            p.line(x, y - s, x, y, 0.7F, tip, core);
            p.line(x, y, x, y + s, 0.7F, core, tip);
        }
    }

    /** Miolo: sombra, anel de metal com rebites (giram com a roda), face de onix e joia na cor da vencedora. */
    private void hub(Painter p, float cx, float cy, float h, float rotation, SpecialityRarity winner, boolean boner, float t) {
        p.disc(cx + 1F, cy + 1.5F, h + 4F, Ui.alpha(BLACK, 0.55F), Ui.alpha(BLACK, 0F));
        p.disc(cx, cy, h + 0.8F, INK, INK);
        int steps = 48;
        for (int i = 0; i < steps; i++) {
            float a0 = 360F * i / steps, a1 = 360F * (i + 1) / steps;
            float l0 = lightAt(a0), l1 = lightAt(a1);
            p.band(cx, cy, h * 0.68F, h, a0, a1, shade(GOLD_HI, l0), shade(GOLD_LO, l0), shade(GOLD_HI, l1), shade(GOLD_LO, l1));
        }
        p.disc(cx, cy, h * 0.68F, INK, INK);
        p.discLit(cx, cy, h * 0.62F, 0xFF2E3442, 0xFF0B0E13);
        for (int s = 0; s < RouletteLayout.SLICES; s++) {
            float a = rad(rotation + (s + 0.5F) * RouletteLayout.SLICE);
            float x = cx + Mth.cos(a) * h * 0.84F, y = cy + Mth.sin(a) * h * 0.84F;
            p.discLit(x, y, Math.max(0.7F, h * 0.08F), 0xFFFFF8E0, GOLD_DEEP);
        }
        int gem = GOLD;
        if (winner != null) gem = Ui.lerpColor(gem, winner.color(), focus);
        if (boner) gem = Ui.lerpColor(SpecialityRarity.BONER.color(), WHITE, 0.15F + 0.15F * Mth.sin(t * 10F));
        float gr = h * 0.4F;
        p.disc(cx, cy, gr * 2.4F, Ui.alpha(gem, 0.3F + 0.25F * focus), Ui.alpha(gem, 0F));
        p.discLit(cx, cy, gr, Ui.lerpColor(gem, WHITE, 0.55F), Ui.lerpColor(gem, BLACK, 0.55F));
        p.disc(cx - gr * 0.35F, cy - gr * 0.38F, gr * 0.3F, Ui.alpha(WHITE, 0.95F), Ui.alpha(WHITE, 0.2F));
    }

    /**
     * Ponteiro tipo lingueta: cabeca redonda com joia em cima do aro e ponta que entra na roda. Inclina quando um pino
     * passa (a roda gira no sentido horario: o pino empurra a ponta pra direita e solta).
     */
    private void pointer(Painter p, float cx, float cy, float rs, float r, float rotation, Mode mode,
                         SpecialityRarity winner, float t) {
        float px = cx, py = cy - r - 1F;
        float scale = r / 92F;
        float since = positiveModulo(rotation, RouletteLayout.SLICE);
        float push = 5F, back = 6F;
        float bump = since < push ? since / push : since < push + back ? 1F - (since - push) / back : 0F;
        float tilt = -20F * smooth(bump);

        GuiGraphics g = p.g;
        g.pose().pushPose();
        g.pose().translate(px, py, 0F);
        g.pose().scale(scale, scale, 1F);
        g.pose().mulPose(Axis.ZP.rotationDegrees(tilt));
        Painter q = new Painter(g, p.tint);
        float head = 7F, sh = 6.6F, tipY = 12F + (r - rs) / scale;
        // sombra, contorno e corpo (lado da luz claro, lado da sombra escuro, aresta no meio)
        blade(q, 1.6F, 2.4F, sh + 0.8F, tipY + 0.8F, head + 1.6F, Ui.alpha(BLACK, 0.4F), Ui.alpha(BLACK, 0.12F));
        blade(q, 0F, 0F, sh + 1.3F, tipY + 1.8F, head + 1.2F, INK, INK);
        q.tri(-sh, 1.5F, 0F, tipY, 0F, 0F, GOLD_HI, GOLD, 0xFFFFF6D6);
        q.tri(0F, 0F, 0F, tipY, sh, 1.5F, 0xFFB8862A, GOLD_LO, 0xFF9A6E1E);
        q.line(0F, 2F, 0F, tipY - 1.5F, 0.6F, Ui.alpha(WHITE, 0.7F), Ui.alpha(WHITE, 0.15F));
        // cabeca de metal e joia
        int steps = 40;
        for (int i = 0; i < steps; i++) {
            float a0 = 360F * i / steps, a1 = 360F * (i + 1) / steps;
            float l0 = lightAt(a0), l1 = lightAt(a1);
            q.band(0F, 0F, head * 0.55F, head, a0, a1, shade(GOLD_HI, l0), shade(GOLD_LO, l0), shade(GOLD_HI, l1), shade(GOLD_LO, l1));
        }
        int gem = winner == null ? 0xFFE8364E : Ui.lerpColor(0xFFE8364E, winner.color(), focus);
        float gr = head * 0.55F;
        q.disc(0F, 0F, gr, INK, INK);
        q.discLit(0F, 0F, gr - 0.6F, Ui.lerpColor(gem, WHITE, 0.5F), Ui.lerpColor(gem, BLACK, 0.5F));
        q.disc(-gr * 0.33F, -gr * 0.36F, gr * 0.3F, Ui.alpha(WHITE, 0.95F), Ui.alpha(WHITE, 0.15F));
        // ponta acesa quando o resultado esta exposto
        if (mode == Mode.WINNER && winner != null && focus > 0.01F) {
            int hot = Ui.lerpColor(winner.color(), WHITE, 0.4F);
            q.disc(0F, tipY - 1F, 7F, Ui.alpha(hot, 0.55F * focus), Ui.alpha(hot, 0F));
        }
        g.flush();
        g.pose().popPose();
    }

    /** Silhueta do ponteiro (cabeca redonda + lamina) deslocada, numa cor so (sombra/contorno). */
    private static void blade(Painter q, float dx, float dy, float half, float tipY, float head, int body, int tip) {
        q.tri(dx - half, dy + 1.2F, dx, dy + tipY, dx + half, dy + 1.2F, body, tip, body);
        q.disc(dx, dy, head, body, body);
    }

    // ================================================================ util

    private static float rad(float deg) { return (float) Math.toRadians(deg - 90F); }

    /** Angulo absoluto (0..360) da tela, para a luz fixa. */
    private static float abs(float deg) { return positiveModulo(deg, 360F); }

    /** Cosseno entre o angulo e a direcao da luz fixa (1 = de frente pra luz). */
    private static float toLight(float deg) { return Mth.cos((float) Math.toRadians(deg - LIGHT)); }

    /** Fator de luz 0.7..1.3 pela direcao da luz fixa. */
    private static float lightAt(float deg) { return 1F + 0.3F * toLight(deg); }

    /** Texto branco ou tinta escura, o que tiver mais contraste (WCAG) com o fundo da fatia. */
    private static int readableOn(int bg) {
        float l = linear(bg >> 16) * 0.2126F + linear(bg >> 8) * 0.7152F + linear(bg) * 0.0722F;
        float onWhite = 1.05F / (l + 0.05F), onInk = (l + 0.05F) / (0.0065F + 0.05F);
        return onWhite >= onInk ? WHITE : 0xFF101317;
    }

    private static float linear(int channel) {
        float c = (channel & 255) / 255F;
        return c <= 0.04045F ? c / 12.92F : (float) Math.pow((c + 0.055F) / 1.055F, 2.4F);
    }

    private static int shade(int argb, float light) {
        return light >= 1F ? Ui.lerpColor(argb, WHITE, (light - 1F) * 0.8F) : Ui.lerpColor(argb, BLACK, (1F - light) * 1.2F);
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0F, 1F);
        return x * x * (3F - 2F * x);
    }

    private static float bell(float x, float center, float width) { return (float) Math.exp(-sq((x - center) / width)); }

    private static float frac(float x) { return x - Mth.floor(x); }

    private static float sq(float x) { return x * x; }

    private static float positiveModulo(float value, float modulus) {
        float r = value % modulus;
        return r < 0 ? r + modulus : r;
    }

    /** Primitivas com cor por vertice no tipo de render da GUI, nas duas ordens de vertices (sem descarte de face). */
    private static final class Painter {
        final GuiGraphics g;
        final IntUnaryOperator tint;
        final VertexConsumer vc;
        final Matrix4f m;

        Painter(GuiGraphics g, IntUnaryOperator tint) {
            this.g = g;
            this.tint = tint;
            this.vc = g.bufferSource().getBuffer(RenderType.gui());
            this.m = g.pose().last().pose();
        }

        void quad(float x0, float y0, int c0, float x1, float y1, int c1, float x2, float y2, int c2, float x3, float y3, int c3) {
            c0 = tint.applyAsInt(c0);
            c1 = tint.applyAsInt(c1);
            c2 = tint.applyAsInt(c2);
            c3 = tint.applyAsInt(c3);
            if ((c0 | c1 | c2 | c3) >>> 24 == 0) return;
            vc.addVertex(m, x0, y0, 0).setColor(c0);
            vc.addVertex(m, x1, y1, 0).setColor(c1);
            vc.addVertex(m, x2, y2, 0).setColor(c2);
            vc.addVertex(m, x3, y3, 0).setColor(c3);
            vc.addVertex(m, x3, y3, 0).setColor(c3);
            vc.addVertex(m, x2, y2, 0).setColor(c2);
            vc.addVertex(m, x1, y1, 0).setColor(c1);
            vc.addVertex(m, x0, y0, 0).setColor(c0);
        }

        void tri(float x0, float y0, float x1, float y1, float x2, float y2, int c0, int c1, int c2) {
            quad(x0, y0, c0, x1, y1, c1, x2, y2, c2, x2, y2, c2);
        }

        /** Trecho de anel: raios {@code r0..r1}, angulos {@code a0..a1} (graus, 0 = topo), cor interna/externa em cada angulo. */
        void band(float cx, float cy, float r0, float r1, float a0, float a1, int in0, int out0, int in1, int out1) {
            float s0 = rad(a0), s1 = rad(a1);
            float c0 = Mth.cos(s0), n0 = Mth.sin(s0), c1 = Mth.cos(s1), n1 = Mth.sin(s1);
            quad(cx + c0 * r0, cy + n0 * r0, in0, cx + c0 * r1, cy + n0 * r1, out0,
                    cx + c1 * r1, cy + n1 * r1, out1, cx + c1 * r0, cy + n1 * r0, in1);
        }

        /** Anel com degrade radial (cor de dentro e de fora). */
        void ring(float cx, float cy, float r0, float r1, float from, float to, int in, int out) {
            int steps = Math.max(2, Math.round(Math.abs(to - from) / 6F));
            for (int i = 0; i < steps; i++) {
                band(cx, cy, r0, r1, from + (to - from) * i / steps, from + (to - from) * (i + 1) / steps, in, out, in, out);
            }
        }

        /** Disco com degrade do centro ({@code center}) para a borda ({@code edge}). */
        void disc(float cx, float cy, float r, int center, int edge) {
            int steps = Mth.clamp(Math.round(r * 2.5F), 10, 48);
            for (int i = 0; i < steps; i++) {
                float a0 = 360F * i / steps, a1 = 360F * (i + 1) / steps;
                float s0 = rad(a0), s1 = rad(a1);
                tri(cx, cy, cx + Mth.cos(s0) * r, cy + Mth.sin(s0) * r, cx + Mth.cos(s1) * r, cy + Mth.sin(s1) * r,
                        center, edge, edge);
            }
        }

        /** Disco "esferico": ponto de luz deslocado para cima/esquerda. */
        void discLit(float cx, float cy, float r, int light, int dark) {
            float lx = cx - r * 0.35F, ly = cy - r * 0.35F;
            int steps = Mth.clamp(Math.round(r * 2.5F), 10, 48);
            for (int i = 0; i < steps; i++) {
                float s0 = rad(360F * i / steps), s1 = rad(360F * (i + 1) / steps);
                tri(lx, ly, cx + Mth.cos(s0) * r, cy + Mth.sin(s0) * r, cx + Mth.cos(s1) * r, cy + Mth.sin(s1) * r,
                        light, dark, dark);
            }
        }

        /** Linha com largura e cor em cada ponta. */
        void line(float x0, float y0, float x1, float y1, float width, int c0, int c1) {
            float dx = x1 - x0, dy = y1 - y0, len = Mth.sqrt(dx * dx + dy * dy);
            if (len < 0.01F) return;
            float nx = -dy / len * width * 0.5F, ny = dx / len * width * 0.5F;
            quad(x0 + nx, y0 + ny, c0, x0 - nx, y0 - ny, c0, x1 - nx, y1 - ny, c1, x1 + nx, y1 + ny, c1);
        }
    }
}
