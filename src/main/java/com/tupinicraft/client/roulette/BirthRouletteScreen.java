package com.tupinicraft.client.roulette;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tupinicraft.client.character.CharacterHubScreen;
import com.tupinicraft.client.ui.TupiButton;
import com.tupinicraft.client.ui.TupiScreen;
import com.tupinicraft.client.ui.Ui;
import com.tupinicraft.network.GameplayNetworking;
import com.tupinicraft.network.ProfileClientCache;
import com.tupinicraft.profile.BirthOrigin;
import com.tupinicraft.profile.CharacterAttribute;
import com.tupinicraft.profile.CharacterProfile;
import com.tupinicraft.registry.ModSounds;
import com.tupinicraft.skill.SkillDef;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;

/**
 * Roleta de nascimento. O sorteio e do servidor: a tela gira enquanto espera a resposta e depois desacelera ate
 * parar na origem sorteada, revela os atributos um a um, com som e VFX. Fatias proporcionais as chances.
 */
public final class BirthRouletteScreen extends TupiScreen {
    private static final String KEY = "gui.tupinicraft.roulette.";
    private enum Phase { IDLE, WAITING, SPINNING, REVEAL, DONE }

    private Phase phase;
    private float angle;       // graus; 0 = inicio da primeira fatia no ponteiro (topo)
    private float prevAngle;
    private float speed;       // graus por tick durante a espera
    private float spinFrom, spinTo;
    private int spinTick, spinLength;
    private int revealTick;
    private int lastRollCount;
    private int waitTicks;
    private int cx, cy, radius;
    private TupiButton spinButton, continueButton;

    public BirthRouletteScreen() {
        super(Component.translatable(KEY + "title"));
        CharacterProfile p = ProfileClientCache.profile();
        lastRollCount = p.rollCount();
        if (p.born() && p.originOrNull() != null) {
            phase = Phase.DONE;
            angle = prevAngle = restingAngle(p.originOrNull());
            revealTick = 999;
        } else {
            phase = Phase.IDLE;
        }
    }

    @Override
    protected void init() {
        layoutPanel(400, 240);
        radius = Math.min(78, (panelHeight - 70) / 2);
        cx = left + 16 + radius;
        cy = top + 30 + radius + 4;
        int bx = left + panelWidth - 130;
        spinButton = addRenderableWidget(new TupiButton(bx, top + panelHeight - 54, 120, 20, spinLabel(), b -> spin())
                .item(new ItemStack(Items.EMERALD)).accent(Ui.GREEN));
        continueButton = addRenderableWidget(new TupiButton(bx, top + panelHeight - 30, 120, 20,
                Component.translatable(KEY + "continue"), b -> minecraft.setScreen(new CharacterHubScreen())));
        updateButtons();
    }

    private Component spinLabel() {
        CharacterProfile p = ProfileClientCache.profile();
        return p.born() ? Component.translatable(KEY + "reroll", p.rerolls()) : Component.translatable(KEY + "spin");
    }

    private void updateButtons() {
        CharacterProfile p = ProfileClientCache.profile();
        boolean busy = phase == Phase.WAITING || phase == Phase.SPINNING || phase == Phase.REVEAL;
        spinButton.active = !busy && (!p.born() || p.rerolls() > 0);
        spinButton.setMessage(spinLabel());
        continueButton.active = !busy && p.born();
    }

    private void spin() {
        CharacterProfile p = ProfileClientCache.profile();
        if (phase == Phase.WAITING || phase == Phase.SPINNING) return;
        lastRollCount = p.rollCount();
        phase = Phase.WAITING;
        speed = 0;
        waitTicks = 0;
        PacketDistributor.sendToServer(new GameplayNetworking.BirthRollRequest(p.born()));
        updateButtons();
    }

    /** Angulo em que o centro da fatia da origem fica sob o ponteiro. */
    private static float restingAngle(BirthOrigin origin) {
        float total = BirthOrigin.totalWeight();
        float start = 0;
        for (BirthOrigin o : BirthOrigin.values()) {
            float size = 360F * o.weight() / total;
            if (o == origin) return -(start + size / 2F);
            start += size;
        }
        return 0;
    }

