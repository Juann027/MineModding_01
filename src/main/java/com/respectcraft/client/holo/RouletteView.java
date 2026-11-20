package com.respectcraft.client.holo;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.respectcraft.client.roulette.RouletteSession;
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
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * Roleta de nascimento holografica (Etapa 4.8.4-C), janela na frente do arco no mundo. Mesma roleta da tela antiga
 * ({@link RouletteSession}: sorteio so no servidor, {@code requestId}, erro e timeout da Etapa 4.8.2, um novo giro,
 * Continuar confirma no servidor): roda com seis fatias <b>iguais</b> (so visual) e as chances reais do servidor
 * escritas em cada fatia, ponteiro, giro e desaceleracao ate a raridade gravada na ficha, Speciality com o selo da
 * raridade, os seis atributos revelados um a um, o traco de nascimento e os pontos.
 *
 * <p>Novo giro e Continuar com giro sobrando gastam algo: pedem um segundo clique em ate
 * {@link HoloPopup#CONFIRM_WINDOW} s (a mira pode clicar sem querer enquanto o jogador anda).
 */
final class RouletteView extends HoloPopup {
    private static final String K = "gui.respectcraft.roulette.";
    static final float W = 424F, H = 262F;
    private static final float R = 84F, WX = 14F + R, WY = 36F + R + 6F;
    private static final float IX = WX + R + 22F;

    private final RouletteSession session = new RouletteSession();
    private final RouletteMachine machine = session.machine();
    /** Botao armado esperando o segundo clique ("spin" / "continue") e quando foi armado (relogio do menu). */
    private String armed;
    private float armedAt;

    RouletteView(HoloMenu menu) { super(menu, Kind.ROULETTE); }

    @Override float width() { return W; }
    @Override float height() { return H; }
    @Override int accent() { return HoloStyle.GOLD; }

    private static float attrX() { return IX; }
    private static float attrW() { return W - 12F - IX; }
    private static float attrY(int i) { return 92F + i * 17F; }

    @Override
    void tick() {
        session.tick(effects);
        int boner = session.bonerTick();
        if (boner >= 0 && HoloConfig.motion() > 0F) {
            int pink = SpecialityRarity.BONER.color();
            if (boner % 4 == 0) {
                float a = (float) (Math.random() * Math.PI * 2);
                float d = R * (0.5F + (float) Math.random() * 0.7F);
                menu.burstOnPopup(WX + Mth.cos(a) * d, WY + Mth.sin(a) * d, boner % 8 == 0 ? pink : HoloStyle.GOLD, 10, 60F);
            }
        }
        if (armed != null && menu.time() - armedAt > CONFIRM_WINDOW) armed = null;
    }

    /** Faiscas da janela nos momentos da roleta (sons e log ficam no {@link RouletteSession}). */
    private final RouletteMachine.Listener effects = new RouletteMachine.Listener() {
        @Override public void revealed(CharacterProfile p) {
            int color = resultColor(p);
            menu.burstOnPopup(WX, WY - R, color, 36, 90F);
            menu.burstOnPopup(attrX() + attrW() / 2F, 46F, color, 20, 60F);
        }
        @Override public void revealStep(int step) {
            CharacterAttribute a = CharacterAttribute.values()[step - 1];
            menu.burstOnPopup(attrX() + attrW() * session.profile().attribute(a) / (float) CharacterAttribute.MAX,
                    attrY(step - 1) + 12F, a.color(), 6, 35F);
        }
    };

    private static int resultColor(CharacterProfile p) {
        Speciality speciality = p.specialityOrNull();
        if (speciality != null) return speciality.rarity().color();
        BirthOrigin origin = p.originOrNull();
        return origin == null ? HoloStyle.GOLD : origin.color();
    }

    private void spin() {
        armed = null;
        session.spin();
    }

    private void confirm() {
        armed = null;
        if (session.confirm()) menu.rouletteConfirmed();
    }

    /** Primeiro clique arma, o segundo (em ate {@link #CONFIRM_WINDOW} s) executa. */
    private void armOr(String id, Runnable action) {
        if (id.equals(armed) && menu.time() - armedAt <= CONFIRM_WINDOW) {
            action.run();
        } else {
            armed = id;
            armedAt = menu.time();
            Ui.playClick(1.4F);
        }
    }

    @Override
    void draw(HoloCanvas c, float w, float h, float since) {
        CharacterProfile p = session.profile();
        header(c, w, since, KEY + "roulette", !p.born() ? 2 : 0);
        float partial = c.mc.getTimer().getGameTimeDeltaPartialTick(false);
        int i = 1;

        // ---- roda
        c.stage(since, i++);
        float rotation = machine.angle(partial);
        drawWheel(c, rotation, p);
        boolean wheelHot = c.over(WX - R - 6F, WY - R - 6F, 2F * R + 12F, 2F * R + 12F)
                && sq(c.panel.cursorX - WX) + sq(c.panel.cursorY - WY) <= sq(R + 6F);
        if (wheelHot) c.tooltip(oddsTooltip());
        String note = HoloView.tr(K + "odds_note");
        c.g.pose().pushPose();
        float ns = Math.min(0.75F, (2F * R + 16F) / Math.max(1, c.font.width(note)));
        c.g.pose().translate(WX, WY + R + 7F, 0F);
        c.g.pose().scale(ns, ns, 1F);
        c.text(note, -c.font.width(note) / 2F, 0F, c.muted(), false);
        c.g.pose().popPose();

        // ---- resultado
        c.stage(since, i++);
        float x = attrX(), aw = attrW();
        boolean showResult = machine.showsResult(p);
        Speciality speciality = p.specialityOrNull();
        BirthOrigin origin = speciality == null ? p.originOrNull() : null;
        float y = 34F;
        if (showResult && speciality != null) {
            SpecialityRarity rarity = speciality.rarity();
            boolean boner = rarity == SpecialityRarity.BONER && session.bonerTick() >= 0;
            String badge = HoloView.tr(rarity.translationKey()) + " · " + SpecialityOdds.formatPercent(ProfileClientCache.odds().permille(rarity));
            float bw = c.font.width(badge) + 8F;
            int border = boner ? Ui.lerpColor(rarity.color(), 0xFFFFFFFF, 0.2F + 0.2F * Mth.sin(c.time * 7F)) : rarity.color();
            c.fill(x + aw - bw - 1F, y - 1F, bw + 2F, 13F, border);
            c.fill(x + aw - bw, y, bw, 11F, Ui.lerpColor(rarity.color(), 0xFF000000, 0.72F));
            c.text(badge, x + aw - bw + 4F, y + 2F, Ui.lerpColor(rarity.color(), 0xFFFFFFFF, 0.3F), false);
            String name = HoloView.tr(speciality.translationKey());
            int nameColor = Ui.lerpColor(rarity.color(), 0xFFFFFFFF, 0.25F);
            float nameW = aw - bw - 6F;
            float size = c.fitSize(HoloCanvas.Type.DISPLAY, name, 0.95F, 0.55F, nameW);
            if (c.measure(HoloCanvas.Type.DISPLAY, name, size) > nameW) {
                // nome muito longo: desce para baixo do selo, inteiro na largura da coluna
                size = c.fitSize(HoloCanvas.Type.DISPLAY, name, 0.95F, 0.45F, aw);
                c.type(HoloCanvas.Type.DISPLAY, name, x, y + 15F, size, nameColor);
                y += 15F;
            } else {
                c.type(HoloCanvas.Type.DISPLAY, name, x, y + 1F, size, nameColor);
            }
            y += HoloCanvas.capHeight(HoloCanvas.Type.DISPLAY, size) + 6F;
            Component desc = Component.translatable(speciality.translationKey() + ".desc");
            c.wrap(desc, x, y, aw, 2, c.muted());
            if (c.over(x, 30F, aw, y + 20F - 30F)) c.tooltip(List.of(Component.literal(name).withStyle(s -> s.withColor(rarity.color() & 0xFFFFFF).withBold(true)),
                    Component.literal(badge).withStyle(ChatFormatting.GRAY), desc.copy().withStyle(ChatFormatting.GRAY)));
        } else if (showResult && origin != null) {
            c.icon(SkillDef.iconTexture(origin.icon()), x, y, 16F, 1F);
            c.text(HoloView.tr("origin.respectcraft." + origin.id()), x + 20F, y + 1F, origin.color(), true);
            c.wrap(Component.translatable("gui.respectcraft.speciality.legacy_origin"), x + 20F, y + 12F, aw - 20F, 3, c.muted());
        } else {
            Component intro = Component.translatable(machine.phase() == RouletteMachine.Phase.WAITING ? KEY + "roulette.waiting" : K + "intro");
            c.wrap(intro, x, y, aw, 4, machine.phase() == RouletteMachine.Phase.WAITING ? HoloStyle.CYAN_SOFT : c.muted());
        }

        // ---- atributos
        int shown = machine.attributesShown();
        for (CharacterAttribute attr : CharacterAttribute.values()) {
            c.stage(since, i++);
            int idx = attr.ordinal();
            float ay = attrY(idx);
            boolean visible = idx < shown;
            c.text(Ui.fit(c.font, HoloView.tr("attribute.respectcraft." + attr.id()), (int) (aw - 30)), x, ay, visible ? c.text() : c.dim(), false);
            int value = machine.attributeValue(p, idx, partial);
            String num = visible ? String.valueOf(value) : "?";
            if (visible && origin != null && origin.bonus(attr) != 0) {
                String bonus = (origin.bonus(attr) > 0 ? "+" : "") + origin.bonus(attr);
                c.textRight(bonus, x + aw - c.font.width(num) - 4F, ay, origin.bonus(attr) > 0 ? HoloStyle.GREEN : HoloStyle.RED, false);
            }
            c.textRight(num, x + aw, ay, HoloStyle.GOLD, false);
            c.bar(x, ay + 10F, aw, 3F, value / (float) CharacterAttribute.MAX, attr.color(), 0, false);
        }

        // ---- traco e pontos
        c.stage(since, i++);
        float ty = attrY(CharacterAttribute.values().length) + 1F;
        if (machine.phase() == RouletteMachine.Phase.DONE && p.born()) drawTraits(c, p, x, ty, aw);

        // ---- aviso (erro, timeout...)
        c.stage(since, i++);
        Component status = session.status();
        float by = h - 27F;
        if (status != null) {
            String full = status.getString();
            float sw = aw;
            c.text(Ui.fit(c.font, full, (int) sw), x, by - 13F, HoloStyle.RED, false);
            if (c.font.width(full) > sw && c.over(x, by - 15F, sw, 12F)) c.tooltip(List.of(status));
        }

        // ---- botoes
        c.stage(since, i++);
        boolean canSpin = session.canSpin();
        boolean reroll = p.born();
        String spinId = "spin";
        String spinLabel = spinId.equals(armed) ? HoloView.tr(KEY + "confirm") : session.spinLabel().getString();
        float spinW = (aw - 6F) * 0.52F;
        boolean spinHot = c.button("rl.spin", spinLabel, x, by, spinW, 20F, SkillDef.iconTexture("trevo"), HoloStyle.GREEN,
                canSpin, spinId.equals(armed), "", () -> {
                    if (reroll) armOr(spinId, this::spin);
                    else spin();
                });
        menu.hoverSound("rl.spin", spinHot);
        if (spinHot) c.tooltip(List.of(session.spinLabel().copy().withStyle(ChatFormatting.BOLD),
                Component.translatable(KEY + (reroll ? "roulette.reroll.desc" : "roulette.spin.desc")).withStyle(ChatFormatting.GRAY)));
        boolean warn = session.continueDiscardsReroll();
        String contId = "continue";
        String contLabel = contId.equals(armed) ? HoloView.tr(KEY + "confirm") : HoloView.tr(K + "continue");
        float contX = x + spinW + 6F, contW = aw - spinW - 6F;
        boolean contHot = c.button("rl.continue", contLabel, contX, by, contW, 20F, null, HoloStyle.CYAN, session.canContinue(),
                contId.equals(armed), "", () -> {
                    if (warn) armOr(contId, this::confirm);
                    else confirm();
                });
        menu.hoverSound("rl.continue", contHot);
        if (contHot) c.tooltip(List.of(Component.translatable(K + "continue").withStyle(ChatFormatting.BOLD),
                Component.translatable(warn ? K + "continue.tooltip" : KEY + "roulette.continue.desc").withStyle(ChatFormatting.GRAY)));

        // ---- Back (embaixo da roda)
        c.stage(since, i);
        boolean backHot = c.button("rl.back", HoloView.tr(KEY + "back"), 14F, h - 24F, 76F, 16F, null, HoloStyle.CYAN, true, false, "",
                menu::closePopup);
        menu.hoverSound("rl.back", backHot);
        if (backHot) c.tooltip(List.of(Component.translatable(KEY + "back.roulette").withStyle(ChatFormatting.GRAY)));
        if (HoloConfig.motion() > 0F && p.born() && machine.phase() == RouletteMachine.Phase.DONE) {
            c.glow(WX, WY + R, 26F + 4F * Mth.sin(c.time * 2F), Ui.alpha(HoloStyle.GOLD, 0.25F));
        }
    }

    /** Traco de nascimento (separado da Speciality): nome e Buff/Debuff/Neutro; a descricao na dica. Mais os pontos. */
    private void drawTraits(HoloCanvas c, CharacterProfile p, float x, float y, float w) {
        String label = HoloView.tr("gui.respectcraft.traits") + ": ";
        c.text(label, x, y, c.muted(), false);
        float nx = x + c.font.width(label);
        List<BirthTrait> traits = p.birthTraits();
        if (traits.isEmpty()) {
            c.text(HoloView.tr("gui.respectcraft.trait.none_short"), nx, y, c.muted(), false);
        } else {
            BirthTrait trait = traits.get(0);
            String name = HoloView.tr(trait.translationKey()) + " (" + HoloView.tr("gui.respectcraft.trait.polarity." + trait.polarity().id()) + ")";
            String shown = Ui.fit(c.font, name, (int) (x + w - nx));
            c.text(shown, nx, y, trait.polarity().color(), true);
            if (c.over(x, y - 2F, w, 12F)) c.tooltip(List.of(Component.translatable(trait.translationKey()).withStyle(ChatFormatting.BOLD),
                    Component.translatable(trait.translationKey() + ".desc").withStyle(ChatFormatting.GRAY),
                    Component.translatable(trait.hasEffect() ? "gui.respectcraft.trait.has_effect" : "gui.respectcraft.trait.no_effect")
                            .withStyle(ChatFormatting.DARK_GRAY)));
        }
        c.text(Ui.fit(c.font, HoloView.tr(K + "points", p.skillPoints()), (int) w), x, y + 12F, HoloStyle.GOLD, false);
    }

    private static List<Component> oddsTooltip() {
        SpecialityOdds odds = ProfileClientCache.odds();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(K + "odds_title").withStyle(ChatFormatting.BOLD));
        for (SpecialityRarity r : SpecialityRarity.values()) {
            lines.add(Component.translatable(r.translationKey()).withColor(r.color() & 0xFFFFFF)
                    .append(Component.literal("  " + SpecialityOdds.formatPercent(odds.permille(r))).withStyle(ChatFormatting.WHITE)));
        }
        lines.add(Component.translatable(K + "odds_hint").withStyle(ChatFormatting.GRAY));
        return lines;
    }

    private static float sq(float v) { return v * v; }

    // ================================================================ roda

    private void drawWheel(HoloCanvas c, float rotation, CharacterProfile p) {
        boolean boner = session.bonerTick() >= 0;
        int glow = boner ? Ui.alpha(SpecialityRarity.BONER.color(), 0.35F + 0.25F * Mth.sin(c.time * 10F)) : 0x40FFE27A;
        c.glow(WX, WY, R + (boner ? 34F : 24F), glow);
        disc(c, WX, WY, R + 5F, 0xFF06080A);
        disc(c, WX, WY, R + 3F, 0xFFD6B84A);
        SpecialityOdds odds = ProfileClientCache.odds();
        RouletteMachine.Phase phase = machine.phase();
        int highlighted = (phase == RouletteMachine.Phase.REVEAL || phase == RouletteMachine.Phase.DONE) && p.specialityOrNull() != null
                ? RouletteLayout.rarityAtPointer(rotation).ordinal() : -1;
        // Fatias iguais (so visual): a chance real de cada raridade vem escrita na fatia.
        for (SpecialityRarity r : SpecialityRarity.values()) {
            float start = rotation + RouletteLayout.sliceStart(r);
            int color = Ui.lerpColor(r.color(), 0xFF000000, 0.38F);
            if (highlighted == r.ordinal()) color = Ui.lerpColor(r.color(), 0xFFFFFFFF, 0.15F + 0.1F * Mth.sin(c.time * 6F));
            sector(c, WX, WY, R, start, start + RouletteLayout.SLICE, color);
            sector(c, WX, WY, R * 0.34F, start, start + RouletteLayout.SLICE, Ui.lerpColor(color, 0xFF000000, 0.35F));
            float mid = (float) Math.toRadians(start + RouletteLayout.SLICE / 2F - 90F);
            float lx = WX + Mth.cos(mid) * R * 0.64F, ly = WY + Mth.sin(mid) * R * 0.64F;
            int text = Ui.readableOn(color);
            boolean shadow = text == 0xFFFFFFFF;
            String label = HoloView.tr(r.translationKey());
            String chance = SpecialityOdds.formatPercent(odds.permille(r));
            float scale = Math.min(0.72F, R * 0.62F / Math.max(1, c.font.width(label)));
            c.g.pose().pushPose();
            c.g.pose().translate(lx, ly, 0F);
            c.g.pose().scale(scale, scale, 1F);
            c.text(label, -c.font.width(label) / 2F, -9F, text, shadow);
            c.text(chance, -c.font.width(chance) / 2F, 2F, shadow ? Ui.lerpColor(text, color, 0.15F) : text, shadow);
            c.g.pose().popPose();
        }
        for (int s = 0; s < RouletteLayout.SLICES; s++) {
            float edge = (float) Math.toRadians(rotation + s * RouletteLayout.SLICE - 90F);
            line(c, WX, WY, WX + Mth.cos(edge) * R, WY + Mth.sin(edge) * R, 0xE006080A);
        }
        disc(c, WX, WY, 13F, 0xFF06080A);
        disc(c, WX, WY, 11F, boner ? Ui.lerpColor(0xFF1D5C3A, SpecialityRarity.BONER.color(), 0.7F) : 0xFF1D5C3A);
        disc(c, WX, WY, 5F, 0xFFFFE27A);
        // ponteiro no topo
        float py = WY - R - 9F;
        for (int k = 0; k < 9; k++) c.fill(WX - 8F + k, py + k, 17F - 2F * k, 1F, k < 1 ? 0xFF06080A : 0xFFFFE27A);
        c.fill(WX - 1F, py, 3F, 6F, 0xFFFFFFFF);
        c.glow(WX, py + 4F, 10F, Ui.alpha(0xFFFFE27A, 0.5F));
    }

    private static void disc(HoloCanvas c, float x, float y, float r, int color) { sector(c, x, y, r, 0F, 360F, color); }

    /** Fatia de circulo (graus, 0 = topo, sentido horario) com a opacidade de revelacao do canvas. */
    private static void sector(HoloCanvas c, float x, float y, float r, float from, float to, int argb) {
        int col = c.c(argb);
        if ((col >>> 24) == 0) return;
        VertexConsumer vc = c.g.bufferSource().getBuffer(RenderType.gui());
        Matrix4f m = c.g.pose().last().pose();
        int steps = Math.max(2, Math.round((to - from) / 4F));
        for (int i = 0; i < steps; i++) {
            float a0 = (float) Math.toRadians(from + (to - from) * i / steps - 90F);
            float a1 = (float) Math.toRadians(from + (to - from) * (i + 1) / steps - 90F);
            float x0 = x + Mth.cos(a0) * r, y0 = y + Mth.sin(a0) * r;
            float x1 = x + Mth.cos(a1) * r, y1 = y + Mth.sin(a1) * r;
            // as duas ordens de vertices: o tipo de render da GUI pode descartar faces "de costas"
            vc.addVertex(m, x, y, 0).setColor(col);
            vc.addVertex(m, x0, y0, 0).setColor(col);
            vc.addVertex(m, x1, y1, 0).setColor(col);
            vc.addVertex(m, x, y, 0).setColor(col);
            vc.addVertex(m, x, y, 0).setColor(col);
            vc.addVertex(m, x1, y1, 0).setColor(col);
            vc.addVertex(m, x0, y0, 0).setColor(col);
            vc.addVertex(m, x, y, 0).setColor(col);
        }
        c.g.flush();
    }

    /** Divisoria fina entre fatias (quad de 1 unidade de largura). */
    private static void line(HoloCanvas c, float x0, float y0, float x1, float y1, int argb) {
        int col = c.c(argb);
        float dx = x1 - x0, dy = y1 - y0, len = Mth.sqrt(dx * dx + dy * dy);
        if (len < 0.01F || (col >>> 24) == 0) return;
        float nx = -dy / len * 0.5F, ny = dx / len * 0.5F;
        VertexConsumer vc = c.g.bufferSource().getBuffer(RenderType.gui());
        Matrix4f m = c.g.pose().last().pose();
        vc.addVertex(m, x0 + nx, y0 + ny, 0).setColor(col);
        vc.addVertex(m, x0 - nx, y0 - ny, 0).setColor(col);
        vc.addVertex(m, x1 - nx, y1 - ny, 0).setColor(col);
        vc.addVertex(m, x1 + nx, y1 + ny, 0).setColor(col);
        vc.addVertex(m, x1 + nx, y1 + ny, 0).setColor(col);
        vc.addVertex(m, x1 - nx, y1 - ny, 0).setColor(col);
        vc.addVertex(m, x0 - nx, y0 - ny, 0).setColor(col);
        vc.addVertex(m, x0 + nx, y0 + ny, 0).setColor(col);
        c.g.flush();
    }
}
