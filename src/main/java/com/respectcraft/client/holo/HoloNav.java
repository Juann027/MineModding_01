package com.respectcraft.client.holo;

import com.respectcraft.client.ui.Ui;
import com.respectcraft.network.ProfileClientCache;
import com.respectcraft.profile.BirthOrigin;
import com.respectcraft.profile.BirthTrait;
import com.respectcraft.profile.CharacterProfile;
import com.respectcraft.profile.Speciality;
import com.respectcraft.skill.SkillDef;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Barra lateral (secoes e acoes existentes: aparencia, roleta, Display, fechar) e placa de identidade sob o
 * holograma do jogador. So exibicao das caches e atalhos para telas que ja existiam.
 */
final class HoloNav {
    private final HoloCharacterScreen screen;
    private float indicatorY = -1F;
    private final float[] indicatorVel = new float[1];

    HoloNav(HoloCharacterScreen screen) { this.screen = screen; }

    void drawNav(HoloCanvas c, float w, float h) {
        c.glass(w, h, HoloStyle.CYAN);
        CharacterProfile p = ProfileClientCache.profile();
        float since = screen.time() - 0.14F;
        c.stage(since, 0);
        c.type(HoloCanvas.Type.LABEL, "RESPECTCRAFT", 9F, 7F, 1.1F, HoloStyle.CYAN);
        c.caption(HoloView.tr("gui.respectcraft.holo.title"), 9F, 17F, c.muted());
        String name = c.mc.player == null ? "" : c.mc.player.getGameProfile().getName();
        c.text(Ui.fit(c.font, name, (int) (w - 18)), 9F, 27F, c.text(), false);
        c.alpha = c.panel.contentAlpha;
        c.divider(7F, 39F, w - 14F, HoloStyle.CYAN);

        // abas das secoes: altura se ajusta a tela (menor GUI = 320x240)
        HoloCharacterScreen.Section[] sections = HoloCharacterScreen.Section.values();
        float actionsH = 4 * 15F + 8F;
        float tabH = Math.max(14F, Math.min(21F, (h - 44F - actionsH - 8F) / sections.length));
        float y = 44F;
        float target = y + screen.section().ordinal() * tabH;
        if (indicatorY < 0F || HoloConfig.reducedMotion()) indicatorY = target;
        else indicatorY = HoloAnim.spring(indicatorY, target, indicatorVel, 220F, 22F, c.dt);
        // marcador que desliza ate a aba ativa (por baixo dos botoes)
        int accent = screen.section().accent;
        c.alpha = c.panel.contentAlpha;
        c.fill(3F, indicatorY + 2F, 2F, tabH - 6F, accent);
        c.glow(4F, indicatorY + tabH / 2F - 1F, tabH * 0.8F, Ui.alpha(accent, 0.6F));
        for (int i = 0; i < sections.length; i++) {
            HoloCharacterScreen.Section s = sections[i];
            c.stage(since, i + 1);
            String badge = s == HoloCharacterScreen.Section.SKILLS && p.skillPoints() > 0 ? String.valueOf(p.skillPoints()) : "";
            String key = "nav." + s.id;
            boolean hot = c.button(key, HoloView.tr(s.key()), 7F, y, w - 13F, tabH - 2F, s.icon, s.accent, true,
                    s == screen.section(), badge, () -> screen.select(s));
            screen.hoverSound(key, hot);
            if (hot) c.tooltip(List.of(Component.translatable(s.key()).withStyle(ChatFormatting.BOLD),
                    Component.translatable("gui.respectcraft.holo.key_hint", i + 1).withStyle(ChatFormatting.GRAY)));
            y += tabH;
        }

        // acoes que ja existiam no menu K
        float ay = h - actionsH;
        c.alpha = c.panel.contentAlpha;
        c.divider(7F, ay - 4F, w - 14F, HoloStyle.VIOLET);
        c.stage(since, 7);
        boolean born = p.born();
        c.button("act.edit", HoloView.tr("gui.respectcraft.hub.edit"), 7F, ay, w - 13F, 13F,
                SkillDef.iconTexture("identidade_dinheiro"), HoloStyle.CYAN_SOFT, true, false, "", screen::openCreator);
        c.stage(since, 8);
        c.button("act.birth", HoloView.tr(born ? "gui.respectcraft.hub.birth_done" : "gui.respectcraft.hub.birth"), 7F, ay + 15F,
                w - 13F, 13F, SkillDef.iconTexture("trevo"), born ? HoloStyle.GOLD : HoloStyle.GREEN, true, false,
                born ? "" : "!", screen::openRoulette);
        c.stage(since, 9);
        c.button("act.display", HoloView.tr("gui.respectcraft.holo.display"), 7F, ay + 30F, w - 13F, 13F, null,
                HoloStyle.VIOLET, true, false, "", screen::openSettings);
        c.stage(since, 10);
        c.button("act.close", HoloView.tr("gui.respectcraft.hub.close"), 7F, ay + 45F, w - 13F, 13F, null,
                HoloStyle.RED, true, false, "", screen::onClose);
    }

