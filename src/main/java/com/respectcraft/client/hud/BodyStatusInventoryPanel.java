package com.respectcraft.client.hud;

import com.respectcraft.client.ui.Ui;
import com.respectcraft.needs.Need;
import com.respectcraft.needs.NeedEffectDef;
import com.respectcraft.needs.Needs;
import com.respectcraft.needs.NeedsRules;
import com.respectcraft.network.ProfileClientCache;
import com.respectcraft.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;

/**
 * Painel Body & Traits ao lado do inventario normal (somente leitura). E o unico lugar com os medidores
 * detalhados (a tela de jogo so tem o orbe de emocao): nome, icone, valor, barra, dica e estado critico.
 *
 * <p>Mostra so as quatro necessidades ativas (Coom, Bexiga, Intestino, Emocao); Hidratacao e Energia sao dados
 * legados inativos e nao aparecem. Embaixo, a Speciality (com a raridade) e o traco de nascimento (com a polaridade),
 * sistemas separados, cada um com o nome inteiro quebrado em ate duas linhas; a altura do painel acompanha.
 * Os valores vem da copia sincronizada pelo servidor; nada e calculado aqui.
 * Usa o lado livre do inventario; em GUIs pequenas, estreita e omite rotulos, sem cobrir os slots; a dica sempre
 * mostra o nome completo.
 */
public final class BodyStatusInventoryPanel {
    private static final Need[] MAIN = Need.ACTIVE;
    private static final int PANEL_WIDTH = 128;
    private static final int SPACING = 7;
    private static final int ROW = 17;

    private static final int BACKGROUND = 0xF0_30353B;
    private static final int INNER = 0xFF_171B20;
    private static final int BORDER = 0xFF_A2ABB1;
    private static final int TEXT = 0xFF_E9EDF2;
    private static final int MUTED = 0xFF_C3C9D0;
    private static final int CRITICAL = 0xFF_FF5A5A;

    private BodyStatusInventoryPanel() {}

    /** Linhas de nome por bloco (Speciality, traco); a ultima recebe "..." se o nome nao couber e a dica mostra tudo. */
    private static final int MAX_NAME_LINES = 2;
    private static final int LINE = 9;

    /**
     * Bloco "rotulo + nome" (Speciality/origem ou traco): o rotulo e a raridade/polaridade dividem a primeira linha,
     * o nome vem embaixo, quebrado na largura do painel.
     */
    private record Block(String label, String badge, int badgeColor, List<String> name, int nameColor,
                         List<Component> tooltip) {
        int height() { return LINE + 1 + name.size() * LINE; }
    }

    private static int panelHeight(Block speciality, Block trait) {
        return 23 + MAIN.length * ROW + 5 + speciality.height() + 4 + trait.height() + 4 + LINE + 6;
    }

