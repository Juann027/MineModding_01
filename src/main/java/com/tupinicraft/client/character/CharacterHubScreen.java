package com.tupinicraft.client.character;

import com.tupinicraft.client.roulette.BirthRouletteScreen;
import com.tupinicraft.client.skilltree.SkillTreeScreen;
import com.tupinicraft.client.ui.EntityPreview;
import com.tupinicraft.client.ui.TupiButton;
import com.tupinicraft.client.ui.TupiScreen;
import com.tupinicraft.client.ui.Ui;
import com.tupinicraft.needs.Need;
import com.tupinicraft.needs.Needs;
import com.tupinicraft.network.ProfileClientCache;
import com.tupinicraft.profile.BirthOrigin;
import com.tupinicraft.profile.BirthTrait;
import com.tupinicraft.profile.CharacterAttribute;
import com.tupinicraft.profile.CharacterProfile;
import com.tupinicraft.skill.SkillDef;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Menu da tecla K: previa do personagem, origem, os seis atributos, as quatro necessidades ativas, os tracos de
 * nascimento e atalhos para as outras telas. Origem, atributos, necessidades e tracos sao sistemas separados.
 */
public final class CharacterHubScreen extends TupiScreen {
    private static final String KEY = "gui.tupinicraft.hub.";
    private float yaw = -20F;

    public CharacterHubScreen() {
        super(Component.translatable(KEY + "title"));
    }

    @Override
    protected void init() {
        layoutPanel(400, 230);
        CharacterProfile profile = ProfileClientCache.profile();
        int bx = left + panelWidth - 132, bw = 122;
        int y = top + 30;
        addRenderableWidget(new TupiButton(bx, y, bw, 22, Component.translatable(KEY + "edit"),
                b -> minecraft.setScreen(new CharacterCreationScreen())).icon(SkillDef.iconTexture("identidade_dinheiro")));
        addRenderableWidget(new TupiButton(bx, y + 26, bw, 22, Component.translatable(profile.born() ? KEY + "birth_done" : KEY + "birth"),
                b -> minecraft.setScreen(new BirthRouletteScreen())).icon(SkillDef.iconTexture("trevo"))
                .accent(profile.born() ? Ui.GOLD : Ui.GREEN).badge(profile.born() ? "" : "!"));
        addRenderableWidget(new TupiButton(bx, y + 52, bw, 22, Component.translatable(KEY + "skills"),
                b -> minecraft.setScreen(new SkillTreeScreen())).icon(SkillDef.iconTexture("niobio"))
                .badge(profile.skillPoints() > 0 ? String.valueOf(profile.skillPoints()) : ""));
        addRenderableWidget(new TupiButton(bx, top + panelHeight - 30, bw, 20, Component.translatable(KEY + "close"), b -> onClose()));
    }

    @Override
    public void tick() {
        super.tick();
        yaw += 0.6F;
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        int px1 = left + 10, py1 = top + 28, px2 = left + 92, py2 = top + panelHeight - 10;
        g.fill(px1, py1, px2, py2, 0xC0101820);
        Ui.glow(g, (px1 + px2) / 2F, py2 - 10, 34, 0x6043D17A);
        if (mc.player != null) EntityPreview.render(g, px1, py1, px2, py2, 40, yaw + partialTick * 0.6F, 4F, mc.player);

        CharacterProfile p = ProfileClientCache.profile();
        int x = px2 + 10, y = top + 30;
        BirthOrigin origin = p.originOrNull();
        if (origin != null) {
            Ui.icon(g, com.tupinicraft.skill.SkillDef.iconTexture(origin.icon()), x, y - 2, 14);
            g.drawString(font, Component.translatable("origin.tupinicraft." + origin.id()), x + 18, y + 1, origin.color(), true);
        } else {
            g.drawString(font, Component.translatable(KEY + "unborn"), x, y + 1, Ui.MUTED, false);
        }
        y += 16;
        int barW = left + panelWidth - 142 - x;
        for (CharacterAttribute a : CharacterAttribute.values()) {
            int v = p.attribute(a);
            g.drawString(font, Component.translatable("attribute.tupinicraft." + a.id()), x, y, Ui.TEXT, false);
            String num = String.valueOf(v);
            g.drawString(font, num, x + barW - font.width(num), y, Ui.GOLD, false);
            Ui.bar(g, x, y + 9, barW, 5, v / (float) CharacterAttribute.MAX, a.color());
            y += 17;
        }
        y += 4;
        // Necessidades ativas (Coom, Bexiga, Intestino, Emocao); hidratacao e energia sao legadas e nao aparecem.
        Needs n = ProfileClientCache.needs();
        g.drawString(font, Component.translatable(KEY + "needs"), x, y, Ui.GOLD, false);
        y += 11;
        int half = (barW - 6) / 2;
        for (int i = 0; i < Need.ACTIVE.length; i++) {
            Need need = Need.ACTIVE[i];
            int cx = x + (i % 2) * (half + 6), cy = y + (i / 2) * 15;
            g.drawString(font, Component.translatable("need.tupinicraft." + need.id()).getString() + " " + n.percent(need) + "%",
                    cx, cy, Ui.MUTED, false);
            Ui.bar(g, cx, cy + 9, half, 4, n.get(need) / (float) Needs.MAX, need.color());
        }
        int rx = left + panelWidth - 132;
        g.drawString(font, Component.translatable(KEY + "points", p.skillPoints()), rx, top + 112, Ui.GOLD, false);
        drawTraits(g, p, rx, top + 128, 122, mouseX, mouseY);
    }

    /** Tracos de nascimento: nome, Buff/Debuff/Neutro, descricao curta e efeito (so quando existe). */
    private void drawTraits(GuiGraphics g, CharacterProfile p, int x, int y, int w, int mouseX, int mouseY) {
        g.drawString(font, Component.translatable(KEY + "traits"), x, y, Ui.GOLD, false);
        y += 12;
        List<BirthTrait> traits = p.birthTraits();
        if (traits.isEmpty()) {
            // Personagem antigo (nasceu antes dos tracos) ou ainda sem nascimento: estado neutro.
            g.drawWordWrap(font, Component.translatable(p.born() ? "gui.tupinicraft.trait.none" : "gui.tupinicraft.trait.unborn"),
                    x, y, w, Ui.MUTED);
            return;
        }
        for (BirthTrait trait : traits) {
            Component name = Component.translatable(trait.translationKey());
            Component tag = Component.translatable("gui.tupinicraft.trait.polarity." + trait.polarity().id());
            g.drawString(font, name, x, y, trait.polarity().color(), true);
            int tagX = x + w - font.width(tag);
            g.drawString(font, tag, Math.max(x + font.width(name) + 4, tagX), y, trait.polarity().color(), false);
            y += 11;
            Component desc = Component.translatable(trait.translationKey() + ".desc");
            g.drawWordWrap(font, desc, x, y, w, Ui.MUTED);
            y += font.wordWrapHeight(desc, w) + 2;
            g.drawString(font, Component.translatable(trait.hasEffect() ? "gui.tupinicraft.trait.has_effect"
                    : "gui.tupinicraft.trait.no_effect"), x, y, 0xFF7D8790, false);
            y += 12;
        }
    }
}
