package com.tupinicraft.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * Orbe de humor permanente, como uma camada da HUD vanilla.
 * Não depende de texturas: o aro, a esfera e o rosto são pixel art desenhados
 * por GuiGraphics. Futuramente poderão ser substituídos por PNGs.
 */
public final class EmotionOrbOverlay {
    private static final int RADIUS = 12;
    private static final int FACE_COLOR = 0xFF17232F;

    private EmotionOrbOverlay() {}

    public static void render(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.options.hideGui) return;
        if (player.isSpectator()) return;
        if (graphics.guiWidth() < 220 || graphics.guiHeight() < 105) return;

        int mood = HudStatusClient.current().emotion();
        int centerX = graphics.guiWidth() / 2;
        // Entre os indicadores de vida/fome, acima do nível e barra de XP.
        int centerY = graphics.guiHeight() - 55;
        drawOrb(graphics, centerX, centerY, mood);
    }

    private static void drawOrb(GuiGraphics graphics, int centerX, int centerY, int mood) {
        int rim = mood < 20 ? 0xFFCE6464 : mood < 40 ? 0xFF6686C6
                : mood < 60 ? 0xFFC3AD69 : mood < 80 ? 0xFF79C8D9 : 0xFF79CE94;
        drawDisc(graphics, centerX, centerY + 1, RADIUS + 2, 0x99000000);
        drawDisc(graphics, centerX, centerY, RADIUS, 0xFF141B25);
        drawDisc(graphics, centerX, centerY, RADIUS - 2, rim);
        drawDisc(graphics, centerX, centerY, RADIUS - 5, 0xFF354F65);
        graphics.fill(centerX - 6, centerY - 7, centerX - 2, centerY - 5, 0xFF83D7F1);
        drawFace(graphics, centerX, centerY, mood);
    }

    private static void drawDisc(GuiGraphics graphics, int x, int y, int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            int dx = Mth.floor(Math.sqrt(radius * radius - dy * dy));
            graphics.fill(x - dx, y + dy, x + dx + 1, y + dy + 1, color);
        }
    }

    private static void drawFace(GuiGraphics graphics, int x, int y, int emotion) {
        // Olhos de dois pixels, posicionados simetricamente.
        graphics.fill(x - 5, y - 3, x - 3, y, FACE_COLOR);
        graphics.fill(x + 3, y - 3, x + 5, y, FACE_COLOR);

        if (emotion >= 60) { // feliz / eufórico
            graphics.fill(x - 4, y + 2, x - 2, y + 4, FACE_COLOR);
            graphics.fill(x - 2, y + 4, x + 3, y + 5, FACE_COLOR);
            graphics.fill(x + 3, y + 2, x + 5, y + 4, FACE_COLOR);
        } else if (emotion >= 40) { // neutro
            graphics.fill(x - 4, y + 3, x + 5, y + 4, FACE_COLOR);
        } else if (emotion >= 20) { // triste
            graphics.fill(x - 4, y + 4, x - 2, y + 6, FACE_COLOR);
            graphics.fill(x - 2, y + 2, x + 3, y + 3, FACE_COLOR);
            graphics.fill(x + 3, y + 4, x + 5, y + 6, FACE_COLOR);
        } else { // abalado
            graphics.fill(x - 3, y + 3, x + 4, y + 6, FACE_COLOR);
            graphics.fill(x - 2, y - 5, x - 1, y - 3, FACE_COLOR);
            graphics.fill(x + 1, y - 5, x + 2, y - 3, FACE_COLOR);
        }
    }
}
