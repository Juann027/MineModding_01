package com.respectcraft.client.holo;

import com.respectcraft.client.ui.Ui;
import com.respectcraft.needs.Need;
import com.respectcraft.needs.NeedEffectDef;
import com.respectcraft.needs.Needs;
import com.respectcraft.needs.NeedsRules;
import com.respectcraft.network.ProfileClientCache;
import com.respectcraft.profile.BirthTrait;
import com.respectcraft.profile.CharacterProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

/**
 * Secao 03 "Body & Traits": as quatro necessidades ativas (Coom, Bexiga, Intestino, Emocao) com o mesmo limite
 * critico do servidor, os efeitos de consumiveis em andamento e o traco de nascimento com polaridade,
 * classificacao, elegibilidade, descricao e situacao do efeito. Hidratacao e Energia (legadas) nao aparecem.
 */
final class BodyView extends HoloView {
    private final float[] shown = new float[Need.ACTIVE.length];
    private boolean primed;

    BodyView(HoloMenu menu) { super(menu, HoloCharacterScreen.Section.BODY); }

    /** Mesmo limite usado pelo servidor nos avisos e efeitos (e pelo painel do inventario). */
    static boolean critical(Need need, int raw) {
        return need.highIsGood() ? raw < NeedsRules.LOW : raw > NeedsRules.URGENT;
    }

