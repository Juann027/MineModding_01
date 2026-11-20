package com.respectcraft.client.holo;

import com.respectcraft.character.CharacterAppearance;
import com.respectcraft.client.character.AppearanceDraft;
import com.respectcraft.client.character.CharacterCreatorKeybinds;
import com.respectcraft.client.ui.Ui;
import com.respectcraft.network.ProfileClientCache;
import java.util.List;
import java.util.function.IntConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Criador de personagem holografico (Etapa 4.8.4-C), janela na frente do arco no mundo. So as tres opcoes
 * aprovadas: Body, Hairstyle e Hair Color (Skin e Face sairam). O proprio jogador (rig GeckoLib + Body Layers) fica
 * de pe no palco da esquerda como previa ao vivo do rascunho, girado por arrasto. Seletores com setas grandes e a
 * roda do mouse sobre o seletor (mira ou modo cursor). Save so termina com a confirmacao do servidor
 * ({@link AppearanceDraft}); depois segue para a roleta (personagem novo) ou volta para a secao Character.
 */
final class CreatorView extends HoloPopup {
    private static final String K = "gui.respectcraft.creator.";
    static final float W = 400F, H = 252F;
    /** Palco do holograma (coordenadas locais da janela). */
    static final float STAGE_X = 10F, STAGE_Y = 32F, STAGE_W = 150F;
    static final float STAGE_H = H - STAGE_Y - 12F;

    private final AppearanceDraft draft = new AppearanceDraft();
    /** Aberta antes de existir aparencia: Back fecha os paineis (nao ha Hub sem personagem). */
    private final boolean firstTime = AppearanceDraft.firstTime();

    private final HoloZone.Drag rotate = new HoloZone.Drag() {
        private float lastX;
        @Override public void press(float px, float py) {
            lastX = px;
            menu.rotatingPlayer = true;
        }
        @Override public void move(float px, float py) {
            menu.rotatePlayer((px - lastX) * 1.6F);
            lastX = px;
        }
        @Override public void release(float px, float py, boolean click) { menu.rotatingPlayer = false; }
    };

    CreatorView(HoloMenu menu) { super(menu, Kind.CREATOR); }

    @Override float width() { return W; }
    @Override float height() { return H; }
    @Override int accent() { return HoloStyle.CYAN_SOFT; }

    /** Pes do holograma no palco (local) e a altura dele (unidades de layout). */
    static float feetX() { return STAGE_X + STAGE_W / 2F; }
    static float feetY() { return STAGE_Y + STAGE_H - 50F; }
    static float hologramHeight() { return STAGE_H - 92F; }

    @Override
    void tick() {
        if (draft.tick()) {
            Ui.playUi(com.respectcraft.registry.ModSounds.UI_HOVER.get(), 1.3F, 0.6F);
            menu.creatorSaved(!ProfileClientCache.profile().born());
        }
    }

    @Override
    void closed() { draft.discard(); }

    private void back() {
        if (firstTime) menu.requestClose();
        else menu.closePopup();
    }

