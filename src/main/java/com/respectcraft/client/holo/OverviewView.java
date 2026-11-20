package com.respectcraft.client.holo;

import com.respectcraft.client.ui.Ui;
import com.respectcraft.needs.Need;
import com.respectcraft.needs.Needs;
import com.respectcraft.network.ProfileClientCache;
import com.respectcraft.profile.BirthOrigin;
import com.respectcraft.profile.BirthTrait;
import com.respectcraft.profile.CharacterAttribute;
import com.respectcraft.profile.CharacterProfile;
import com.respectcraft.profile.Speciality;
import com.respectcraft.skill.SkillDef;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Secao 01, a de entrada: identidade (Speciality e raridade, ou origem legada), traco de nascimento, os seis
 * atributos, pontos de habilidade e um resumo das quatro necessidades ativas. Os numeros sao exatos desde o
 * primeiro quadro; so as barras crescem na revelacao.
 */
final class OverviewView extends HoloView {
    OverviewView(HoloMenu menu) { super(menu, HoloCharacterScreen.Section.OVERVIEW); }

    @Override
    float headerRight(HoloCanvas c, float w) {
        CharacterProfile p = ProfileClientCache.profile();
        String pts = tr("gui.respectcraft.hub.points", p.skillPoints());
        float pw = c.font.width(pts) + 8F;
        c.pill(pts, w - 10F - pw, 7F, p.skillPoints() > 0 ? HoloStyle.GOLD : 0xFF3A4654);
        return w - 14F - pw;
    }