    public static void onInventoryForeground(ContainerScreenEvent.Render.Foreground event) {
        if (!(event.getContainerScreen() instanceof InventoryScreen inventory)) return;
        if (Minecraft.getInstance().player == null) return;

        GuiGraphics graphics = event.getGuiGraphics();
        int screenWidth = graphics.guiWidth();
        int leftSpace = inventory.getGuiLeft();
        int rightSpace = screenWidth - (inventory.getGuiLeft() + inventory.getXSize());
        // O livro de receitas ocupa a lateral esquerda quando esta aberto: preferir a direita ou ocultar.
        boolean recipeBookOpen = inventory.getRecipeBookComponent().isVisible();
        // Com efeitos de pocao ativos o vanilla lista os efeitos a direita do inventario: o painel vai para a esquerda.
        boolean vanillaEffects = !Minecraft.getInstance().player.getActiveEffects().isEmpty();
        boolean right = recipeBookOpen || (vanillaEffects ? leftSpace < 36 + SPACING + 3 : rightSpace >= leftSpace);
        int available = (right ? rightSpace : leftSpace) - SPACING - 3;
        if (available < 36) return; // escala de GUI muito pequena: nao invade os slots.

        int width = Math.min(PANEL_WIDTH, available);
        Font font = Minecraft.getInstance().font;
        int inner = width - 2 * padding(width);
        Block speciality = specialityBlock(font, inner, compact(width));
        Block trait = traitBlock(font, inner, compact(width));
        int height = panelHeight(speciality, trait);
        int x = right ? inventory.getGuiLeft() + inventory.getXSize() + SPACING
                : inventory.getGuiLeft() - SPACING - width;
        int y = Math.min(inventory.getGuiTop() + 2, graphics.guiHeight() - height - 2);
        y = Math.max(2, y);

        // O evento Foreground chega com a pose deslocada para (guiLeft, guiTop); as contas acima estao em
        // coordenadas de tela.
        graphics.pose().pushPose();
        graphics.pose().translate(-inventory.getGuiLeft(), -inventory.getGuiTop(), 0F);
        List<Component> tooltip = drawPanel(graphics, x, y, width, height, speciality, trait, event.getMouseX(), event.getMouseY());
        if (tooltip != null) {
            graphics.pose().translate(0, 0, 300F);
            graphics.renderComponentTooltip(Minecraft.getInstance().font, tooltip, event.getMouseX(), event.getMouseY());
        }
        graphics.pose().popPose();
    }

    private static boolean compact(int width) { return width < 99; }

    private static int padding(int width) { return compact(width) ? 5 : 8; }

    private static List<Component> drawPanel(GuiGraphics graphics, int x, int y, int width, int height, Block speciality,
                                             Block trait, int mouseX, int mouseY) {
        Font font = Minecraft.getInstance().font;
        Needs needs = ProfileClientCache.needs();
        List<Map.Entry<NeedEffectDef, Integer>> effects = ProfileClientCache.activeEffects();
        boolean compact = compact(width);
        int padding = padding(width);
        int inner = width - 2 * padding;
        List<Component> tooltip = null;

        rect(graphics, x, y, width, height, BORDER);
        rect(graphics, x + 2, y + 2, width - 4, height - 4, BACKGROUND);
        rect(graphics, x + 3, y + 3, width - 6, 17, INNER);
        String title = compact ? (width < 70 ? "ST" : "STATUS") : "BODY & TRAITS";
        graphics.drawString(font, title, x + padding, y + 7, TEXT, false);

        int rowY = y + 23;
        for (Need need : MAIN) {
            if (meter(graphics, font, x + padding, rowY, inner, need, needs, compact, mouseX, mouseY)) {
                tooltip = needTooltip(need, needs, effects);
            }
            rowY += ROW;
        }
        rect(graphics, x + padding, rowY + 1, inner, 1, BORDER);
        rowY += 5;
        // Speciality ativa (ficha antiga sem Speciality mostra a origem social legada) e, separado, o traco de nascimento.
        for (Block block : List.of(speciality, trait)) {
            drawBlock(graphics, font, block, x + padding, rowY, inner);
            if (block.tooltip() != null && inside(mouseX, mouseY, x + padding, rowY - 1, inner, block.height())) {
                tooltip = block.tooltip();
            }
            rowY += block.height() + 4;
        }
        String footer = compact ? (effects.isEmpty() ? "K" : "+" + effects.size())
                : effects.isEmpty() ? Component.translatable("gui.respectcraft.hud_hint").getString()
                : Component.translatable("gui.respectcraft.needs.effects", effects.size()).getString();
        int footerY = rowY;
        graphics.drawString(font, font.plainSubstrByWidth(footer, inner), x + padding, footerY,
                effects.isEmpty() ? MUTED : 0xFF_8FE3A0, false);
        if (!effects.isEmpty() && inside(mouseX, mouseY, x + padding, footerY - 2, inner, 11)) {
            tooltip = effectsTooltip(effects);
        }
        return tooltip;
    }