    static List<Component> needTooltip(Need need, Needs needs) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("need.respectcraft." + need.id()).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
        lines.add(Component.literal(needs.percent(need) + "%").withStyle(ChatFormatting.GRAY));
        String desc = "need.respectcraft." + need.id() + ".desc";
        if (Language.getInstance().has(desc)) lines.add(Component.translatable(desc).withStyle(ChatFormatting.GRAY));
        if (critical(need, needs.get(need))) {
            lines.add(Component.translatable("need.respectcraft." + need.id() + ".critical").withStyle(ChatFormatting.RED));
        }
        for (Map.Entry<NeedEffectDef, Integer> e : ProfileClientCache.activeEffects()) {
            NeedEffectDef def = e.getKey();
            if (def.rate(need) != 1F || def.gradual().get(need) != 0 || def.delayed().get(need) != 0) {
                lines.add(Component.translatable("gui.respectcraft.needs.effect_line",
                        Component.translatable("item.respectcraft." + def.id()), e.getValue()).withStyle(ChatFormatting.GREEN));
            }
        }
        return lines;
    }

    static List<Component> traitTooltip(BirthTrait t) {
        return List.of(Component.translatable(t.translationKey()).withStyle(s -> s.withColor(t.polarity().color() & 0xFFFFFF).withBold(true)),
                Component.translatable("gui.respectcraft.trait.polarity." + t.polarity().id()).withStyle(ChatFormatting.GRAY),
                classification(t).withStyle(ChatFormatting.GRAY),
                Component.translatable(t.translationKey() + ".desc").withStyle(ChatFormatting.GRAY),
                Component.translatable(t.hasEffect() ? "gui.respectcraft.trait.has_effect" : "gui.respectcraft.trait.no_effect")
                        .withStyle(ChatFormatting.DARK_GRAY));
    }

    /** "Classification: Endurance · Universal" a partir dos dados do enum (categoria e elegibilidade). */
    static net.minecraft.network.chat.MutableComponent classification(BirthTrait t) {
        return Component.translatable("gui.respectcraft.trait.classification",
                Component.translatable("gui.respectcraft.trait.category." + t.category().name().toLowerCase(Locale.ROOT)),
                Component.translatable("gui.respectcraft.trait.eligibility." + t.eligibility().name().toLowerCase(Locale.ROOT)));
    }

    @Override
    void onEnter() {
        super.onEnter();
        primed = false;
    }

    @Override
    float body(HoloCanvas c, float w, float viewportHeight, float since) {
        CharacterProfile p = ProfileClientCache.profile();
        Needs n = ProfileClientCache.needs();
        float x = 10F, iw = w - 20F, y = 3F;
        int i = 1;
        c.stage(since, i++);
        c.caption(tr("gui.respectcraft.hub.needs"), x, y + 1F, c.muted());
        y += 12F;

        // ---- quatro medidores (2 colunas; 1 coluna se o painel for estreito)
        boolean two = iw >= 160F;
        float cardW = two ? (iw - 6F) / 2F : iw;
        float cardH = 32F;
        for (int k = 0; k < Need.ACTIVE.length; k++) {
            Need need = Need.ACTIVE[k];
            float target = n.get(need) / (float) Needs.MAX;
            if (!primed) shown[k] = 0F;
            shown[k] = HoloConfig.reducedMotion() ? target : HoloAnim.approach(shown[k], target, 7F, c.dt);
            c.stage(since, i + k);
            float cx = two ? x + (k % 2) * (cardW + 6F) : x;
            float cy = two ? y + (k / 2) * (cardH + 5F) : y + k * (cardH + 4F);
            boolean critical = critical(need, n.get(need));
            boolean hot = c.over(cx, cy, cardW, cardH);
            float hk = c.hoverAnim("need." + need.id(), hot);
            c.fill(cx, cy, cardW, cardH, Ui.alpha(need.color(), 0.06F + 0.06F * hk));
            c.frame(cx, cy, cardW, cardH, Ui.alpha(critical ? HoloStyle.RED : need.color(), 0.25F + 0.4F * hk));
            c.fill(cx, cy, 2F, cardH, Ui.alpha(need.color(), 0.9F));
            String name = tr("need.respectcraft." + need.id());
            String pct = n.percent(need) + "%";
            float pctW = c.measure(HoloCanvas.Type.MONO, pct, 1.35F);
            c.text(Ui.fit(c.font, name, (int) (cardW - pctW - 14)), cx + 6F, cy + 5F, critical ? HoloStyle.RED : c.text(), false);
            c.type(HoloCanvas.Type.MONO, pct, cx + cardW - 5F - pctW, cy + 5F, 1.35F, critical ? HoloStyle.RED : need.color());
            c.bar(cx + 6F, cy + 20F, cardW - 12F, 5F, shown[k], need.color(), 12, critical);
            if (critical) c.text("!", cx + cardW - 9F, cy + 19F, HoloStyle.RED, true);
            if (hot) c.tooltip(needTooltip(need, n));
        }
        primed = true;
        i += Need.ACTIVE.length;
        y += two ? 2 * (cardH + 5F) : Need.ACTIVE.length * (cardH + 4F);

        // ---- efeitos de consumiveis em andamento
        List<Map.Entry<NeedEffectDef, Integer>> effects = ProfileClientCache.activeEffects();
        c.stage(since, i++);
        c.text(tr("gui.respectcraft.needs.effects", effects.size()), x, y, effects.isEmpty() ? c.dim() : HoloStyle.GREEN, false);
        y += 11F;
        for (Map.Entry<NeedEffectDef, Integer> e : effects) {
            String line = tr("gui.respectcraft.needs.effect_line", tr("item.respectcraft." + e.getKey().id()), e.getValue());
            c.text(Ui.fit(c.font, line, (int) (iw - 8)), x + 6F, y, c.muted(), false);
            if (c.over(x, y - 1, iw, 10)) c.tooltip(List.of(Component.translatable("item.respectcraft." + e.getKey().id()).withStyle(ChatFormatting.BOLD),
                    Component.translatable("effect.respectcraft.need." + e.getKey().id() + ".desc").withStyle(ChatFormatting.GRAY)));
            y += 10F;
        }
        y += 4F;

        // ---- traco de nascimento
        c.stage(since, i++);
        c.divider(x, y, iw, accent());
        y += 5F;
        c.caption(tr("gui.respectcraft.hub.traits"), x, y + 1F, c.muted());
        y += 12F;
        List<BirthTrait> traits = p.birthTraits();
        if (traits.isEmpty()) {
            y += c.wrap(Component.translatable(p.born() ? "gui.respectcraft.trait.none" : "gui.respectcraft.trait.unborn"),
                    x, y, iw, 3, c.dim()) + 2F;
            return y;
        }
        for (BirthTrait t : traits) {
            int col = t.polarity().color();
            float top = y;
            String tag = tr("gui.respectcraft.trait.polarity." + t.polarity().id());
            float tagW = c.font.width(tag) + 8F;
            String name = tr(t.translationKey());
            float size = c.fitSize(HoloCanvas.Type.DISPLAY, name, 0.85F, 0.55F, iw - tagW - 8F);
            c.glow(x + 18F, y + 5F, 22F, Ui.alpha(col, 0.2F));
            c.type(HoloCanvas.Type.DISPLAY, name, x + 4F, y + 1F, size, col);
            c.pill(tag, x + iw - tagW, y + 1F, col);
            y += HoloCanvas.capHeight(HoloCanvas.Type.DISPLAY, size) + 7F;
            c.stage(since, i++);
            y += c.wrap(classification(t), x + 4F, y, iw - 4F, 2, HoloStyle.CYAN_SOFT) + 1F;
            y += c.wrap(Component.translatable(t.translationKey() + ".desc"), x + 4F, y, iw - 4F, 3, c.muted()) + 1F;
            y += c.wrap(Component.translatable(t.hasEffect() ? "gui.respectcraft.trait.has_effect" : "gui.respectcraft.trait.no_effect"),
                    x + 4F, y, iw - 4F, 2, c.dim()) + 3F;
            c.alpha = c.panel.contentAlpha;
            c.fill(x, top, 1F, y - top - 3F, Ui.alpha(col, 0.6F));
            if (c.over(x, top, iw, y - top)) c.tooltip(traitTooltip(t));
        }
        return y;
    }
}