    @Override
    float body(HoloCanvas c, float w, float viewportHeight, float since) {
        CharacterProfile p = ProfileClientCache.profile();
        float x = 10F, iw = w - 20F, y = 3F;
        int i = 1;

        // ---- identidade
        c.stage(since, i++);
        Speciality sp = p.specialityOrNull();
        BirthOrigin origin = p.originOrNull();
        if (sp != null) {
            int col = sp.rarity().color();
            String rarity = tr(sp.rarity().translationKey());
            float pillW = c.font.width(rarity) + 8F;
            c.caption(tr("gui.respectcraft.speciality"), x, y + 2F, c.muted());
            c.pill(rarity, x + iw - pillW, y, col);
            y += 14F;
            String name = tr(sp.translationKey());
            float size = c.fitSize(HoloCanvas.Type.DISPLAY, name, 1.05F, 0.6F, iw);
            c.glow(x + 24F, y + 6F, 30F, Ui.alpha(col, 0.22F));
            c.type(HoloCanvas.Type.DISPLAY, name, x, y, size, col);
            float nameH = HoloCanvas.capHeight(HoloCanvas.Type.DISPLAY, size) + 4F;
            if (c.over(x, y - 2, iw, nameH + 22)) c.tooltip(List.of(
                    Component.literal(name).withStyle(s -> s.withColor(col & 0xFFFFFF).withBold(true)),
                    Component.literal(rarity).withStyle(ChatFormatting.GRAY),
                    Component.translatable(sp.translationKey() + ".desc").withStyle(ChatFormatting.GRAY)));
            y += nameH + 2F;
            c.stage(since, i++);
            y += c.wrap(Component.translatable(sp.translationKey() + ".desc"), x, y, iw, 2, c.muted()) + 4F;
        } else if (p.born() && origin != null) {
            c.caption(tr("gui.respectcraft.origin"), x, y + 2F, c.muted());
            y += 13F;
            c.icon(SkillDef.iconTexture(origin.icon()), x, y - 1F, 12F, 1F);
            c.text(tr("origin.respectcraft." + origin.id()), x + 16F, y + 1F, origin.color(), true);
            y += 13F;
            y += c.wrap(Component.translatable("gui.respectcraft.speciality.legacy_origin"), x, y, iw, 2, c.dim()) + 4F;
        } else {
            c.caption(tr("gui.respectcraft.speciality"), x, y + 2F, c.muted());
            y += 13F;
            y += c.wrap(Component.translatable("gui.respectcraft.hub.unborn"), x, y, iw, 2, HoloStyle.GOLD) + 3F;
            c.button("ov.birth", tr("gui.respectcraft.hub.birth"), x, y, Math.min(iw, 120F), 14F,
                    SkillDef.iconTexture("trevo"), HoloStyle.GREEN, true, false, "!", menu::openRoulette);
            y += 19F;
        }

        // ---- traco de nascimento
        c.stage(since, i++);
        c.divider(x, y, iw, accent());
        y += 5F;
        c.caption(tr("gui.respectcraft.hub.traits"), x, y + 1F, c.muted());
        y += 11F;
        List<BirthTrait> traits = p.birthTraits();
        if (traits.isEmpty()) {
            y += c.wrap(Component.translatable(p.born() ? "gui.respectcraft.trait.none" : "gui.respectcraft.trait.unborn"),
                    x, y, iw, 2, c.dim()) + 2F;
        } else {
            for (BirthTrait t : traits) {
                int col = t.polarity().color();
                String tag = tr("gui.respectcraft.trait.polarity." + t.polarity().id());
                String name = tr(t.translationKey());
                float tagW = c.font.width(tag) + 8F;
                c.text(Ui.fit(c.font, name, (int) (iw - tagW - 6)), x, y + 2F, col, true);
                c.pill(tag, x + iw - tagW, y, col);
                if (c.over(x, y - 1, iw, 13)) c.tooltip(BodyView.traitTooltip(t));
                y += 15F;
            }
        }

        // ---- atributos (exatos; a barra cresce na revelacao)
        c.stage(since, i++);
        c.divider(x, y, iw, accent());
        y += 5F;
        c.caption(tr("gui.respectcraft.holo.attributes"), x, y + 1F, c.muted());
        y += 12F;
        CharacterAttribute[] attrs = CharacterAttribute.values();
        boolean twoColumns = iw >= 250F;
        float colW = twoColumns ? (iw - 12F) / 2F : iw;
        for (int a = 0; a < attrs.length; a++) {
            CharacterAttribute attr = attrs[a];
            float grow = c.stage(since, i + a) / Math.max(0.001F, c.panel.contentAlpha);
            float ax = twoColumns ? x + (a % 2) * (colW + 12F) : x;
            float ay = twoColumns ? y + (a / 2) * 18F : y + a * 17F;
            int v = p.attribute(attr);
            c.text(tr("attribute.respectcraft." + attr.id()), ax, ay, c.text(), false);
            String num = String.valueOf(v);
            c.type(HoloCanvas.Type.MONO, num, ax + colW - c.measure(HoloCanvas.Type.MONO, num, 1.2F), ay + 0.5F, 1.2F, HoloStyle.GOLD);
            c.bar(ax, ay + 10F, colW, 3F, v / (float) CharacterAttribute.MAX * HoloAnim.outCubic(grow), attr.color(),
                    CharacterAttribute.MAX, false);
            if (c.over(ax, ay - 1, colW, 16)) c.tooltip(List.of(
                    Component.translatable("attribute.respectcraft." + attr.id()).withStyle(ChatFormatting.BOLD),
                    Component.literal(v + " / " + CharacterAttribute.MAX).withStyle(ChatFormatting.GRAY)));
        }
        i += attrs.length;
        y += twoColumns ? 3 * 18F + 2F : attrs.length * 17F + 1F;

        // ---- resumo das necessidades ativas (detalhes na secao Body & Traits)
        c.stage(since, i++);
        c.divider(x, y, iw, accent());
        y += 5F;
        c.caption(tr("gui.respectcraft.hub.needs"), x, y + 1F, c.muted());
        y += 12F;
        Needs n = ProfileClientCache.needs();
        float cell = (iw - 3 * 6F) / 4F;
        for (int k = 0; k < Need.ACTIVE.length; k++) {
            Need need = Need.ACTIVE[k];
            float nx = x + k * (cell + 6F);
            boolean critical = BodyView.critical(need, n.get(need));
            String label = Ui.fit(c.font, tr("need.respectcraft." + need.id()), (int) cell);
            c.text(label, nx, y, critical ? HoloStyle.RED : c.muted(), false);
            c.bar(nx, y + 10F, cell, 3F, n.get(need) / (float) Needs.MAX, need.color(), 5, critical);
            if (c.over(nx, y - 1, cell, 15)) c.tooltip(BodyView.needTooltip(need, n));
        }
        y += 18F;
        HoloZone link = c.zone(x, y - 30F, iw, 30F);
        link.click = () -> menu.select(HoloCharacterScreen.Section.BODY);
        return y + 2F;
    }
}