    /** Rotulo a esquerda, raridade/polaridade a direita e o nome embaixo. */
    private static void drawBlock(GuiGraphics graphics, Font font, Block block, int x, int y, int width) {
        int badgeW = Math.min(font.width(block.badge()), width);
        String badge = Ui.fit(font, block.badge(), width);
        if (!badge.isEmpty()) graphics.drawString(font, badge, x + width - font.width(badge), y, block.badgeColor(), false);
        int labelW = width - (badge.isEmpty() ? 0 : badgeW + 4);
        if (!block.label().isEmpty() && labelW > 0) {
            graphics.drawString(font, Ui.fit(font, block.label(), labelW), x, y, MUTED, false);
        }
        int lineY = y + LINE + 1;
        for (String line : block.name()) {
            graphics.drawString(font, line, x, lineY, block.nameColor(), false);
            lineY += LINE;
        }
    }

    private static Block specialityBlock(Font font, int width, boolean compact) {
        var profile = ProfileClientCache.profile();
        var speciality = profile.specialityOrNull();
        var origin = profile.originOrNull();
        if (speciality != null) {
            Component name = Component.translatable(speciality.translationKey());
            Component rarity = Component.translatable(speciality.rarity().translationKey());
            List<Component> tooltip = List.of(name.copy().withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD),
                    rarity.copy().withStyle(ChatFormatting.GRAY),
                    Component.translatable(speciality.translationKey() + ".desc").withStyle(ChatFormatting.GRAY));
            return new Block(compact ? "" : Component.translatable("gui.respectcraft.speciality").getString(),
                    rarity.getString(), speciality.rarity().color(), Ui.wrap(font, name.getString(), width, MAX_NAME_LINES),
                    speciality.rarity().color(), tooltip);
        }
        String label = Component.translatable(origin != null ? "gui.respectcraft.origin" : "gui.respectcraft.speciality").getString();
        String name = origin == null ? "--" : Component.translatable("origin.respectcraft." + origin.id()).getString();
        List<Component> tooltip = origin == null ? null : List.of(Component.literal(name).withStyle(ChatFormatting.WHITE));
        return new Block(label, "", TEXT, Ui.wrap(font, name, width, MAX_NAME_LINES), TEXT, tooltip);
    }

    /** Traco de nascimento (nao confundir com a origem); a ficha tem um por nascimento. */
    private static Block traitBlock(Font font, int width, boolean compact) {
        var traits = ProfileClientCache.profile().birthTraits();
        var trait = traits.isEmpty() ? null : traits.get(0);
        String label = compact ? "" : Component.translatable("gui.respectcraft.traits").getString();
        if (trait == null) return new Block(label, "", MUTED, List.of("--"), MUTED, null);
        Component name = Component.translatable(trait.translationKey());
        Component polarity = Component.translatable("gui.respectcraft.trait.polarity." + trait.polarity().id());
        List<Component> tooltip = List.of(name.copy().withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD),
                polarity.copy().withStyle(ChatFormatting.GRAY),
                Component.translatable(trait.translationKey() + ".desc").withStyle(ChatFormatting.GRAY),
                Component.translatable(trait.hasEffect() ? "gui.respectcraft.trait.has_effect"
                        : "gui.respectcraft.trait.no_effect").withStyle(ChatFormatting.DARK_GRAY));
        return new Block(label, polarity.getString(), trait.polarity().color(), Ui.wrap(font, name.getString(), width, MAX_NAME_LINES),
                trait.polarity().color(), tooltip);
    }

    /** Uma linha: icone, nome, valor e barra. Devolve se o mouse esta sobre ela. */
    private static boolean meter(GuiGraphics graphics, Font font, int x, int y, int width, Need need, Needs needs,
                                 boolean compact, int mouseX, int mouseY) {
        if (width < 8) return false;
        int value = needs.percent(need);
        boolean critical = critical(need, needs.get(need));
        boolean blink = critical && (System.currentTimeMillis() / 400) % 2 == 0;

        int iconSize = 8;
        renderIcon(graphics, icon(need), x, y);
        int textX = x + iconSize + 2;
        String title = Component.translatable("need.respectcraft." + need.id()).getString();
        String name = compact ? title.substring(0, 1) : title;
        String number = value + "%";
        int numberX = x + width - font.width(number);
        graphics.drawString(font, name, textX, y, critical ? CRITICAL : TEXT, false);
        if (numberX >= textX + font.width(name) + 2) {
            graphics.drawString(font, number, numberX, y, critical ? CRITICAL : MUTED, false);
        }
        rect(graphics, x, y + 9, width, 6, critical ? (blink ? CRITICAL : 0xFF_6A1E1E) : 0xFF_0E1319);
        rect(graphics, x + 1, y + 10, width - 2, 4, 0xFF_4B535D);
        int contentWidth = Math.max(0, width - 2);
        int filledWidth = (contentWidth * value) / 100;
        if (filledWidth > 0) rect(graphics, x + 1, y + 10, filledWidth, 4, blink ? CRITICAL : need.color());
        return inside(mouseX, mouseY, x, y - 1, width, ROW - 1);
    }

    /** Mesmo limite usado pelo servidor nos avisos e efeitos. */
    private static boolean critical(Need need, int raw) {
        return need.highIsGood() ? raw < NeedsRules.LOW : raw > NeedsRules.URGENT;
    }

    private static List<Component> needTooltip(Need need, Needs needs, List<Map.Entry<NeedEffectDef, Integer>> effects) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("need.respectcraft." + need.id()).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
        lines.add(Component.literal(needs.percent(need) + "%").withStyle(ChatFormatting.GRAY));
        String desc = "need.respectcraft." + need.id() + ".desc";
        if (Language.getInstance().has(desc)) lines.add(Component.translatable(desc).withStyle(ChatFormatting.GRAY));
        if (critical(need, needs.get(need))) {
            lines.add(Component.translatable("need.respectcraft." + need.id() + ".critical").withStyle(ChatFormatting.RED));
        }
        for (Map.Entry<NeedEffectDef, Integer> e : effects) {
            NeedEffectDef def = e.getKey();
            if (def.rate(need) != 1F || def.gradual().get(need) != 0 || def.delayed().get(need) != 0) {
                lines.add(Component.translatable("gui.respectcraft.needs.effect_line",
                        Component.translatable("item.respectcraft." + def.id()), e.getValue()).withStyle(ChatFormatting.GREEN));
            }
        }
        return lines;
    }

    private static List<Component> effectsTooltip(List<Map.Entry<NeedEffectDef, Integer>> effects) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.respectcraft.needs.effects", effects.size()).withStyle(ChatFormatting.WHITE));
        for (Map.Entry<NeedEffectDef, Integer> e : effects) {
            lines.add(Component.translatable("gui.respectcraft.needs.effect_line",
                    Component.translatable("item.respectcraft." + e.getKey().id()), e.getValue()).withStyle(ChatFormatting.GREEN));
        }
        return lines;
    }

    private static ItemStack icon(Need need) {
        return switch (need) {
            case COOM -> new ItemStack(ModItems.AFRODISIACO_MISTERIOSO.get());
            case BLADDER -> new ItemStack(ModItems.CHA_DIURETICO.get());
            case BOWEL -> new ItemStack(ModItems.PENICO.get());
            case MOOD -> new ItemStack(Items.SUNFLOWER);
            case HYDRATION, ENERGY -> ItemStack.EMPTY; // legadas: nunca exibidas
        };
    }

    /** Icone de item reduzido a 8x8. */
    private static void renderIcon(GuiGraphics graphics, ItemStack stack, int x, int y) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0F);
        graphics.pose().scale(0.5F, 0.5F, 1F);
        graphics.renderFakeItem(stack, 0, 0);
        graphics.pose().popPose();
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static void rect(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        if (width > 0 && height > 0) graphics.fill(x, y, x + width, y + height, color);
    }
}
