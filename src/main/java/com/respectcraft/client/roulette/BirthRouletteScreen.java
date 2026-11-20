package com.respectcraft.client.roulette;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.respectcraft.RespectCraft;
import com.respectcraft.client.holo.HoloCharacterScreen;
import com.respectcraft.client.ui.RespectButton;
import com.respectcraft.client.ui.RespectScreen;
import com.respectcraft.client.ui.Ui;
import com.respectcraft.network.GameplayNetworking;
import com.respectcraft.network.ProfileClientCache;
import com.respectcraft.profile.BirthOrigin;
import com.respectcraft.profile.BirthRoulette;
import com.respectcraft.profile.BirthTrait;
import com.respectcraft.profile.CharacterAttribute;
import com.respectcraft.profile.CharacterProfile;
import com.respectcraft.profile.RouletteLayout;
import com.respectcraft.profile.Speciality;
import com.respectcraft.profile.SpecialityOdds;
import com.respectcraft.profile.SpecialityRarity;
import com.respectcraft.registry.ModSounds;
import com.respectcraft.skill.SkillDef;
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
 * som e VFX. Etapa 4.7.1: a roda tem seis fatias <b>iguais</b>, uma por raridade ({@link RouletteLayout}); o
 * tamanho da fatia nao e a chance. As chances reais sao as do servidor ({@link ProfileClientCache#odds()},
 * sincronizadas da config) e aparecem em numeros em cada fatia, na dica da roda e no selo do resultado. A roda
 * so para na raridade que o servidor gravou na ficha. Fichas antigas com origem social legada mostram essa origem
 * (com o bonus que ela deu) e param no topo da roda.
 *
 * <p>Maquina de estados (Etapa 4.5): {@code IDLE} aguardando sorteio -> {@code WAITING} pedido enviado, girando
 * -> {@code SPINNING} resposta recebida, desacelerando ate a origem -> {@code REVEAL} atributos -> {@code DONE}
 * resultado disponivel (Continuar ou o unico novo giro). O traco de nascimento aparece junto do resultado.
 * Continuar confirma o nascimento no servidor ({@link GameplayNetworking.BirthConfirmRequest}): o novo giro que
 * sobrou e descartado. A animacao nunca decide dados: o servidor responde a
 * todo pedido ({@link GameplayNetworking.BirthRollReply}) e a tela so mostra a ficha recebida. Etapa 4.8.2: cada
 * pedido leva um {@code requestId}; so a resposta com esse id encerra a espera. Erro do servidor
 * ({@link BirthRoulette.Outcome#ERROR}) mostra "Server error (request #N)" na hora; sem resposta nenhuma em
 * {@link #TIMEOUT_TICKS} (rede), a tela volta ao estado real com o aviso de timeout. Um pedido atrasado nao gera
 * resultado duplicado nem gasta o novo giro (o servidor confere o {@code rollCount} esperado).
 */
public final class BirthRouletteScreen extends RespectScreen {
    private static final String KEY = "gui.respectcraft.roulette.";
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
    /** Id do pedido de giro em andamento (Etapa 4.8.2): so a resposta com este id encerra a espera. */
    private int pendingRequestId;
    private int waitTicks;
    private Component status;
    private int cx, cy, radius;
    private RespectButton spinButton, continueButton;
    private boolean continueWarns;
    /** Ticks desde a revelacao de um resultado BONER (efeito especial so visual; -1 = nenhum). */
    private int bonerTick = -1;

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
        spinButton = addRenderableWidget(new RespectButton(bx, top + panelHeight - 54, 120, 20, spinLabel(), b -> spin())
                .item(new ItemStack(Items.EMERALD)).accent(Ui.GREEN));
        continueButton = addRenderableWidget(new RespectButton(bx, top + panelHeight - 30, 120, 20,
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
        pendingRequestId = ProfileClientCache.nextRollRequestId();
        PacketDistributor.sendToServer(new GameplayNetworking.BirthRollRequest(p.born(), p.rollCount(), pendingRequestId));
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
        minecraft.setScreen(new HoloCharacterScreen());
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

    /** Rotacao em que a fatia da raridade da Speciality da ficha fica sob o ponteiro (ficha legada: topo). */
    private static float restingAngle(CharacterProfile p) {
        Speciality speciality = p.specialityOrNull();
        return speciality == null ? 0 : RouletteLayout.restingRotation(speciality.rarity(), p.rollCount());
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
        tickBonerReveal();
        switch (phase) {
            case WAITING -> {
                speed = Math.min(24F, speed + 2.5F);
                angle += speed;
                tickSound();
                GameplayNetworking.BirthRollReply reply = ProfileClientCache.lastRollReply();
                if (ProfileClientCache.rollReplyRevision() != requestRevision && reply != null
                        && reply.answers(pendingRequestId)) {
                    // Resposta do servidor ao nosso pedido (a ficha correspondente chegou antes dela).
                    boolean rolled = reply.outcome() == BirthRoulette.Outcome.ROLLED
                            && reply.rollCount() == p.rollCount() && hasResult(p);
                    if (reply.outcome() == BirthRoulette.Outcome.ERROR) {
                        // O servidor falhou e disse isso: nada mudou na ficha (o novo giro continua intacto).
                        status = Component.translatable(KEY + "status.error", pendingRequestId);
                        RespectCraft.LOGGER.warn("Birth roulette request #{}: the server reported an internal error;"
                                + " look for the same request number in the server log", pendingRequestId);
                    } else if (!rolled) {
                        status = Component.translatable(KEY + "status."
                                + (reply.outcome() == BirthRoulette.Outcome.NO_REROLLS ? "no_rerolls" : "stale"));
                    }
                    settleOn(p, rolled);
                } else if (++waitTicks > TIMEOUT_TICKS) {
                    // Nenhuma resposta (problema de rede de verdade): volta ao estado real. Uma resposta atrasada
                    // e tratada abaixo (ficha nova); o servidor recusa um pedido repetido sobre a ficha antiga.
                    status = Component.translatable(KEY + "status.timeout", pendingRequestId);
                    RespectCraft.LOGGER.warn("Birth roulette request #{}: no reply from the server after {} ticks",
                            pendingRequestId, TIMEOUT_TICKS);
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
                        Speciality won = p.specialityOrNull();
                        bonerTick = won != null && won.rarity() == SpecialityRarity.BONER ? 0 : -1;
                        if (bonerTick == 0) Ui.playUi(ModSounds.ROULETTE_WIN.get(), 1.5F, 1F);
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
        return RouletteLayout.rarityAtPointer(angle).ordinal();
    }

    /**
     * BONER: chuva de faiscas rosa e brilho pulsante por ~2 s depois da revelacao. Puramente visual (particulas da
     * propria tela, sem texturas novas) e fora da maquina de estados: nao muda fase, botoes nem tempo da revelacao.
     */
    private void tickBonerReveal() {
        if (bonerTick < 0) return;
        if (++bonerTick > 40) {
            bonerTick = -1;
            return;
        }
        int pink = SpecialityRarity.BONER.color();
        if (bonerTick % 4 == 0) {
            float a = (float) (Math.random() * Math.PI * 2);
            float d = radius * (0.5F + (float) Math.random() * 0.7F);
            vfx.burst(cx + Mth.cos(a) * d, cy + Mth.sin(a) * d, 14, bonerTick % 8 == 0 ? pink : Ui.GOLD, 2.6F);
        }
        if (bonerTick % 10 == 0) vfx.ring(cx, cy, radius + 22 + bonerTick / 2F, pink, 16);
        if (bonerTick % 2 == 0) vfx.ember(attrX() + (float) Math.random() * attrW(), top + 40, Ui.alpha(pink, 0.9F));
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
            boolean boner = rarity == SpecialityRarity.BONER;
            // Selo da raridade com a chance real do servidor (o tamanho da fatia nao e a chance).
            Component badge = Component.translatable(rarity.translationKey()).append(" · "
                    + SpecialityOdds.formatPercent(ProfileClientCache.odds().permille(rarity)));
            int bw = font.width(badge) + 8, bx = x + attrW() - bw;
            // Fundo escuro tingido + borda e texto na cor da raridade: contraste alto em todas (inclusive Common).
            int badgeBg = Ui.lerpColor(rarity.color(), 0xFF000000, 0.72F);
            int border = boner ? Ui.lerpColor(rarity.color(), 0xFFFFFFFF, 0.2F + 0.2F * Mth.sin((ticks + partialTick) * 0.35F))
                    : rarity.color();
            g.fill(bx - 1, top + 27, bx + bw + 1, top + 40, border);
            g.fill(bx, top + 28, bx + bw, top + 39, badgeBg);
            g.drawString(font, badge, bx + 4, top + 30, Ui.lerpColor(rarity.color(), 0xFFFFFFFF, 0.3F), false);
            int nameColor = boner ? Ui.lerpColor(rarity.color(), 0xFFFFE27A, 0.5F + 0.5F * Mth.sin((ticks + partialTick) * 0.35F))
                    : Ui.lerpColor(rarity.color(), 0xFFFFFFFF, 0.25F);
            Component name = Component.translatable(speciality.translationKey());
            if (font.width(name) > bx - x - 4) {
                // Nome longo: desce para baixo do selo (cabe em qualquer escala de GUI).
                g.drawString(font, name, x, top + 42, nameColor, true);
                drawWrapped(g, Component.translatable(speciality.translationKey() + ".desc"), x, top + 52, attrW(), 1);
            } else {
                g.drawString(font, name, x, top + 30, nameColor, true);
                drawWrapped(g, Component.translatable(speciality.translationKey() + ".desc"), x, top + 42, attrW(), 2);
            }
        } else if (showResult && origin != null) {
            Ui.icon(g, SkillDef.iconTexture(origin.icon()), x, top + 29, 20);
            g.drawString(font, Component.translatable("origin.respectcraft." + origin.id()), x + 24, top + 30, origin.color(), true);
            g.drawString(font, Component.translatable("gui.respectcraft.speciality.legacy_origin"), x + 24, top + 41, Ui.MUTED, false);
        } else {
            g.drawWordWrap(font, Component.translatable(KEY + "intro"), x, top + 28, attrW(), Ui.MUTED);
        }
        int shown = phase == Phase.DONE ? 99 : phase == Phase.REVEAL ? revealTick / 6 : 0;
        for (CharacterAttribute attr : CharacterAttribute.values()) {
            int i = attr.ordinal();
            int y = attrY(i);
            boolean visible = i < shown;
            g.drawString(font, Component.translatable("attribute.respectcraft." + attr.id()), x, y, visible ? Ui.TEXT : 0xFF5A6570, false);
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
            // Cabe na coluna da roda (os botoes ficam a direita); o texto inteiro aparece na dica.
            int sx = left + 12, sy = top + panelHeight - 28, sw = left + panelWidth - 136 - sx;
            String full = status.getString();
            g.drawString(font, Ui.fit(font, full, sw), sx, sy, Ui.RED, false);
            if (font.width(full) > sw && mouseX >= sx && mouseX < sx + sw && mouseY >= sy - 2 && mouseY < sy + 10) {
                g.renderTooltip(font, font.split(status, 200), mouseX, mouseY);
            }
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
        String label = Component.translatable("gui.respectcraft.traits").getString() + ": ";
        g.drawString(font, label, x, y, Ui.MUTED, false);
        int nx = x + font.width(label);
        if (traits.isEmpty()) {
            g.drawString(font, Component.translatable("gui.respectcraft.trait.none_short"), nx, y, Ui.MUTED, false);
            return;
        }
        for (BirthTrait trait : traits) {
            Component name = Component.translatable(trait.translationKey()).append(" (")
                    .append(Component.translatable("gui.respectcraft.trait.polarity." + trait.polarity().id())).append(")");
            g.drawString(font, name, nx, y, trait.polarity().color(), true);
            if (mouseX >= nx && mouseX < nx + font.width(name) && mouseY >= y - 2 && mouseY < y + 10) {
                g.renderComponentTooltip(font, java.util.List.of(Component.translatable(trait.translationKey()),
                        Component.translatable(trait.translationKey() + ".desc").withStyle(net.minecraft.ChatFormatting.GRAY),
                        Component.translatable(trait.hasEffect() ? "gui.respectcraft.trait.has_effect"
                                : "gui.respectcraft.trait.no_effect").withStyle(net.minecraft.ChatFormatting.DARK_GRAY)),
                        mouseX, mouseY);
            }
            y += 11;
        }
    }

    private void drawWheel(GuiGraphics g, float rotation) {
        boolean boner = bonerTick >= 0;
        int glow = boner ? Ui.alpha(SpecialityRarity.BONER.color(), 0.35F + 0.25F * Mth.sin(ticks * 0.5F)) : 0x40FFE27A;
        Ui.glow(g, cx, cy, radius + (boner ? 34 : 26), glow);
        // Aro externo
        disc(g, cx, cy, radius + 5, 0xFF06080A);
        disc(g, cx, cy, radius + 3, 0xFFD6B84A);
        CharacterProfile p = ProfileClientCache.profile();
        SpecialityOdds odds = ProfileClientCache.odds();
        int highlighted = (phase == Phase.REVEAL || phase == Phase.DONE) && p.specialityOrNull() != null
                ? segmentAtPointer(rotation) : -1;
        // Fatias iguais (so visual): a chance real de cada raridade vem escrita na fatia.
        for (SpecialityRarity r : SpecialityRarity.values()) {
            float start = rotation + RouletteLayout.sliceStart(r);
            int color = Ui.lerpColor(r.color(), 0xFF000000, 0.38F);
            if (highlighted == r.ordinal()) color = Ui.lerpColor(r.color(), 0xFFFFFFFF, 0.15F + 0.1F * Mth.sin(ticks * 0.3F));
            sector(g, cx, cy, radius, start, start + RouletteLayout.SLICE, color);
            // miolo de cada fatia mais escuro: da profundidade sem textura
            sector(g, cx, cy, radius * 0.34F, start, start + RouletteLayout.SLICE, Ui.lerpColor(color, 0xFF000000, 0.35F));
            float mid = (float) Math.toRadians(start + RouletteLayout.SLICE / 2F - 90F);
            int lx = Math.round(cx + Mth.cos(mid) * radius * 0.64F), ly = Math.round(cy + Mth.sin(mid) * radius * 0.64F);
            int text = Ui.readableOn(color);
            boolean shadow = text == 0xFFFFFFFF;
            String label = Component.translatable(r.translationKey()).getString();
            String chance = SpecialityOdds.formatPercent(odds.permille(r));
            float scale = crispScale(Math.min(0.75F, radius * 0.62F / Math.max(1, font.width(label))));
            g.pose().pushPose();
            g.pose().translate(lx, ly, 0);
            g.pose().scale(scale, scale, 1F);
            g.drawString(font, label, -font.width(label) / 2, -9, text, shadow);
            g.drawString(font, chance, -font.width(chance) / 2, 2, shadow ? Ui.lerpColor(text, color, 0.15F) : text, shadow);
            g.pose().popPose();
        }
        // divisorias por cima das fatias
        for (int i = 0; i < RouletteLayout.SLICES; i++) {
            float edge = (float) Math.toRadians(rotation + i * RouletteLayout.SLICE - 90F);
            Ui.line(g, cx, cy, cx + Mth.cos(edge) * radius, cy + Mth.sin(edge) * radius, 1, 0xE006080A);
        }
        // Miolo
        disc(g, cx, cy, 13, 0xFF06080A);
        disc(g, cx, cy, 11, boner ? Ui.lerpColor(0xFF1D5C3A, SpecialityRarity.BONER.color(), 0.7F) : 0xFF1D5C3A);
        disc(g, cx, cy, 5, 0xFFFFE27A);
        // Ponteiro no topo
        int py = cy - radius - 8;
        for (int i = 0; i < 9; i++) g.fill(cx - 8 + i, py + i, cx + 9 - i, py + i + 1, i < 1 ? 0xFF06080A : 0xFFFFE27A);
        g.fill(cx - 1, py, cx + 2, py + 6, 0xFFFFFFFF);
        // Aviso: fatias iguais nao sao chances iguais.
        Component note = Component.translatable(KEY + "odds_note");
        float ns = crispScale(Math.min(0.75F, (radius * 2F + 16F) / Math.max(1, font.width(note))));
        g.pose().pushPose();
        g.pose().translate(cx, cy + radius + 8, 0);
        g.pose().scale(ns, ns, 1F);
        g.drawString(font, note, -font.width(note) / 2, 0, Ui.MUTED, false);
        g.pose().popPose();
    }

    /**
     * Maior escala ate {@code wanted} em que cada pixel da fonte vira um numero inteiro de pixels da janela: texto
     * reduzido fica nitido em qualquer escala de GUI (0,5 na escala 2; 0,67 na 3; 0,75 na 4). Nunca menor que 1:1.
     */
    private float crispScale(float wanted) {
        double gui = Math.max(1, minecraft.getWindow().getGuiScale());
        int pixels = Math.max(1, (int) Math.floor(wanted * gui + 1e-4));
        return (float) (pixels / gui);
    }

    /** Dica sobre a roda: tabela das chances reais do servidor. */
    @Override
    protected void renderOverlay(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderOverlay(g, mouseX, mouseY, partialTick);
        float dx = mouseX - cx, dy = mouseY - cy;
        if (dx * dx + dy * dy > (radius + 5) * (radius + 5)) return;
        SpecialityOdds odds = ProfileClientCache.odds();
        java.util.List<Component> lines = new java.util.ArrayList<>();
        lines.add(Component.translatable(KEY + "odds_title"));
        for (SpecialityRarity r : SpecialityRarity.values()) {
            lines.add(Component.translatable(r.translationKey()).withColor(r.color() & 0xFFFFFF)
                    .append(Component.literal("  " + SpecialityOdds.formatPercent(odds.permille(r)))
                            .withStyle(net.minecraft.ChatFormatting.WHITE)));
        }
        lines.add(Component.translatable(KEY + "odds_hint").withStyle(net.minecraft.ChatFormatting.GRAY));
        g.renderComponentTooltip(font, lines, mouseX, mouseY);
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