    @Override
    void draw(HoloCanvas c, float w, float h, float since) {
        header(c, w, since, KEY + "creator", firstTime ? 1 : 0);
        int i = 1;

        // ---- palco do holograma (o jogador 3D e desenhado na passada em perspectiva, na frente daqui)
        c.stage(since, i++);
        float x = STAGE_X, y = STAGE_Y, sw = STAGE_W, sh = STAGE_H;
        c.fill(x, y, sw, sh, 0x50020A14);
        c.frame(x, y, sw, sh, Ui.alpha(accent(), 0.3F));
        c.g.drawManaged(() -> {
            for (float gx = x + 12; gx < x + sw; gx += 16) c.fill(gx, y + 1, 1, sh - 2, 0x0C8FEFFF);
            for (float gy = y + 12; gy < y + sh; gy += 16) c.fill(x + 1, gy, sw - 2, 1, 0x0C8FEFFF);
        });
        c.brackets(x + 3, y + 3, sw - 6, sh - 6, 6F, Ui.alpha(accent(), 0.8F));
        c.caption(HoloView.tr("gui.respectcraft.holo.preview"), x + 7F, y + 6F, c.muted());
        c.zone(x, y, sw, sh).drag = rotate;
        String hint = HoloView.tr("gui.respectcraft.holo.drag_rotate");
        c.text(Ui.fit(c.font, hint, (int) (sw - 14)), x + 7F, y + 17F, c.dim(), false);
        c.stage(since, i++);
        boolean randHot = c.button("cr.random", HoloView.tr(K + "random"), x + 8F, y + sh - 25F, sw - 16F, 17F, null, 0xFF8FB3C9,
                !draft.saving(), false, "", () -> {
                    draft.randomize();
                    menu.burstOnPopup(feetX(), feetY() - hologramHeight() * 0.5F, HoloStyle.GOLD, 22, 55F);
                });
        menu.hoverSound("cr.random", randHot);

        // ---- opcoes
        float ix = x + sw + 14F, iw = w - ix - 12F;
        float oy = STAGE_Y + 2F;
        c.stage(since, i++);
        oy = selector(c, "body", ix, oy, iw, HoloView.tr(K + "body"),
                HoloView.tr(K + "body." + CharacterAppearance.BODY_IDS[draft.bodyType()]), d -> draft.setBodyType(draft.bodyType() + d));
        c.stage(since, i++);
        oy = selector(c, "hair", ix, oy, iw, HoloView.tr(K + "hair"),
                HoloView.tr(K + "hair." + CharacterAppearance.HAIR_IDS[draft.hairStyle()]), d -> draft.setHairStyle(draft.hairStyle() + d));
        c.stage(since, i++);
        oy = palette(c, ix, oy, iw);

        // ---- dica e estado do salvamento
        c.stage(since, i++);
        float footY = h - 30F;
        AppearanceDraft.State state = draft.state();
        if (state == AppearanceDraft.State.SAVING || state == AppearanceDraft.State.ERROR) {
            boolean error = state == AppearanceDraft.State.ERROR;
            Component msg = Component.translatable(K + (error ? "save_error" : "saving"));
            c.wrap(msg, ix, footY - 22F, iw, 2, error ? HoloStyle.RED : HoloStyle.GREEN);
        } else if (oy + 10F <= footY - 4F) {
            c.wrap(Component.translatable(KEY + "creator.hint", CharacterCreatorKeybinds.cursorKey().getTranslatedKeyMessage()),
                    ix, oy, iw, Math.max(1, (int) ((footY - 4F - oy) / 10F)), c.dim());
        }

        // ---- Back e Save
        c.stage(since, i++);
        float bw = 84F;
        boolean backHot = c.button("cr.back", HoloView.tr(KEY + "back"), ix, footY, bw, 20F, null, HoloStyle.CYAN, true, false, "",
                this::back);
        menu.hoverSound("cr.back", backHot);
        if (backHot) c.tooltip(List.of(Component.translatable(KEY + (firstTime ? "back.first" : "back.creator"))
                .withStyle(ChatFormatting.GRAY)));
        float saveW = Math.min(130F, iw - bw - 8F);
        boolean saveHot = c.button("cr.save", HoloView.tr(K + "save"), ix + iw - saveW, footY, saveW, 20F, null, HoloStyle.GREEN,
                !draft.saving(), true, "", () -> {
                    if (draft.save()) menu.burstOnPopup(ix + iw - saveW / 2F, footY + 10F, HoloStyle.GREEN, 14, 40F);
                });
        menu.hoverSound("cr.save", saveHot);
        if (saveHot) c.tooltip(List.of(Component.translatable(K + "save").withStyle(ChatFormatting.BOLD),
                Component.translatable(KEY + (ProfileClientCache.profile().born() ? "save.desc" : "save.desc_first"))
                        .withStyle(ChatFormatting.GRAY)));
    }

