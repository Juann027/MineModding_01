package com.tupinicraft.client.roulette;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tupinicraft.client.character.CharacterHubScreen;
import com.tupinicraft.client.ui.TupiButton;
import com.tupinicraft.client.ui.TupiScreen;
import com.tupinicraft.client.ui.Ui;
import com.tupinicraft.network.GameplayNetworking;
import com.tupinicraft.network.ProfileClientCache;
import com.tupinicraft.profile.BirthOrigin;
import com.tupinicraft.profile.BirthRoulette;
import com.tupinicraft.profile.BirthTrait;
import com.tupinicraft.profile.CharacterAttribute;
import com.tupinicraft.profile.CharacterProfile;
import com.tupinicraft.profile.Speciality;
import com.tupinicraft.profile.SpecialityOdds;
import com.tupinicraft.profile.SpecialityRarity;
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
 * parar na raridade sorteada, mostra a Speciality (nome, raridade, descricao) e revela os atributos um a um, com
 * som e VFX. Etapa 4.7: as fatias sao as seis raridades, proporcionais a distribuicao aprovada
 * ({@link SpecialityOdds#DEFAULT}; a config do servidor so muda o sorteio, nao o desenho). Fichas antigas com
 * origem social legada mostram essa origem (com o bonus que ela deu) e param no topo da roda.
 *
 * <p>Maquina de estados (Etapa 4.5): {@code IDLE} aguardando sorteio -> {@code WAITING} pedido enviado, girando
 * -> {@code SPINNING} resposta recebida, desacelerando ate a origem -> {@code REVEAL} atributos -> {@code DONE}
 * resultado disponivel (Continuar ou o unico novo giro). O traco de nascimento aparece junto do resultado.
 * Continuar confirma o nascimento no servidor ({@link GameplayNetworking.BirthConfirmRequest}): o novo giro que
 * sobrou e descartado. A animacao nunca decide dados: o servidor responde a
 * todo pedido ({@link GameplayNetworking.BirthRollReply}) e a tela so mostra a ficha recebida. Sem resposta em
 * {@link #TIMEOUT_TICKS}, a tela volta ao estado real com um aviso; um pedido atrasado nao gera resultado
 * duplicado nem gasta o novo giro (o servidor confere o {@code rollCount} esperado).
 */
public final class BirthRouletteScreen extends TupiScreen {
    private static final String KEY = "gui.tupinicraft.roulette.";
    private static final int TIMEOUT_TICKS = 200;
    private enum Phase { IDLE, WAITING, SPINNING, REVEAL, DONE }

    private Phase phase;
    private float angle;       // graus; 0 = inicio da primeira fatia no ponteiro (topo)
    private float prevAngle;
    private float speed;       // graus por tick durante a espera
    private float spinFrom, spinTo;
    private int spinTick, spinLength;
    private boolean revealAfterSpin;
    private int revealTick;
    /** rollCount da ficha que a roda mostra (ou para a qual esta desacelerando). */
    private int shownRollCount;
    private long requestRevision;
    private int waitTicks;
    private Component status;
    private int cx, cy, radius;
    private TupiButton spinButton, continueButton;
    private boolean continueWarns;

    public BirthRouletteScreen() {
        super(Component.translatable(KEY + "title"));
        CharacterProfile p = ProfileClientCache.profile();
        shownRollCount = p.rollCount();
        if (hasResult(p)) {
            phase = Phase.DONE;
            angle = prevAngle = restingAngle(p);
            revealTick = 999;
        } else {
            phase = Phase.IDLE;
        }
    }

    private static boolean hasResult(CharacterProfile p) {
        return p.hasBirthResult();
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
                Component.translatable(KEY + "continue"), b -> confirm()));
        continueWarns = false;
        updateButtons();
    }

    private Component spinLabel() {
        CharacterProfile p = ProfileClientCache.profile();
        return p.born() ? Component.translatable(KEY + "reroll", p.rerolls()) : Component.translatable(KEY + "spin");
    }

    private boolean busy() {
        return phase == Phase.WAITING || phase == Phase.SPINNING || phase == Phase.REVEAL;
    }

    /** Botoes sempre derivados da fase e da ficha atual (chamado a cada tick: nunca ficam presos). */
    private void updateButtons() {
        if (spinButton == null) return;
        CharacterProfile p = ProfileClientCache.profile();
        boolean idle = !busy();
        spinButton.active = idle && (!p.born() || p.canReroll());
        spinButton.setMessage(spinLabel());
        continueButton.active = idle && phase == Phase.DONE && hasResult(p);
        // Quem ainda tem o novo giro fica sabendo que Continuar o descarta.
        boolean warn = continueButton.active && p.canReroll();
        if (warn != continueWarns) {
            continueWarns = warn;
            continueButton.setTooltip(warn ? net.minecraft.client.gui.components.Tooltip.create(
                    Component.translatable(KEY + "continue.tooltip")) : null);
        }
    }

    private void spin() {
        CharacterProfile p = ProfileClientCache.profile();
        if (busy() || (p.born() && !p.canReroll())) return;
        phase = Phase.WAITING;
        speed = 0;
        waitTicks = 0;
        status = null;
        requestRevision = ProfileClientCache.rollReplyRevision();
        PacketDistributor.sendToServer(new GameplayNetworking.BirthRollRequest(p.born(), p.rollCount()));
        updateButtons();
    }

    /**
     * Aceita o resultado: a ficha ja esta salva no servidor; o servidor confirma o nascimento (o novo giro restante
     * e descartado, entao reabrir a roleta nao troca mais o resultado) e a tela segue para o menu do personagem.
     */
    private void confirm() {
        CharacterProfile p = ProfileClientCache.profile();
        if (phase != Phase.DONE || !hasResult(p)) return;
        if (!p.birthConfirmed()) PacketDistributor.sendToServer(new GameplayNetworking.BirthConfirmRequest(p.rollCount()));
        minecraft.setScreen(new CharacterHubScreen());
    }

    /** Desacelera ate a origem da ficha atual (com revelacao quando e um resultado novo). */
    private void settleOn(CharacterProfile p, boolean reveal) {
        shownRollCount = p.rollCount();
        if (reveal) status = null;
        spinFrom = angle;
        spinTick = 0;
        revealAfterSpin = false;
        phase = Phase.SPINNING;
        if (!hasResult(p)) {
            // Sem nascimento: so desacelera ate parar e volta a aguardar o sorteio.
            spinTo = angle + speed * 6F;
            spinLength = 20;
            return;
        }
        float target = restingAngle(p);
        float base = angle + (reveal ? 720F : 360F);
        spinTo = base + Mth.positiveModulo(target - base, 360F);
        spinLength = reveal ? 50 : 30;
        revealAfterSpin = reveal;
    }

    /** Angulo em que o centro da fatia da raridade da Speciality fica sob o ponteiro (ficha legada: topo). */
    private static float restingAngle(CharacterProfile p) {
        Speciality speciality = p.specialityOrNull();
        if (speciality == null) return 0;
        float start = 0;
        for (SpecialityRarity r : SpecialityRarity.values()) {
            float size = sliceSize(r);
            if (r == speciality.rarity()) return -(start + size / 2F);
            start += size;
        }
        return 0;
    }

    private static float sliceSize(SpecialityRarity rarity) {
        return 360F * SpecialityOdds.DEFAULT.permille(rarity) / SpecialityOdds.TOTAL;
    }

    private static int resultColor(CharacterProfile p) {
        Speciality speciality = p.specialityOrNull();
        if (speciality != null) return speciality.rarity().color();
        BirthOrigin origin = p.originOrNull();
        return origin == null ? Ui.GOLD : origin.color();
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
                GameplayNetworking.BirthRollReply reply = ProfileClientCache.lastRollReply();
                if (ProfileClientCache.rollReplyRevision() != requestRevision && reply != null) {
                    // Resposta do servidor (a ficha correspondente chegou antes dela).
                    boolean rolled = reply.outcome() == BirthRoulette.Outcome.ROLLED
                            && reply.rollCount() == p.rollCount() && hasResult(p);
                    if (!rolled) status = Component.translatable(KEY + "status."
                            + (reply.outcome() == BirthRoulette.Outcome.NO_REROLLS ? "no_rerolls" : "stale"));
                    settleOn(p, rolled);
                } else if (++waitTicks > TIMEOUT_TICKS) {
                    // Sem resposta: volta ao estado real. Uma resposta atrasada e tratada abaixo (ficha nova).
                    status = Component.translatable(KEY + "status.timeout");
                    settleOn(p, false);
                }
            }
            case SPINNING -> {
                spinTick++;
                float t = Math.min(1F, spinTick / (float) spinLength);
                float eased = 1F - (1F - t) * (1F - t) * (1F - t);
                angle = spinFrom + (spinTo - spinFrom) * eased;
                tickSound();
                if (t >= 1F) {
                    speed = 0;
                    if (revealAfterSpin) {
                        phase = Phase.REVEAL;
                        revealTick = 0;
                        int color = resultColor(p);
                        vfx.flash(cx, cy - radius, 50, color, 16);
                        vfx.ring(cx, cy, radius + 30, color, 20);
                        vfx.burst(cx, cy - radius, 50, color, 4F);
                        Ui.playUi(ModSounds.ROULETTE_WIN.get(), 1F, 0.8F);
                    } else {
                        phase = hasResult(p) ? Phase.DONE : Phase.IDLE;
                        revealTick = 999;
                    }
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
                    phase = hasResult(p) ? Phase.DONE : Phase.IDLE;
                }
            }
            default -> {
                if (p.rollCount() != shownRollCount || (phase == Phase.DONE) != hasResult(p)) {
                    // A ficha mudou fora de um pedido desta tela (resposta atrasada, giro do admin, ficha zerada).
                    settleOn(p, hasResult(p) && p.rollCount() != shownRollCount);
                } else if (ticks % 6 == 0 && p.born()) {
                    vfx.ember(cx + (float) (Math.random() - 0.5) * radius * 2, cy + radius, Ui.alpha(Ui.GOLD, 0.8F));
                }
            }
        }
        updateButtons();
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
        float start = 0;
        for (SpecialityRarity r : SpecialityRarity.values()) {
            float size = sliceSize(r);
            if (pos < start + size) return r.ordinal();
            start += size;
        }
        return 0;
    }

    private int attrX() { return cx + radius + 26; }
    private int attrW() { return left + panelWidth - 10 - attrX(); }
    private int attrY(int index) { return top + 66 + index * 18; }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        float a = Mth.lerp(partialTick, prevAngle, angle);
        drawWheel(g, a);
        CharacterProfile p = ProfileClientCache.profile();
        Speciality speciality = p.specialityOrNull();
        // Origem social so em ficha antiga (sem Speciality): o bonus dela continua nos atributos.
        BirthOrigin origin = speciality == null ? p.originOrNull() : null;
        int x = attrX();
        boolean showResult = (phase == Phase.REVEAL || phase == Phase.DONE) && hasResult(p);
        if (showResult && speciality != null) {
            SpecialityRarity rarity = speciality.rarity();
            Component rarityName = Component.translatable(rarity.translationKey());
            g.drawString(font, Component.translatable(speciality.translationKey()), x, top + 30, rarity.color(), true);
            g.drawString(font, rarityName, x + attrW() - font.width(rarityName), top + 30, rarity.color(), false);
            drawWrapped(g, Component.translatable(speciality.translationKey() + ".desc"), x, top + 42, attrW(), 2);
        } else if (showResult && origin != null) {
            Ui.icon(g, SkillDef.iconTexture(origin.icon()), x, top + 29, 20);
            g.drawString(font, Component.translatable("origin.tupinicraft." + origin.id()), x + 24, top + 30, origin.color(), true);
            g.drawString(font, Component.translatable("gui.tupinicraft.speciality.legacy_origin"), x + 24, top + 41, Ui.MUTED, false);
        } else {
            g.drawWordWrap(font, Component.translatable(KEY + "intro"), x, top + 28, attrW(), Ui.MUTED);
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
            g.drawString(font, Component.translatable(KEY + "points", p.skillPoints()), left + 12, top + panelHeight - 16, Ui.GOLD, false);
            drawTraits(g, p, x, attrY(CharacterAttribute.values().length) + 2, mouseX, mouseY);
        }
        if (status != null && phase != Phase.WAITING) {
            g.drawString(font, status, left + 12, top + panelHeight - 28, Ui.RED, false);
        }
    }

    /** Ate {@code maxLines} linhas de texto quebrado (a descricao curta da Speciality). */
    private void drawWrapped(GuiGraphics g, Component text, int x, int y, int width, int maxLines) {
        var lines = font.split(text, width);
        for (int i = 0; i < Math.min(maxLines, lines.size()); i++) {
            g.drawString(font, lines.get(i), x, y + i * 9, Ui.MUTED, false);
        }
    }

    /** Traco de nascimento sorteado junto (separado da Speciality): nome e Buff/Debuff/Neutro; descricao na dica. */
    private void drawTraits(GuiGraphics g, CharacterProfile p, int x, int y, int mouseX, int mouseY) {
        java.util.List<BirthTrait> traits = p.birthTraits();
        String label = Component.translatable("gui.tupinicraft.traits").getString() + ": ";
        g.drawString(font, label, x, y, Ui.MUTED, false);
        int nx = x + font.width(label);
        if (traits.isEmpty()) {
            g.drawString(font, Component.translatable("gui.tupinicraft.trait.none_short"), nx, y, Ui.MUTED, false);
            return;
        }
        for (BirthTrait trait : traits) {
            Component name = Component.translatable(trait.translationKey()).append(" (")
                    .append(Component.translatable("gui.tupinicraft.trait.polarity." + trait.polarity().id())).append(")");
            g.drawString(font, name, nx, y, trait.polarity().color(), true);
            if (mouseX >= nx && mouseX < nx + font.width(name) && mouseY >= y - 2 && mouseY < y + 10) {
                g.renderComponentTooltip(font, java.util.List.of(Component.translatable(trait.translationKey()),
                        Component.translatable(trait.translationKey() + ".desc").withStyle(net.minecraft.ChatFormatting.GRAY),
                        Component.translatable(trait.hasEffect() ? "gui.tupinicraft.trait.has_effect"
                                : "gui.tupinicraft.trait.no_effect").withStyle(net.minecraft.ChatFormatting.DARK_GRAY)),
                        mouseX, mouseY);
            }
            y += 11;
        }
    }

    private void drawWheel(GuiGraphics g, float rotation) {
        Ui.glow(g, cx, cy, radius + 26, 0x40FFE27A);
        // Aro externo
        disc(g, cx, cy, radius + 5, 0xFF06080A);
        disc(g, cx, cy, radius + 3, 0xFFD6B84A);
        float start = rotation;
        CharacterProfile p = ProfileClientCache.profile();
        int highlighted = (phase == Phase.REVEAL || phase == Phase.DONE) && p.specialityOrNull() != null
                ? segmentAtPointer(rotation) : -1;
        for (SpecialityRarity r : SpecialityRarity.values()) {
            float size = sliceSize(r);
            int color = Ui.lerpColor(r.color(), 0xFF000000, 0.2F);
            if (highlighted == r.ordinal()) color = Ui.lerpColor(r.color(), 0xFFFFFFFF, 0.25F + 0.1F * Mth.sin(ticks * 0.3F));
            sector(g, cx, cy, radius, start, start + size, color);
            // nome da raridade no meio da fatia (so nas que cabem)
            if (size > 30) {
                float mid = (float) Math.toRadians(start + size / 2F - 90F);
                String label = Component.translatable(r.translationKey()).getString();
                float lx = cx + Mth.cos(mid) * radius * 0.62F, ly = cy + Mth.sin(mid) * radius * 0.62F;
                g.pose().pushPose();
                g.pose().translate(lx, ly, 0);
                g.pose().scale(0.75F, 0.75F, 1F);
                g.drawString(font, label, -font.width(label) / 2, -4, 0xFFFFFFFF, true);
                g.pose().popPose();
            }
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
