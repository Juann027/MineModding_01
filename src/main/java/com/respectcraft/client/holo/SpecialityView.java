package com.respectcraft.client.holo;

import com.respectcraft.client.ui.Ui;
import com.respectcraft.network.ProfileClientCache;
import com.respectcraft.profile.BirthOrigin;
import com.respectcraft.profile.CharacterProfile;
import com.respectcraft.profile.Speciality;
import com.respectcraft.profile.SpecialityOdds;
import com.respectcraft.profile.SpecialityRarity;
import com.respectcraft.skill.SkillDef;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Secao 04 "Speciality": Speciality atual com a cor da raridade, descricao completa, Speciality de nascimento quando
 * for diferente e a escada das seis raridades com as chances reais do servidor ({@code SpecialityOddsSync}).
 * Nenhum botao de troca: a troca de Speciality nao existe no jogo.
 */
final class SpecialityView extends HoloView {
    SpecialityView(HoloCharacterScreen screen) { super(screen, HoloCharacterScreen.Section.SPECIALITY); }

    @Override
    int accent() {
        Speciality sp = ProfileClientCache.profile().specialityOrNull();
        return sp == null ? super.accent() : sp.rarity().color();
    }

    @Override
    float body(HoloCanvas c, float w, float viewportHeight, float since) {
        CharacterProfile p = ProfileClientCache.profile();
        SpecialityOdds odds = ProfileClientCache.odds();
        float x = 10F, iw = w - 20F, y = 3F;
        int i = 1;
        Speciality sp = p.specialityOrNull();
        BirthOrigin origin = p.originOrNull();
        c.stage(since, i++);
        if (sp != null) {
            SpecialityRarity rarity = sp.rarity();
            int col = rarity.color();
            // cartao da Speciality com moldura e brilho da raridade
            String rarityName = tr(rarity.translationKey());
            String chance = SpecialityOdds.formatPercent(odds.permille(rarity));
            String name = tr(sp.translationKey());
            Component desc = Component.translatable(sp.translationKey() + ".desc");
            float size = c.fitSize(HoloCanvas.Type.DISPLAY, name, 1.25F, 0.6F, iw - 16F);
            float nameH = HoloCanvas.capHeight(HoloCanvas.Type.DISPLAY, size) + 4F;
            int descLines = Math.min(6, c.lineCount(desc, iw - 16F));
            float cardH = 16F + nameH + 6F + descLines * 10F + 8F;
            float pulse = 0.8F + 0.2F * (float) Math.sin(c.time * 2.2F);
            c.fill(x, y, iw, cardH, Ui.alpha(col, 0.08F));
            c.frame(x, y, iw, cardH, Ui.alpha(col, 0.55F * pulse));
            c.brackets(x - 2F, y - 2F, iw + 4F, cardH + 4F, 7F, col);
            c.glow(x + iw * 0.3F, y + 20F + nameH / 2F, iw * 0.35F, Ui.alpha(col, 0.16F * pulse));
            float pw = c.pill(rarityName, x + 6F, y + 5F, col);
            c.pill(chance, x + 6F + pw + 3F, y + 5F, 0xFF2A3644);
            c.caption(tr("gui.respectcraft.holo.current_speciality"), x + iw - 6F
                    - c.measure(HoloCanvas.Type.LABEL, tr("gui.respectcraft.holo.current_speciality").toUpperCase(), 1F), y + 8F, c.muted());
            float ny = y + 20F;
            c.type(HoloCanvas.Type.DISPLAY, name, x + 8F, ny, size, col);
            c.stage(since, i++);
            c.wrap(desc, x + 8F, ny + nameH + 5F, iw - 16F, 6, c.text());
            if (c.over(x, y, iw, cardH)) c.tooltip(List.of(Component.literal(name).withStyle(s -> s.withColor(col & 0xFFFFFF).withBold(true)),
                    Component.literal(rarityName + " · " + chance).withStyle(ChatFormatting.GRAY), desc.copy().withStyle(ChatFormatting.GRAY)));
            y += cardH + 8F;
            Speciality birth = p.birthSpecialityOrNull();
            if (birth != null && birth != sp) {
                c.stage(since, i++);
                String line = tr("gui.respectcraft.holo.birth_speciality", tr(birth.translationKey()));
                c.text(Ui.fit(c.font, line, (int) iw), x, y, birth.rarity().color(), false);
                y += 13F;
            }
        } else if (p.born() && origin != null) {
            c.caption(tr("gui.respectcraft.origin"), x, y + 1F, c.muted());
            y += 13F;
            c.icon(SkillDef.iconTexture(origin.icon()), x, y - 1F, 14F, 1F);
            c.text(tr("origin.respectcraft." + origin.id()), x + 18F, y + 2F, origin.color(), true);
            y += 16F;
            y += c.wrap(Component.translatable("gui.respectcraft.speciality.legacy_origin"), x, y, iw, 3, c.muted()) + 6F;
        } else {
            y += c.wrap(Component.translatable("gui.respectcraft.hub.unborn"), x, y, iw, 2, HoloStyle.GOLD) + 4F;
            c.button("sp.birth", tr("gui.respectcraft.hub.birth"), x, y, Math.min(iw, 130F), 14F, SkillDef.iconTexture("trevo"),
                    HoloStyle.GREEN, true, false, "!", screen::openRoulette);
            y += 22F;
        }

        // ---- escada de raridades (mais alta em cima) com as chances do servidor
        c.stage(since, i++);
        c.divider(x, y, iw, accent());
        y += 5F;
        c.caption(tr("gui.respectcraft.holo.rarity"), x, y + 1F, c.muted());
        String note = tr("gui.respectcraft.roulette.odds_title");
        c.text(Ui.fit(c.font, note, (int) (iw - 60)), x + iw - Math.min(c.font.width(note), iw - 60), y, c.dim(), false);
        y += 13F;
        SpecialityRarity[] all = SpecialityRarity.values();
        for (int r = all.length - 1; r >= 0; r--) {
            SpecialityRarity rarity = all[r];
            c.stage(since, i++);
            boolean mine = sp != null && sp.rarity() == rarity;
            int col = rarity.color();
            float rowH = 13F;
            if (mine) {
                c.fill(x, y - 2F, iw, rowH, Ui.alpha(col, 0.16F));
                c.frame(x, y - 2F, iw, rowH, Ui.alpha(col, 0.7F));
                c.glow(x + 6F, y + 4F, 12F, Ui.alpha(col, 0.5F));
            }
            c.fill(x + 3F, y + 1F, 6F, 6F, col);
            String rn = tr(rarity.translationKey());
            c.text(rn, x + 14F, y, mine ? 0xFFFFFFFF : Ui.lerpColor(col, c.muted(), 0.35F), mine);
            String pct = SpecialityOdds.formatPercent(odds.permille(rarity));
            c.type(HoloCanvas.Type.MONO, pct, x + iw - 5F - c.measure(HoloCanvas.Type.MONO, pct, 1.15F), y + 1F, 1.15F, mine ? col : c.dim());
            // barra de chance proporcional (a roda tem fatias iguais; aqui e so a leitura das chances)
            float barX = x + 14F + c.font.width(rn) + 8F, barW = iw - (barX - x) - 46F;
            if (barW > 10F) c.fill(barX, y + 3F, barW * Math.min(1F, odds.permille(rarity) / 500F), 2F, Ui.alpha(col, mine ? 0.9F : 0.4F));
            y += rowH + 1F;
        }
        return y + 2F;
    }
}
