package com.respectcraft.client.ui;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/** VFX 2D das telas: faiscas, aneis que se expandem e clarões. Atualizado por tick da tela. */
public final class UiParticles {
    private enum Kind { SPARK, RING, FLASH }

    private static final class P {
        Kind kind;
        float x, y, vx, vy, size, prevX, prevY;
        int age, life, color;
    }

    private final List<P> particles = new ArrayList<>();
    private final RandomSource random = RandomSource.create();

    /** Explosao de faiscas em volta de (x, y). */
    public void burst(float x, float y, int count, int color, float speed) {
        for (int i = 0; i < count; i++) {
            P p = new P();
            p.kind = Kind.SPARK;
            float angle = random.nextFloat() * Mth.TWO_PI;
            float v = speed * (0.4F + random.nextFloat() * 0.8F);
            p.x = p.prevX = x;
            p.y = p.prevY = y;
            p.vx = Mth.cos(angle) * v;
            p.vy = Mth.sin(angle) * v - speed * 0.3F;
            p.size = 1 + random.nextInt(2);
            p.life = 12 + random.nextInt(14);
            p.color = random.nextFloat() < 0.3F ? 0xFFFFFFFF : color;
            particles.add(p);
        }
    }

    public void ring(float x, float y, float maxRadius, int color, int life) {
        P p = new P();
        p.kind = Kind.RING;
        p.x = p.prevX = x;
        p.y = p.prevY = y;
        p.size = maxRadius;
        p.life = life;
        p.color = color;
        particles.add(p);
    }

    public void flash(float x, float y, float radius, int color, int life) {
        P p = new P();
        p.kind = Kind.FLASH;
        p.x = p.prevX = x;
        p.y = p.prevY = y;
        p.size = radius;
        p.life = life;
        p.color = color;
        particles.add(p);
    }

    /** Faisca isolada subindo devagar (ambiente). */
    public void ember(float x, float y, int color) {
        P p = new P();
        p.kind = Kind.SPARK;
        p.x = p.prevX = x;
        p.y = p.prevY = y;
        p.vx = (random.nextFloat() - 0.5F) * 0.4F;
        p.vy = -0.3F - random.nextFloat() * 0.5F;
        p.size = 1;
        p.life = 30 + random.nextInt(30);
        p.color = color;
        particles.add(p);
    }

    public void tick() {
        Iterator<P> it = particles.iterator();
        while (it.hasNext()) {
            P p = it.next();
            p.prevX = p.x;
            p.prevY = p.y;
            p.x += p.vx;
            p.y += p.vy;
            p.vx *= 0.9F;
            p.vy = p.vy * 0.9F + 0.08F;
            if (++p.age >= p.life) it.remove();
        }
    }

    public boolean isEmpty() { return particles.isEmpty(); }

    public void render(GuiGraphics g, float partialTick) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        for (P p : particles) {
            float t = (p.age + partialTick) / p.life;
            float fade = 1F - Mth.clamp(t, 0F, 1F);
            float x = Mth.lerp(partialTick, p.prevX, p.x);
            float y = Mth.lerp(partialTick, p.prevY, p.y);
            switch (p.kind) {
                case SPARK -> {
                    int s = Math.max(1, Math.round(p.size));
                    g.fill(Math.round(x), Math.round(y), Math.round(x) + s, Math.round(y) + s, Ui.alpha(p.color, fade));
                    Ui.glow(g, x, y, 4 + p.size * 2, Ui.alpha(p.color, fade * 0.35F));
                }
                case RING -> {
                    float r = p.size * (1 - (1 - t) * (1 - t));
                    int segments = 28;
                    for (int i = 0; i < segments; i++) {
                        float a0 = i * Mth.TWO_PI / segments, a1 = (i + 1) * Mth.TWO_PI / segments;
                        Ui.line(g, x + Mth.cos(a0) * r, y + Mth.sin(a0) * r, x + Mth.cos(a1) * r, y + Mth.sin(a1) * r,
                                2, Ui.alpha(p.color, fade));
                    }
                }
                case FLASH -> Ui.glow(g, x, y, p.size * (0.6F + 0.4F * t), Ui.alpha(p.color, fade * 0.9F));
            }
        }
        g.pose().popPose();
    }
}
