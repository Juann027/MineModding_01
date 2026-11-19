package com.respectcraft.client.holo;

import com.respectcraft.character.CharacterAppearance;
import com.respectcraft.client.ui.Ui;
import com.respectcraft.network.CharacterAppearanceClientCache;
import com.respectcraft.skill.SkillDef;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;

/**
 * Secao 02 "Character": palco transparente onde o proprio jogador (rig GeckoLib, Body Layers, skin Classic/Slim)
 * fica de pe em 3D na frente do painel, girado por arrasto, e a ficha da aparencia confirmada pelo servidor com o
 * atalho para o Character Creator que ja existia. O criador nao foi refeito.
 */
final class CharacterView extends HoloView {
    CharacterView(HoloCharacterScreen screen) { super(screen, HoloCharacterScreen.Section.CHARACTER); }

    @Override
    boolean fitsContent() { return false; }

    /** Largura do palco dentro do painel largo (o holograma 3D fica centrado nele). */
    static float stageWidth(float panelW) { return Math.max(84F, Math.min(200F, panelW * 0.44F)); }

    @Override
    float body(HoloCanvas c, float w, float viewportHeight, float since) {
        float sw = stageWidth(w);
        float x = 10F, y = 2F;
        int i = 1;

        // ---- palco: janela com grade; o jogador 3D e desenhado na passada em perspectiva, na frente daqui
        c.stage(since, i++);
        float sh = viewportHeight - 6F;
        c.fill(x, y, sw, sh, 0x50020A14);
        c.frame(x, y, sw, sh, Ui.alpha(accent(), 0.3F));
        c.g.drawManaged(() -> {
            for (float gx = x + 12; gx < x + sw; gx += 16) c.fill(gx, y + 1, 1, sh - 2, 0x0C8FEFFF);
            for (float gy = y + 12; gy < y + sh; gy += 16) c.fill(x + 1, gy, sw - 2, 1, 0x0C8FEFFF);
        });
        c.brackets(x + 3, y + 3, sw - 6, sh - 6, 6F, Ui.alpha(accent(), 0.8F));
        c.caption(tr("gui.respectcraft.holo.hologram"), x + 7F, y + 6F, c.muted());
        String hint = tr("gui.respectcraft.holo.drag_rotate");
        c.text(Ui.fit(c.font, hint, (int) (sw - 12)), x + 6F, y + sh - 12F, c.dim(), false);

        // ---- ficha da aparencia (cache confirmada pelo servidor)
        float ix = x + sw + 12F, iw = w - ix - 10F;
        float iy = y;
        c.stage(since, i++);
        var player = c.mc.player;
        String name = player == null ? "" : player.getGameProfile().getName();
        float size = c.fitSize(HoloCanvas.Type.DISPLAY, name, 1.1F, 0.6F, iw);
        c.type(HoloCanvas.Type.DISPLAY, name, ix, iy + 1F, size, c.text());
        iy += HoloCanvas.capHeight(HoloCanvas.Type.DISPLAY, size) + 7F;
        if (player instanceof AbstractClientPlayer client) {
            boolean slim = client.getSkin().model() == PlayerSkin.Model.SLIM;
            String model = tr("gui.respectcraft.holo.skin_model." + (slim ? "slim" : "wide"));
            c.caption(tr("gui.respectcraft.holo.skin_model"), ix, iy + 2F, c.muted());
            if (iw < 120F) {
                iy += 11F;
                c.pill(model, ix, iy, HoloStyle.CYAN_SOFT);
            } else {
                c.pillRight(model, ix + iw, iy, HoloStyle.CYAN_SOFT);
            }
            iy += 15F;
        }
        c.divider(ix, iy, iw, accent());
        iy += 6F;
        CharacterAppearance look = player == null ? CharacterAppearance.defaults()
                : CharacterAppearanceClientCache.confirmed(player.getUUID());
        String k = "gui.respectcraft.creator.";
        iy = row(c, since, i++, ix, iy, iw, tr(k + "body"), tr(k + "body." + CharacterAppearance.BODY_IDS[look.bodyType()]), 0);
        iy = row(c, since, i++, ix, iy, iw, tr(k + "skin"), tr(k + "skin." + CharacterAppearance.SKIN_IDS[look.skinTone()]),
                look.customSkinTone() ? CharacterAppearance.SKIN_ARGB[look.skinTone()] : 0);
        iy = row(c, since, i++, ix, iy, iw, tr(k + "face"), tr(k + "face." + CharacterAppearance.FACE_IDS[look.face()]), 0);
        iy = row(c, since, i++, ix, iy, iw, tr(k + "hair"), tr(k + "hair." + CharacterAppearance.HAIR_IDS[look.hairStyle()]), 0);
        iy = row(c, since, i++, ix, iy, iw, tr(k + "color"), tr(k + "color." + CharacterAppearance.COLOR_IDS[look.hairColor()]),
                CharacterAppearance.COLOR_ARGB[look.hairColor()]);
        iy += 6F;
        c.stage(since, i++);
        boolean hot = c.button("ch.edit", tr("gui.respectcraft.holo.edit_appearance"), ix, iy, Math.min(iw, 150F), 16F,
                SkillDef.iconTexture("identidade_dinheiro"), HoloStyle.CYAN, true, false, "", screen::openCreator);
        if (hot) c.tooltip(List.of(Component.translatable("gui.respectcraft.holo.edit_appearance").withStyle(ChatFormatting.BOLD),
                Component.translatable("gui.respectcraft.holo.edit_appearance.desc").withStyle(ChatFormatting.GRAY)));
        iy += 22F;
        return Math.max(iy, y + sh);
    }

    private float row(HoloCanvas c, float since, int index, float x, float y, float w, String label, String value, int swatch) {
        c.stage(since, index);
        float right = x + w;
        if (w < 120F) {
            // painel estreito (menor GUI): rotulo em cima, valor embaixo
            c.text(Ui.fit(c.font, label, (int) w), x, y, c.muted(), false);
            if (swatch != 0) {
                c.fill(right - 9F, y + 10F, 9F, 9F, 0xFF000000);
                c.fill(right - 8F, y + 11F, 7F, 7F, swatch);
            }
            c.text(Ui.fit(c.font, value, (int) (w - (swatch != 0 ? 13 : 0))), x, y + 10F, c.text(), false);
            c.fill(x, y + 21F, w, 1F, 0x18FFFFFF);
            return y + 24F;
        }
        c.text(Ui.fit(c.font, label, (int) (w * 0.45F)), x, y, c.muted(), false);
        if (swatch != 0) {
            c.fill(right - 9F, y - 1F, 9F, 9F, 0xFF000000);
            c.fill(right - 8F, y, 7F, 7F, swatch);
            right -= 13F;
        }
        String shown = Ui.fit(c.font, value, (int) (w * 0.55F - (swatch != 0 ? 13 : 0)));
        c.textRight(shown, right, y, c.text(), false);
        c.fill(x, y + 11F, w, 1F, 0x18FFFFFF);
        return y + 15F;
    }
}