    @Override
    public void tick() {
        super.tick();
        prevAngle = angle;
        CharacterProfile p = ProfileClientCache.profile();
        switch (phase) {
            case WAITING -> {
                speed = Math.min(24F, speed + 2.5F);
                angle += speed;
                tickSound();
                if (p.rollCount() != lastRollCount && p.originOrNull() != null) {
                    // Resposta do servidor: desacelera ate a fatia sorteada (pelo menos 2 voltas a mais).
                    lastRollCount = p.rollCount();
                    float target = restingAngle(p.originOrNull());
                    float base = angle + 720F;
                    float delta = Mth.positiveModulo(target - base, 360F);
                    spinFrom = angle;
                    spinTo = base + delta;
                    spinTick = 0;
                    spinLength = 50;
                    phase = Phase.SPINNING;
                } else if (++waitTicks > 200) { // sem resposta (servidor recusou): volta ao estado real
                    phase = p.born() ? Phase.DONE : Phase.IDLE;
                    updateButtons();
                }
            }
            case SPINNING -> {
                spinTick++;
                float t = Math.min(1F, spinTick / (float) spinLength);
                float eased = 1F - (1F - t) * (1F - t) * (1F - t);
                angle = spinFrom + (spinTo - spinFrom) * eased;
                tickSound();
                if (t >= 1F) {
                    phase = Phase.REVEAL;
                    revealTick = 0;
                    BirthOrigin origin = p.originOrNull();
                    int color = origin == null ? Ui.GOLD : origin.color();
                    vfx.flash(cx, cy - radius, 50, color, 16);
                    vfx.ring(cx, cy, radius + 30, color, 20);
                    vfx.burst(cx, cy - radius, 50, color, 4F);
                    Ui.playUi(ModSounds.ROULETTE_WIN.get(), 1F, 0.8F);
                }
            }
            case REVEAL -> {
                revealTick++;
                int shown = revealTick / 6;
                if (revealTick % 6 == 0 && shown <= CharacterAttribute.values().length) {
                    Ui.playUi(ModSounds.ROULETTE_TICK.get(), 0.8F + shown * 0.1F, 0.6F);
                    if (shown > 0) {
                        CharacterAttribute a = CharacterAttribute.values()[shown - 1];
                        vfx.burst(attrX() + attrW() * p.attribute(a) / (float) CharacterAttribute.MAX,
                                attrY(shown - 1) + 11, 8, a.color(), 1.8F);
                    }
                }
                if (revealTick > 6 * (CharacterAttribute.values().length + 1)) {
                    phase = Phase.DONE;
                    updateButtons();
                }
            }
            default -> {
                if (ticks % 6 == 0 && p.born()) {
                    vfx.ember(cx + (float) (Math.random() - 0.5) * radius * 2, cy + radius, Ui.alpha(Ui.GOLD, 0.8F));
                }
            }
        }
    }

    private int lastSegment = -1;

    private void tickSound() {
        int segment = segmentAtPointer(angle);
        if (segment != lastSegment) {
            lastSegment = segment;
            Ui.playUi(ModSounds.ROULETTE_TICK.get(), 1.0F + (segment % 3) * 0.08F, 0.35F);
        }
    }

    private static int segmentAtPointer(float angle) {
        float pos = Mth.positiveModulo(-angle, 360F);
        float total = BirthOrigin.totalWeight();
        float start = 0;
        for (BirthOrigin o : BirthOrigin.values()) {
            float size = 360F * o.weight() / total;
            if (pos < start + size) return o.ordinal();
            start += size;
        }
        return 0;
    }