    /** Placa sob o holograma: nome, Speciality com a cor da raridade, traco e pontos. */
    void drawPlate(HoloCanvas c, float w, float h) {
        c.glass(w, h, HoloStyle.VIOLET);
        CharacterProfile p = ProfileClientCache.profile();
        float since = screen.time() - 0.3F;
        c.stage(since, 0);
        String name = c.mc.player == null ? "" : c.mc.player.getGameProfile().getName();
        c.text(Ui.fit(c.font, name, (int) (w - 12)), 6F, 5F, c.text(), true);
        c.stage(since, 1);
        Speciality sp = p.specialityOrNull();
        BirthOrigin origin = p.originOrNull();
        float y = 17F;
        if (sp != null) {
            int col = sp.rarity().color();
            String spName = HoloView.tr(sp.translationKey());
            String rarity = HoloView.tr(sp.rarity().translationKey());
            c.text(Ui.fit(c.font, spName, (int) (w - 12)), 6F, y, col, false);
            if (c.over(0, y - 2, w, 12)) c.tooltip(List.of(Component.literal(spName).withStyle(s -> s.withColor(col & 0xFFFFFF).withBold(true)),
                    Component.literal(rarity).withStyle(ChatFormatting.GRAY),
                    Component.translatable(sp.translationKey() + ".desc").withStyle(ChatFormatting.GRAY)));
            y += 11F;
            c.pill(Ui.fit(c.font, rarity, (int) (w - 20)), 6F, y, col);
        } else if (p.born() && origin != null) {
            c.text(Ui.fit(c.font, HoloView.tr("origin.respectcraft." + origin.id()), (int) (w - 12)), 6F, y, origin.color(), false);
            y += 11F;
            c.text(Ui.fit(c.font, HoloView.tr("gui.respectcraft.origin"), (int) (w - 12)), 6F, y + 1, c.dim(), false);
        } else {
            c.text(Ui.fit(c.font, HoloView.tr("gui.respectcraft.hub.unborn"), (int) (w - 12)), 6F, y, c.muted(), false);
            y += 11F;
        }
        y += 14F;
        c.stage(since, 2);
        List<BirthTrait> traits = p.birthTraits();
        if (!traits.isEmpty()) {
            BirthTrait t = traits.get(0);
            String tName = HoloView.tr(t.translationKey());
            c.text(Ui.fit(c.font, tName, (int) (w - 34)), 6F, y, t.polarity().color(), false);
        } else {
            c.text(HoloView.tr("gui.respectcraft.trait.none_short"), 6F, y, c.dim(), false);
        }
        String pts = String.valueOf(p.skillPoints());
        c.type(HoloCanvas.Type.MONO, pts, w - 8F - c.measure(HoloCanvas.Type.MONO, pts, 1.2F), y, 1.2F,
                p.skillPoints() > 0 ? HoloStyle.GOLD : c.dim());
        if (c.over(w - 30, y - 3, 30, 12)) c.tooltip(List.of(Component.translatable("gui.respectcraft.hub.points", p.skillPoints())));
    }
}
