package com.tupinicraft.client.hud;

import com.tupinicraft.needs.Need;
import com.tupinicraft.needs.Needs;
import com.tupinicraft.needs.NeedsRules;
import com.tupinicraft.network.ProfileClientCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * Quatro mini-barras (hidratacao, energia, bexiga, intestino) em grade 2x2 acima da fome, a direita da hotbar.
 * Usa e incrementa {@code gui.rightHeight}, como as camadas vanilla; pisca quando um valor esta critico.
 */
public final class NeedsHudOverlay {
    private static final Need[] SHOWN = {Need.HYDRATION, Need.ENERGY, Need.BLADDER, Need.BOWEL};
    private static final String[] LETTERS = {"H", "E", "B", "I"};

    private NeedsHudOverlay() {}

    public static void render(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.player.isCreative() || mc.player.isSpectator()) return;
        if (mc.gameMode == null || !mc.gameMode.canHurtPlayer()) return;
        Needs n = ProfileClientCache.needs();
        // Grade 2x2 encostada na direita da hotbar: nao invade o orbe de emocao no centro.
        int right = g.guiWidth() / 2 + 91;
        int cellW = 38;
        int baseY = g.guiHeight() - mc.gui.rightHeight - 14;
        long time = mc.level == null ? 0 : mc.level.getGameTime();
        for (int i = 0; i < SHOWN.length; i++) {
            Need need = SHOWN[i];
            int value = n.get(need);
            boolean critical = need.highIsGood() ? value < NeedsRules.LOW : value > NeedsRules.URGENT;
            int x = right - (2 - i % 2) * (cellW + 2) + 2;
            int y = baseY + (i / 2) * 8;
            int color = need.color();
            if (critical && (time / 5) % 2 == 0) color = 0xFFFF5A5A;
            g.drawString(mc.font, LETTERS[i], x, y - 1, critical ? 0xFFFF7A7A : 0xFFD8E0E8, true);
            int bx = x + 7, bw = cellW - 7;
            g.fill(bx, y, bx + bw, y + 5, 0xFF06080A);
            int fill = Math.round((bw - 2) * Mth.clamp(value / (float) Needs.MAX, 0F, 1F));
            if (fill > 0) g.fill(bx + 1, y + 1, bx + 1 + fill, y + 4, color);
        }
        mc.gui.rightHeight += 17;
    }
}