    private int attrX() { return cx + radius + 26; }
    private int attrW() { return left + panelWidth - 10 - attrX(); }
    private int attrY(int index) { return top + 58 + index * 18; }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        float a = Mth.lerp(partialTick, prevAngle, angle);
        drawWheel(g, a);
        CharacterProfile p = ProfileClientCache.profile();
        BirthOrigin origin = p.originOrNull();
        int x = attrX();
        boolean showResult = (phase == Phase.REVEAL || phase == Phase.DONE) && origin != null;
        if (showResult) {
            Ui.icon(g, SkillDef.iconTexture(origin.icon()), x, top + 29, 20);
            g.drawString(font, Component.translatable("origin.tupinicraft." + origin.id()), x + 24, top + 30, origin.color(), true);
            g.drawString(font, Component.translatable("origin.tupinicraft." + origin.id() + ".desc"), x + 24, top + 41, Ui.MUTED, false);
        } else {
            g.drawWordWrap(font, Component.translatable(KEY + "intro"), x, top + 32, attrW(), Ui.MUTED);
        }
        int shown = phase == Phase.DONE ? 99 : phase == Phase.REVEAL ? revealTick / 6 : 0;
        for (CharacterAttribute attr : CharacterAttribute.values()) {
            int i = attr.ordinal();
            int y = attrY(i);
            boolean visible = i < shown;
            g.drawString(font, Component.translatable("attribute.tupinicraft." + attr.id()), x, y, visible ? Ui.TEXT : 0xFF5A6570, false);
            int value = visible ? p.attribute(attr) : 0;
            if (visible && phase == Phase.REVEAL && i == shown - 1) {
                value = Math.round(p.attribute(attr) * Math.min(1F, ((revealTick % 6) + partialTick) / 5F));
            }
            String num = visible ? String.valueOf(value) : "?";
            g.drawString(font, num, x + attrW() - font.width(num), y, Ui.GOLD, false);
            Ui.bar(g, x, y + 9, attrW(), 6, value / (float) CharacterAttribute.MAX, attr.color());
            if (visible && origin != null && origin.bonus(attr) != 0) {
                String bonus = (origin.bonus(attr) > 0 ? "+" : "") + origin.bonus(attr);
                g.drawString(font, bonus, x + attrW() - font.width(num) - font.width(bonus) - 4, y,
                        origin.bonus(attr) > 0 ? Ui.GREEN : Ui.RED, false);
            }
        }
        if (phase == Phase.DONE && p.born()) {
            g.drawString(font, Component.translatable(KEY + "points", p.skillPoints()), x, attrY(6) + 2, Ui.GOLD, false);
        }
    }

    private void drawWheel(GuiGraphics g, float rotation) {
        Ui.glow(g, cx, cy, radius + 26, 0x40FFE27A);
        // Aro externo
        disc(g, cx, cy, radius + 5, 0xFF06080A);
        disc(g, cx, cy, radius + 3, 0xFFD6B84A);
        float total = BirthOrigin.totalWeight();
        float start = rotation;
        int highlighted = (phase == Phase.REVEAL || phase == Phase.DONE) ? segmentAtPointer(rotation) : -1;
        for (BirthOrigin o : BirthOrigin.values()) {
            float size = 360F * o.weight() / total;
            int color = o.color();
            if (o.ordinal() % 2 == 1) color = Ui.lerpColor(color, 0xFF000000, 0.15F);
            if (highlighted == o.ordinal()) color = Ui.lerpColor(color, 0xFFFFFFFF, 0.25F + 0.1F * Mth.sin(ticks * 0.3F));
            sector(g, cx, cy, radius, start, start + size, color);
            // icone no meio da fatia
            float mid = (float) Math.toRadians(start + size / 2F - 90F);
            int ix = Math.round(cx + Mth.cos(mid) * radius * 0.66F) - 7;
            int iy = Math.round(cy + Mth.sin(mid) * radius * 0.66F) - 7;
            if (size > 18) Ui.icon(g, SkillDef.iconTexture(o.icon()), ix, iy, 14);
            // divisoria
            float edge = (float) Math.toRadians(start - 90F);
            Ui.line(g, cx, cy, cx + Mth.cos(edge) * radius, cy + Mth.sin(edge) * radius, 1, 0xC006080A);
            start += size;
        }
        // Miolo
        disc(g, cx, cy, 13, 0xFF06080A);
        disc(g, cx, cy, 11, 0xFF1D5C3A);
        disc(g, cx, cy, 5, 0xFFFFE27A);
        // Ponteiro no topo
        int py = cy - radius - 8;
        for (int i = 0; i < 9; i++) g.fill(cx - 8 + i, py + i, cx + 9 - i, py + i + 1, i < 1 ? 0xFF06080A : 0xFFFFE27A);
        g.fill(cx - 1, py, cx + 2, py + 6, 0xFFFFFFFF);
    }

    private static void disc(GuiGraphics g, int x, int y, int r, int color) {
        sector(g, x, y, r, 0, 360, color);
    }

    /** Fatia de circulo (angulos em graus, 0 = topo, sentido horario). */
    private static void sector(GuiGraphics g, float x, float y, float r, float from, float to, int argb) {
        VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
        Matrix4f m = g.pose().last().pose();
        int steps = Math.max(2, Math.round((to - from) / 4F));
        for (int i = 0; i < steps; i++) {
            float a0 = (float) Math.toRadians(from + (to - from) * i / steps - 90F);
            float a1 = (float) Math.toRadians(from + (to - from) * (i + 1) / steps - 90F);
            float x0 = x + Mth.cos(a0) * r, y0 = y + Mth.sin(a0) * r;
            float x1 = x + Mth.cos(a1) * r, y1 = y + Mth.sin(a1) * r;
            // As duas ordens de vertices: o tipo de render da GUI pode descartar faces "de costas".
            vc.addVertex(m, x, y, 0).setColor(argb);
            vc.addVertex(m, x0, y0, 0).setColor(argb);
            vc.addVertex(m, x1, y1, 0).setColor(argb);
            vc.addVertex(m, x, y, 0).setColor(argb);
            vc.addVertex(m, x, y, 0).setColor(argb);
            vc.addVertex(m, x1, y1, 0).setColor(argb);
            vc.addVertex(m, x0, y0, 0).setColor(argb);
            vc.addVertex(m, x, y, 0).setColor(argb);
        }
        g.flush();
    }
}
