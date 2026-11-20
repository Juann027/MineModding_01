package com.respectcraft.client.roulette;

import com.respectcraft.client.holo.HoloCharacterScreen;
import com.respectcraft.client.ui.RespectButton;
import com.respectcraft.client.ui.RespectScreen;
import com.respectcraft.client.ui.Ui;
import com.respectcraft.network.ProfileClientCache;
import com.respectcraft.profile.BirthOrigin;
import com.respectcraft.profile.BirthTrait;
import com.respectcraft.profile.CharacterAttribute;
import com.respectcraft.profile.CharacterProfile;
import com.respectcraft.profile.RouletteLayout;
import com.respectcraft.profile.Speciality;
import com.respectcraft.profile.SpecialityOdds;
import com.respectcraft.profile.SpecialityRarity;
import com.respectcraft.skill.SkillDef;
import com.respectcraft.ui.RouletteMachine;
import java.util.function.IntUnaryOperator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Roleta de nascimento. O sorteio e do servidor: a tela gira enquanto espera a resposta e depois desacelera ate
 * parar na raridade sorteada, mostra a Speciality (nome, raridade, descricao) e revela os atributos um a um, com
 * som e VFX. Etapa 4.7.1: a roda tem seis fatias <b>iguais</b>, uma por raridade ({@link RouletteLayout}); o
 * tamanho da fatia nao e a chance. As chances reais sao as do servidor ({@link ProfileClientCache#odds()},
 * sincronizadas da config) e aparecem em numeros em cada fatia, na dica da roda e no selo do resultado. A roda
 * so para na raridade que o servidor gravou na ficha. Fichas antigas com origem social legada mostram essa origem
 * (com o bonus que ela deu) e param no topo da roda.
 *
 * <p>Etapa 4.8.4-C: tela de reserva do modo {@code SCREEN}; no modo mundo a roleta e a placa holografica
 * {@code RouletteView}. A maquina de estados (fases, pedido com {@code requestId} da Etapa 4.8.2, erro, timeout,
 * resposta atrasada) e os pedidos ao servidor estao em {@link RouletteSession} / {@link RouletteMachine}, comuns as
 * duas; aqui fica so o desenho 2D e os efeitos da tela.
 */
public final class BirthRouletteScreen extends RespectScreen {
    private static final String KEY = "gui.respectcraft.roulette.";

    /** Estado, pedidos, sons e log: comuns a placa holografica (Etapa 4.8.4-C). */
    private final RouletteSession session = new RouletteSession();
    private final RouletteMachine machine = session.machine();
    private final PrizeWheel wheel = new PrizeWheel();
    private int cx, cy, radius;
    private RespectButton spinButton, continueButton;
    private boolean continueWarns;

    public BirthRouletteScreen() {
        super(Component.translatable(KEY + "title"));
    }

    @Override
    protected void init() {
        layoutPanel(400, 240);
        radius = Math.min(78, (panelHeight - 70) / 2);
        cx = left + 16 + radius;
        cy = top + 30 + radius + 4;
        int bx = left + panelWidth - 130;
        spinButton = addRenderableWidget(new RespectButton(bx, top + panelHeight - 54, 120, 20, session.spinLabel(), b -> spin())
                .item(new ItemStack(Items.EMERALD)).accent(Ui.GREEN));
        continueButton = addRenderableWidget(new RespectButton(bx, top + panelHeight - 30, 120, 20,
                Component.translatable(KEY + "continue"), b -> confirm()));
        continueWarns = false;
        updateButtons();
    }

    /** Botoes sempre derivados da fase e da ficha atual (chamado a cada tick: nunca ficam presos). */
    private void updateButtons() {
        if (spinButton == null) return;
        spinButton.active = session.canSpin();
        spinButton.setMessage(session.spinLabel());
        continueButton.active = session.canContinue();
        // Quem ainda tem o novo giro fica sabendo que Continuar o descarta.
        boolean warn = session.continueDiscardsReroll();
        if (warn != continueWarns) {
            continueWarns = warn;
            continueButton.setTooltip(warn ? net.minecraft.client.gui.components.Tooltip.create(
                    Component.translatable(KEY + "continue.tooltip")) : null);
        }
    }

    private void spin() {
        session.spin();
        updateButtons();
    }

    /** Aceita o resultado e segue para o menu do personagem. */
    private void confirm() {
        if (session.confirm()) minecraft.setScreen(new HoloCharacterScreen());
    }

    private static int resultColor(CharacterProfile p) {
        Speciality speciality = p.specialityOrNull();
        if (speciality != null) return speciality.rarity().color();
        BirthOrigin origin = p.originOrNull();
        return origin == null ? Ui.GOLD : origin.color();
    }

    /** Efeitos 2D desta tela nos momentos da roleta. */
    private final RouletteMachine.Listener effects = new RouletteMachine.Listener() {
        @Override public void revealed(CharacterProfile p) {
            int color = resultColor(p);
            vfx.flash(cx, cy - radius, 50, color, 16);
            vfx.ring(cx, cy, radius + 30, color, 20);
            vfx.burst(cx, cy - radius, 50, color, 4F);
        }
        @Override public void revealStep(int step) {
            CharacterAttribute a = CharacterAttribute.values()[step - 1];
            vfx.burst(attrX() + attrW() * session.profile().attribute(a) / (float) CharacterAttribute.MAX,
                    attrY(step - 1) + 11, 8, a.color(), 1.8F);
        }
    };

    @Override
    public void tick() {
        super.tick();
        session.tick(effects);
        tickBonerReveal();
        RouletteMachine.Phase phase = machine.phase();
        if ((phase == RouletteMachine.Phase.IDLE || phase == RouletteMachine.Phase.DONE) && ticks % 6 == 0
                && session.profile().born()) {
            vfx.ember(cx + (float) (Math.random() - 0.5) * radius * 2, cy + radius, Ui.alpha(Ui.GOLD, 0.8F));
        }
        updateButtons();
    }

    /**
     * BONER: chuva de faiscas rosa e brilho pulsante por ~2 s depois da revelacao. Puramente visual (particulas da
     * propria tela, sem texturas novas) e fora da maquina de estados: nao muda fase, botoes nem tempo da revelacao.
     */
    private void tickBonerReveal() {
        int bonerTick = session.bonerTick();
        if (bonerTick < 0) return;
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
        float a = machine.angle(partialTick);
        drawWheel(g, a, partialTick);
        CharacterProfile p = session.profile();
        RouletteMachine.Phase phase = machine.phase();
        int bonerTick = session.bonerTick();
        Speciality speciality = p.specialityOrNull();
        // Origem social so em ficha antiga (sem Speciality): o bonus dela continua nos atributos.
        BirthOrigin origin = speciality == null ? p.originOrNull() : null;
        int x = attrX();
        boolean showResult = machine.showsResult(p);
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
        int shown = machine.attributesShown();
        for (CharacterAttribute attr : CharacterAttribute.values()) {
            int i = attr.ordinal();
            int y = attrY(i);
            boolean visible = i < shown;
            g.drawString(font, Component.translatable("attribute.respectcraft." + attr.id()), x, y, visible ? Ui.TEXT : 0xFF5A6570, false);
            int value = machine.attributeValue(p, i, partialTick);
            String num = visible ? String.valueOf(value) : "?";
            g.drawString(font, num, x + attrW() - font.width(num), y, Ui.GOLD, false);
            Ui.bar(g, x, y + 9, attrW(), 6, value / (float) CharacterAttribute.MAX, attr.color());
            if (visible && origin != null && origin.bonus(attr) != 0) {
                String bonus = (origin.bonus(attr) > 0 ? "+" : "") + origin.bonus(attr);
                g.drawString(font, bonus, x + attrW() - font.width(num) - font.width(bonus) - 4, y,
                        origin.bonus(attr) > 0 ? Ui.GREEN : Ui.RED, false);
            }
        }
        if (phase == RouletteMachine.Phase.DONE && p.born()) {
            g.drawString(font, Component.translatable(KEY + "points", p.skillPoints()), left + 12, top + panelHeight - 16, Ui.GOLD, false);
            drawTraits(g, p, x, attrY(CharacterAttribute.values().length) + 2, mouseX, mouseY);
        }
        Component status = session.status();
        if (status != null) {
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

    private void drawWheel(GuiGraphics g, float rotation, float partialTick) {
        boolean boner = session.bonerTick() >= 0;
        CharacterProfile p = session.profile();
        RouletteMachine.Phase phase = machine.phase();
        SpecialityRarity winner = (phase == RouletteMachine.Phase.REVEAL || phase == RouletteMachine.Phase.DONE)
                && p.specialityOrNull() != null ? RouletteLayout.rarityAtPointer(rotation) : null;
        PrizeWheel.Mode mode = winner != null ? PrizeWheel.Mode.WINNER
                : phase == RouletteMachine.Phase.SPINNING || phase == RouletteMachine.Phase.WAITING ? PrizeWheel.Mode.SPINNING
                : PrizeWheel.Mode.IDLE;
        int glow = boner ? Ui.alpha(SpecialityRarity.BONER.color(), 0.35F + 0.25F * Mth.sin(ticks * 0.5F))
                : winner != null ? Ui.alpha(winner.color(), 0.4F) : 0x40FFE27A;
        Ui.glow(g, cx, cy, radius + (boner ? 34 : 26), glow);
        SpecialityOdds odds = ProfileClientCache.odds();
        // Fatias iguais (so visual): a chance real de cada raridade vem escrita na fatia.
        wheel.draw(g, IntUnaryOperator.identity(), cx, cy, radius + 4, rotation, (ticks + partialTick) / 20F, mode, winner,
                boner, 1F, (r, lx, ly, text, shadow) -> {
                    String label = Component.translatable(r.translationKey()).getString();
                    String chance = SpecialityOdds.formatPercent(odds.permille(r));
                    float scale = crispScale(Math.min(0.75F, radius * 0.58F / Math.max(1, font.width(label))));
                    g.pose().pushPose();
                    g.pose().translate(Math.round(lx), Math.round(ly), 0);
                    g.pose().scale(scale, scale, 1F);
                    g.drawString(font, label, -font.width(label) / 2, -9, text, shadow);
                    g.drawString(font, chance, -font.width(chance) / 2, 2, text, shadow);
                    g.pose().popPose();
                });
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
}
