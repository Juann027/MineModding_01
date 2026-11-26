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
 * Elementos de navegacao que flutuam no espaco: a fita de identidade acima do arco (nome, Speciality, traco,
 * pontos e as acoes que o menu antigo tinha: aparencia, roleta, Display, fechar) e os chips de secao numa fileira
 * curva abaixo dele. So exibicao das caches e atalhos para telas que ja existiam.
 */
final class HoloNav {
    private final HoloMenu menu;

    HoloNav(HoloMenu menu) { this.menu = menu; }

    /** Fita de identidade: marca e nome a esquerda, Speciality e traco no meio, pontos e acoes a direita. */
    void drawHeader(HoloCanvas c, float w, float h) {
        c.glass(w, h, HoloStyle.CYAN);
        CharacterProfile p = ProfileClientCache.profile();
        float since = menu.time() - 0.12F;
        c.stage(since, 0);
        boolean small = h < 28F;
        float mid = h / 2F;
        c.type(HoloCanvas.Type.LABEL, "RESPECTCRAFT", 8F, mid - 9F, 0.95F, HoloStyle.CYAN);
        String name = c.mc.player == null ? "" : c.mc.player.getGameProfile().getName();
        c.text(Ui.fit(c.font, name, 70), 8F, mid + 1F, c.text(), false);

        // acoes a direita (icones com dica)
        float bs = h - 8F;
        float bx = w - 6F - 5 * (bs + 3F);
        boolean born = p.born();
        action(c, "act.edit", bx, 4F, bs, SkillDef.iconTexture("identidade_dinheiro"), null, HoloStyle.CYAN_SOFT,
                "gui.respectcraft.hub.edit", "", menu::openCreator);
        action(c, "act.birth", bx + (bs + 3F), 4F, bs, SkillDef.iconTexture("trevo"), null, born ? HoloStyle.GOLD : HoloStyle.GREEN,
                born ? "gui.respectcraft.hub.birth_done" : "gui.respectcraft.hub.birth", born ? "" : "!", menu::openRoulette);
        // Etapa 4.11: avisos holograficos (selo = nao lidos) leva a secao Alerts.
        int unread = com.respectcraft.network.NoticeClientCache.unread();
        action(c, "act.alerts", bx + 2 * (bs + 3F), 4F, bs, null, "✉", unread > 0 ? HoloStyle.MAGENTA : HoloStyle.CYAN_SOFT,
                "gui.respectcraft.holo.alerts.open", unread > 0 ? (unread > 9 ? "+" : String.valueOf(unread)) : "",
                () -> menu.select(HoloCharacterScreen.Section.ALERTS));
        action(c, "act.display", bx + 3 * (bs + 3F), 4F, bs, null, "⚙", HoloStyle.VIOLET, "gui.respectcraft.holo.display", "",
                menu::openSettings);
        action(c, "act.close", bx + 4 * (bs + 3F), 4F, bs, null, "✕", HoloStyle.RED, "gui.respectcraft.hub.close", "",
                menu::requestClose);

        // pontos
        String pts = String.valueOf(p.skillPoints());
        float ptsW = c.measure(HoloCanvas.Type.MONO, pts, 1.2F);
        float px = bx - 10F - ptsW;
        c.type(HoloCanvas.Type.MONO, pts, px, mid - 3.5F, 1.2F, p.skillPoints() > 0 ? HoloStyle.GOLD : c.dim());
        if (!small) c.caption(HoloView.tr("gui.respectcraft.holo.points_short"), px - c.measure(HoloCanvas.Type.LABEL,
                HoloView.tr("gui.respectcraft.holo.points_short").toUpperCase(), 1F) - 4F, mid - 3F, c.dim());
        if (c.over(px - 30F, 0, ptsW + 34F, h)) c.tooltip(List.of(Component.translatable("gui.respectcraft.hub.points", p.skillPoints())));

        // Speciality e traco no meio
        float sx = 86F, sw = px - 40F - sx;
        c.stage(since, 1);
        Speciality sp = p.specialityOrNull();
        BirthOrigin origin = p.originOrNull();
        if (sw > 30F) {
            if (sp != null) {
                int col = sp.rarity().color();
                String spName = HoloView.tr(sp.translationKey());
                String rarity = HoloView.tr(sp.rarity().translationKey());
                c.text(Ui.fit(c.font, spName, (int) sw), sx, mid - 9F, col, true);
                c.text(Ui.fit(c.font, rarity, (int) sw), sx, mid + 1F, Ui.lerpColor(col, c.muted(), 0.3F), false);
                if (c.over(sx, 0, sw, h)) c.tooltip(List.of(Component.literal(spName).withStyle(s -> s.withColor(col & 0xFFFFFF).withBold(true)),
                        Component.literal(rarity).withStyle(ChatFormatting.GRAY),
                        Component.translatable(sp.translationKey() + ".desc").withStyle(ChatFormatting.GRAY)));
            } else if (p.born() && origin != null) {
                c.text(Ui.fit(c.font, HoloView.tr("origin.respectcraft." + origin.id()), (int) sw), sx, mid - 9F, origin.color(), true);
                c.text(Ui.fit(c.font, HoloView.tr("gui.respectcraft.origin"), (int) sw), sx, mid + 1F, c.dim(), false);
            } else {
                c.text(Ui.fit(c.font, HoloView.tr("gui.respectcraft.hub.unborn"), (int) sw), sx, mid - 4F, HoloStyle.GOLD, false);
            }
            List<BirthTrait> traits = p.birthTraits();
            if (!traits.isEmpty() && sp != null) {
                BirthTrait t = traits.get(0);
                String tName = HoloView.tr(t.translationKey());
                float rw = c.font.width(HoloView.tr(sp.rarity().translationKey())) + 8F;
                if (sw - rw > 40F) c.text(Ui.fit(c.font, "· " + tName, (int) (sw - rw)), sx + rw, mid + 1F, t.polarity().color(), false);
            }
        }
    }

