package com.respectcraft.client.character;

import com.respectcraft.client.roulette.BirthRouletteScreen;
import com.respectcraft.client.skilltree.SkillTreeScreen;
import com.respectcraft.client.ui.EntityPreview;
import com.respectcraft.client.ui.RespectButton;
import com.respectcraft.client.ui.RespectScreen;
import com.respectcraft.client.ui.Ui;
import com.respectcraft.needs.Need;
import com.respectcraft.needs.Needs;
import com.respectcraft.network.ProfileClientCache;
import com.respectcraft.profile.BirthOrigin;
import com.respectcraft.profile.BirthTrait;
import com.respectcraft.profile.CharacterAttribute;
import com.respectcraft.profile.CharacterProfile;
import com.respectcraft.profile.Speciality;
import com.respectcraft.profile.SpecialityRarity;
import com.respectcraft.skill.SkillDef;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Menu da tecla K: previa do personagem, origem, os seis atributos, as quatro necessidades ativas, os tracos de
 * nascimento e atalhos para as outras telas. Origem, atributos, necessidades e tracos sao sistemas separados.
 */
public final class CharacterHubScreen extends RespectScreen {
    private static final String KEY = "gui.respectcraft.hub.";
    /** Largura da coluna da direita (botoes e tracos). */
    private static final int TRAIT_W = 122;
    private float yaw = -20F;
    /** Dica do quadro atual (nome completo da Speciality ou do traco); desenhada por cima dos widgets. */
    private List<Component> tooltip;

    public CharacterHubScreen() {
        super(Component.translatable(KEY + "title"));
    }

    @Override
    protected void init() {
        layoutPanel(400, 230);
        CharacterProfile profile = ProfileClientCache.profile();
        int bx = left + panelWidth - 132, bw = TRAIT_W;
        int y = top + 30;
        addRenderableWidget(new RespectButton(bx, y, bw, 22, Component.translatable(KEY + "edit"),
                b -> minecraft.setScreen(new CharacterCreationScreen())).icon(SkillDef.iconTexture("identidade_dinheiro")));
        addRenderableWidget(new RespectButton(bx, y + 26, bw, 22, Component.translatable(profile.born() ? KEY + "birth_done" : KEY + "birth"),
                b -> minecraft.setScreen(new BirthRouletteScreen())).icon(SkillDef.iconTexture("trevo"))
                .accent(profile.born() ? Ui.GOLD : Ui.GREEN).badge(profile.born() ? "" : "!"));
        addRenderableWidget(new RespectButton(bx, y + 52, bw, 22, Component.translatable(KEY + "skills"),
                b -> minecraft.setScreen(new SkillTreeScreen())).icon(SkillDef.iconTexture("niobio"))
                .badge(profile.skillPoints() > 0 ? String.valueOf(profile.skillPoints()) : ""));
        addRenderableWidget(new RespectButton(bx, top + panelHeight - 30, bw, 20, Component.translatable(KEY + "close"), b -> onClose()));
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
        tooltip = null;
        int x = px2 + 10, y = top + 30;
        int barW = left + panelWidth - 142 - x;
        // Speciality ativa (hoje igual a de nascimento): nome, raridade e descricao curta. Ficha antiga sem
        // Speciality mostra a origem social legada; ninguem recebe Speciality ao carregar.
        Speciality speciality = p.specialityOrNull();
        BirthOrigin origin = p.originOrNull();
        if (speciality != null) {
            SpecialityRarity rarity = speciality.rarity();
            Component rarityName = Component.translatable(rarity.translationKey());
            Component specialityName = Component.translatable(speciality.translationKey());
            if (font.width(specialityName) + 6 + font.width(rarityName) <= barW) {
                // Nome e raridade dividem a linha; a descricao curta vem embaixo (ate duas linhas).
                g.drawString(font, specialityName, x, y + 1, rarity.color(), true);
                g.drawString(font, rarityName, x + barW - font.width(rarityName), y + 1, rarity.color(), false);
                var lines = font.split(Component.translatable(speciality.translationKey() + ".desc"), barW);
                for (int i = 0; i < Math.min(2, lines.size()); i++) g.drawString(font, lines.get(i), x, y + 12 + i * 9, Ui.MUTED, false);
            } else {
                // Tela estreita: o nome quebra em ate duas linhas, a raridade vai embaixo e a descricao fica na dica.
                List<String> lines = Ui.wrap(font, specialityName.getString(), barW, 2);
                for (int i = 0; i < lines.size(); i++) g.drawString(font, lines.get(i), x, y + 1 + i * 9, rarity.color(), true);
                g.drawString(font, rarityName, x, y + 21, rarity.color(), false);
            }
            if (mouseX >= x && mouseX < x + barW && mouseY >= y && mouseY < y + 30) {
                tooltip = List.of(specialityName.copy().withStyle(ChatFormatting.BOLD),
                        rarityName.copy().withStyle(ChatFormatting.GRAY),
                        Component.translatable(speciality.translationKey() + ".desc").withStyle(ChatFormatting.GRAY));
            }
            y += 32;
        } else if (p.born() && origin != null) {
            Ui.icon(g, com.respectcraft.skill.SkillDef.iconTexture(origin.icon()), x, y - 2, 14);
            g.drawString(font, Component.translatable("origin.respectcraft." + origin.id()), x + 18, y + 1, origin.color(), true);
            g.drawString(font, Component.translatable("gui.respectcraft.speciality.legacy_origin"), x, y + 14, Ui.MUTED, false);
            y += 32;
        } else {
            g.drawString(font, Component.translatable(KEY + "unborn"), x, y + 1, Ui.MUTED, false);
            y += 16;
        }
        for (CharacterAttribute a : CharacterAttribute.values()) {
            int v = p.attribute(a);
            g.drawString(font, Component.translatable("attribute.respectcraft." + a.id()), x, y, Ui.TEXT, false);
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
            String needName = Component.translatable("need.respectcraft." + need.id()).getString();
            String pct = n.percent(need) + "%";
            if (font.width(needName + " " + pct) <= half) {
                g.drawString(font, needName + " " + pct, cx, cy, Ui.MUTED, false);
            } else {
                // Tela estreita: o nome encurta para nao invadir a coluna vizinha; o valor fica a direita.
                String shortName = Ui.fit(font, needName, half - font.width(pct) - 3);
                if (shortName.startsWith("...")) shortName = needName.substring(0, 1); // sem espaco: so a inicial
                g.drawString(font, shortName, cx, cy, Ui.MUTED, false);
                g.drawString(font, pct, cx + half - font.width(pct), cy, Ui.MUTED, false);
                if (mouseX >= cx && mouseX < cx + half && mouseY >= cy - 1 && mouseY < cy + 14) {
                    tooltip = List.of(Component.literal(needName + " " + pct));
                }
            }
            Ui.bar(g, cx, cy + 9, half, 4, n.get(need) / (float) Needs.MAX, need.color());
        }
        int rx = left + panelWidth - 132;
        g.drawString(font, Component.translatable(KEY + "points", p.skillPoints()), rx, top + 110, Ui.GOLD, false);
        // A coluna da direita tem a largura dos botoes (termina 10 px antes da borda) e para acima de "Back to game".
        drawTraits(g, p, rx, top + 124, TRAIT_W, top + panelHeight - 34, mouseX, mouseY);
    }