    /** Rotulo e seletor [◀ valor ▶]: setas grandes, clique no valor avanca, roda sobre ele troca. Devolve o proximo y. */
    private float selector(HoloCanvas c, String id, float x, float y, float w, String label, String value, IntConsumer step) {
        c.caption(label, x, y + 1F, c.muted());
        y += 12F;
        float h = 22F;
        boolean enabled = !draft.saving();
        boolean prev = c.button("cr." + id + ".prev", "◀", x, y, h + 4F, h, null, accent(), enabled, false, "", () -> step.accept(-1));
        boolean next = c.button("cr." + id + ".next", "▶", x + w - h - 4F, y, h + 4F, h, null, accent(), enabled, false, "",
                () -> step.accept(1));
        float vx = x + h + 8F, vw = w - 2F * (h + 8F);
        boolean hot = c.over(vx, y, vw, h);
        float k = c.hoverAnim("cr." + id + ".value", hot);
        c.fill(vx, y, vw, h, Ui.alpha(accent(), 0.06F + 0.1F * k));
        c.frame(vx, y, vw, h, Ui.alpha(accent(), 0.25F + 0.4F * k));
        String shown = Ui.fit(c.font, value, (int) vw - 8);
        c.textCentered(shown, vx + vw / 2F, y + (h - 8F) / 2F, c.text(), false);
        HoloZone valueZone = c.zone(vx, y, vw, h);
        valueZone.enabled = enabled;
        valueZone.click = enabled ? () -> step.accept(1) : null;
        // roda sobre o seletor inteiro troca o valor (mais facil que mirar nas setas)
        HoloZone wheel = c.zone(x, y, w, h);
        wheel.enabled = enabled;
        wheel.scroll = amount -> {
            if (amount == 0) return;
            step.accept(amount > 0 ? -1 : 1);
            Ui.playClick(1.15F);
        };
        menu.hoverSound("cr." + id, hot || prev || next);
        if (hot || prev || next) c.tooltip(List.of(Component.literal(label + ": " + value).withStyle(ChatFormatting.BOLD),
                Component.translatable(KEY + "selector.desc").withStyle(ChatFormatting.GRAY)));
        return y + h + 10F;
    }

    /** Hair Color: nome da cor e a paleta clicavel; a roda sobre ela troca a cor. */
    private float palette(HoloCanvas c, float x, float y, float w) {
        String label = HoloView.tr(K + "hair_color");
        String name = HoloView.tr(K + "color." + CharacterAppearance.COLOR_IDS[draft.hairColor()]);
        c.caption(label, x, y + 1F, c.muted());
        String shownName = Ui.fit(c.font, name, (int) (w * 0.5F) - 12);
        float nameX = x + w - c.font.width(shownName);
        c.text(shownName, nameX, y, c.text(), false);
        c.fill(nameX - 11F, y, 8F, 8F, 0xFF000000);
        c.fill(nameX - 10F, y + 1F, 6F, 6F, CharacterAppearance.COLOR_ARGB[draft.hairColor()] | 0xFF000000);
        y += 12F;
        int count = CharacterAppearance.COLOR_IDS.length;
        float gap = 5F, s = Math.min(30F, (w - gap * (count - 1)) / count), h = 22F;
        boolean enabled = !draft.saving();
        boolean any = false;
        for (int i = 0; i < count; i++) {
            int color = i;
            float sx = x + i * (s + gap);
            boolean selected = i == draft.hairColor();
            boolean hot = enabled && c.over(sx, y, s, h);
            float k = c.hoverAnim("cr.color." + i, hot);
            float sel = c.hoverAnim("cr.color.sel." + i, selected);
            int argb = CharacterAppearance.COLOR_ARGB[i] | 0xFF000000;
            c.fill(sx, y, s, h, 0xC0040A12);
            c.fill(sx + 3F, y + 3F, s - 6F, h - 6F, argb);
            c.frame(sx, y, s, h, Ui.alpha(selected ? 0xFFFFFFFF : accent(), 0.25F + 0.45F * Math.max(k, sel)));
            if (sel > 0.01F) {
                c.fill(sx, y + h + 1F, s, 2F, Ui.alpha(accent(), sel));
                c.glow(sx + s / 2F, y + h / 2F, s * 0.8F, Ui.alpha(argb, 0.5F * sel));
            }
            HoloZone z = c.zone(sx, y, s, h);
            z.enabled = enabled;
            z.click = enabled ? () -> draft.setHairColor(color) : null;
            if (hot) {
                any = true;
                c.tooltip(List.of(Component.translatable(K + "color." + CharacterAppearance.COLOR_IDS[i]).withStyle(ChatFormatting.BOLD),
                        Component.translatable(KEY + "selector.desc").withStyle(ChatFormatting.GRAY)));
            }
        }
        HoloZone wheel = c.zone(x, y, w, h);
        wheel.enabled = enabled;
        wheel.scroll = amount -> {
            if (amount == 0) return;
            draft.setHairColor(draft.hairColor() + (amount > 0 ? -1 : 1));
            Ui.playClick(1.15F);
        };
        menu.hoverSound("cr.palette", any);
        return y + h + 12F;
    }
}