    private void action(HoloCanvas c, String key, float x, float y, float s, net.minecraft.resources.ResourceLocation icon,
                        String glyph, int accent, String labelKey, String badge, Runnable run) {
        HoloZone z = c.zone(x, y, s, s);
        z.click = run;
        boolean hot = c.over(x, y, s, s);
        float k = c.hoverAnim(key, hot);
        c.fill(x, y, s, s, Ui.alpha(accent, 0.08F + 0.2F * k));
        c.frame(x, y, s, s, Ui.alpha(accent, 0.35F + 0.5F * k));
        if (k > 0.01F) c.glow(x + s / 2F, y + s / 2F, s, Ui.alpha(accent, 0.4F * k));
        if (icon != null) c.icon(icon, x + (s - 12F) / 2F, y + (s - 12F) / 2F, 12F, 1F);
        if (glyph != null) c.textCentered(glyph, x + s / 2F + 0.5F, y + (s - 8F) / 2F, Ui.lerpColor(accent, 0xFFFFFFFF, 0.3F + 0.5F * k), false);
        if (!badge.isEmpty()) {
            c.fill(x + s - 5F, y - 1F, 6F, 6F, accent);
            c.text(badge, x + s - 3.5F, y - 1F, 0xFF101317, false);
        }
        menu.hoverSound(key, hot);
        if (hot) c.tooltip(List.of(Component.translatable(labelKey)));
    }

    /** Ponto aceso no chip: avisos nao lidos (Alerts) ou recompensa contra mim (Bounty). */
    static int badge(HoloCharacterScreen.Section s) {
        return switch (s) {
            case ALERTS -> com.respectcraft.network.NoticeClientCache.unread();
            case BOUNTY -> com.respectcraft.network.BountyClientCache.onMe();
            default -> 0;
        };
    }

    /** Chip de secao na fileira curva: icone e nome; o ativo acende na cor da secao. */
    void drawChip(HoloCanvas c, HoloCharacterScreen.Section s, float w, float h, boolean active) {
        float since = menu.time() - 0.3F;
        c.alpha = 1F;
        boolean hot = c.over(0, 0, w, h);
        float k = c.hoverAnim("chip." + s.id, hot);
        float on = c.hoverAnim("chip.on." + s.id, active);
        float o = c.hc ? 1F : c.opacity;
        c.fill(0, 0, w, h, Ui.alpha(c.hc ? HoloStyle.GLASS_HC : HoloStyle.GLASS_TOP, 0.82F * o));
        c.fill(0, 0, w, h, Ui.alpha(s.accent, 0.06F + 0.14F * k + 0.22F * on));
        c.frame(0, 0, w, h, Ui.alpha(s.accent, 0.3F + 0.35F * k + 0.35F * on));
        if (on > 0.01F) {
            c.fill(0, h - 2F, w, 2F, Ui.alpha(s.accent, on));
            c.glow(w / 2F, h, w * 0.5F, Ui.alpha(s.accent, 0.5F * on));
        }
        c.stage(since, s.ordinal());
        float size = Math.min(12F, h - 4F);
        String label = HoloView.tr(s.key());
        // Etapa 4.11: chip estreito (sete secoes em telas pequenas) mostra so o icone; o nome fica na dica.
        boolean compact = w < 58F || c.font.width(label) > w - size - 10F && w < 66F;
        if (compact) {
            c.icon(s.icon, (w - size) / 2F, (h - size) / 2F, size, 1F);
        } else {
            c.icon(s.icon, 4F, (h - size) / 2F, size, 1F);
            String shown = Ui.fit(c.font, label, (int) (w - size - 10F));
            c.text(shown, 6F + size, (h - 8F) / 2F, active ? 0xFFFFFFFF : Ui.lerpColor(c.muted(), c.text(), k), false);
        }
        int badge = badge(s);
        if (badge > 0) {
            int col = s == HoloCharacterScreen.Section.BOUNTY ? HoloStyle.RED : s.accent;
            float pulse = 0.75F + 0.25F * (float) Math.sin(c.time * 5F);
            c.fill(w - 7F, 1F, 6F, 6F, Ui.alpha(col, pulse));
            c.glow(w - 4F, 4F, 6F, Ui.alpha(col, 0.5F * pulse));
        }
        HoloZone z = c.zone(0, 0, w, h);
        z.click = () -> menu.select(s);
        menu.hoverSound("chip." + s.id, hot);
        if (hot) c.tooltip(List.of(Component.translatable(s.key()).withStyle(ChatFormatting.BOLD),
                (menu.world ? Component.translatable("gui.respectcraft.holo.key_hint_world")
                        : Component.translatable("gui.respectcraft.holo.key_hint", s.ordinal() + 1)).withStyle(ChatFormatting.GRAY)));
    }
}