    @Override
    protected void renderOverlay(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (tooltip != null) g.renderComponentTooltip(font, tooltip, mouseX, mouseY);
    }

    /**
     * Tracos de nascimento: nome, Buff/Debuff/Neutro, descricao curta e efeito (so quando existe). Tudo quebra na
     * largura {@code w}: o rotulo da polaridade fica ao lado do nome quando cabe, senao na linha de baixo. Linhas que
     * passariam de {@code bottom} nao sao desenhadas; a dica (mouse sobre o traco) sempre mostra o texto completo.
     */
    private void drawTraits(GuiGraphics g, CharacterProfile p, int x, int y, int w, int bottom, int mouseX, int mouseY) {
        g.drawString(font, Component.translatable(KEY + "traits"), x, y, Ui.GOLD, false);
        y += 12;
        List<BirthTrait> traits = p.birthTraits();
        if (traits.isEmpty()) {
            // Personagem antigo (nasceu antes dos tracos) ou ainda sem nascimento: estado neutro.
            g.drawWordWrap(font, Component.translatable(p.born() ? "gui.respectcraft.trait.none" : "gui.respectcraft.trait.unborn"),
                    x, y, w, Ui.MUTED);
            return;
        }
        for (BirthTrait trait : traits) {
            int start = y;
            int color = trait.polarity().color();
            Component name = Component.translatable(trait.translationKey());
            Component tag = Component.translatable("gui.respectcraft.trait.polarity." + trait.polarity().id());
            Component desc = Component.translatable(trait.translationKey() + ".desc");
            Component effect = Component.translatable(trait.hasEffect() ? "gui.respectcraft.trait.has_effect"
                    : "gui.respectcraft.trait.no_effect");
            List<FormattedCharSequence> nameLines = font.split(name, w);
            for (int i = 0; i < nameLines.size(); i++) {
                if (y + 9 > bottom) break;
                g.drawString(font, nameLines.get(i), x, y, color, true);
                if (i < nameLines.size() - 1) y += 9;
            }
            int lastNameW = font.width(nameLines.get(nameLines.size() - 1));
            if (lastNameW + 6 + font.width(tag) <= w) {
                if (y + 9 <= bottom) g.drawString(font, tag, x + w - font.width(tag), y, color, false);
            } else {
                y += 10;
                if (y + 9 <= bottom) g.drawString(font, tag, x + w - font.width(tag), y, color, false);
            }
            y += 11;
            y = wrapped(g, desc, x, y, w, bottom, Ui.MUTED) + 2;
            y = wrapped(g, effect, x, y, w, bottom, 0xFF7D8790) + 3;
            if (mouseX >= x && mouseX < x + w && mouseY >= start && mouseY < Math.min(y, bottom)) {
                tooltip = List.of(name.copy().withStyle(ChatFormatting.BOLD), tag.copy().withStyle(ChatFormatting.GRAY),
                        desc.copy().withStyle(ChatFormatting.GRAY), effect.copy().withStyle(ChatFormatting.DARK_GRAY));
            }
        }
    }

    /** Texto quebrado em {@code w}, sem passar de {@code bottom}. Devolve o y logo abaixo da ultima linha. */
    private int wrapped(GuiGraphics g, Component text, int x, int y, int w, int bottom, int color) {
        for (FormattedCharSequence line : font.split(text, w)) {
            if (y + 9 > bottom) break;
            g.drawString(font, line, x, y, color, false);
            y += 9;
        }
        return y;
    }
}
