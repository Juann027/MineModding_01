package com.tupinicraft.client.character;

import com.tupinicraft.character.CharacterAppearance;
import com.tupinicraft.network.CharacterAppearanceClientCache;
import com.tupinicraft.network.CharacterAppearanceUpdatePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Criador inicial: penteados 3D simples e cor são renderizados sobre os jogadores.
 * Corpo/pele/rosto são preferências salvas, aguardando o futuro modelo personalizado.
 * NÃO modifica skin Mojang, atributos ou animações do personagem.
 */
public final class CharacterCreationScreen extends Screen {
    private int bodyType, hairStyle, hairColor, skinTone, face;
    private Button[] selectors;
    private int px, py, pw;
    private Button saveButton;
    private boolean waitingForServer = false;
    private int requestedAppearance;
    private long revisionBeforeSave;
    private int waitTicks;
    private boolean saveError = false;

    public CharacterCreationScreen() {
        super(Component.translatable("gui.tupinicraft.creator.title"));
        CharacterAppearance current = Minecraft.getInstance().player == null ? CharacterAppearance.defaults()
                : CharacterAppearanceClientCache.get(Minecraft.getInstance().player.getUUID());
        bodyType = current.bodyType();
        hairStyle = current.hairStyle();
        hairColor = current.hairColor();
        skinTone = current.skinTone();
        face = current.face();
    }

    @Override
    protected void init() {
        pw = Math.min(width - 12, 356);
        px = (width - pw) / 2;
        py = Math.max(2, (height - 220) / 2);
        int right = px + 132;
        int buttonWidth = Math.max(72, pw - 143);
        selectors = new Button[5];
        for (int i = 0; i < selectors.length; i++) {
            final int field = i;
            selectors[i] = addRenderableWidget(Button.builder(label(i), button -> {
                cycle(field);
                button.setMessage(label(field));
            }).bounds(right, py + 40 + i * 25, buttonWidth, 22).build());
        }
        saveButton = addRenderableWidget(Button.builder(Component.translatable("gui.tupinicraft.creator.save"), button -> saveCharacter())
                .bounds(px + pw - 99, py + 195, 89, 20).build());
        saveButton.active = !waitingForServer;
        addRenderableWidget(Button.builder(Component.translatable("gui.tupinicraft.creator.back"), button -> onClose())
                .bounds(px + 10, py + 195, 84, 20).build());
    }

    /** Só considera a criação concluída quando o servidor confirma a aparência via sync. */
    private void saveCharacter() {
        Minecraft mc = Minecraft.getInstance();
        if (waitingForServer || mc.player == null) return;
        CharacterAppearance appearance = new CharacterAppearance(true, bodyType, hairStyle, hairColor, skinTone, face);
        requestedAppearance = appearance.pack();
        revisionBeforeSave = CharacterAppearanceClientCache.revision(mc.player.getUUID());
        waitTicks = 0;
        waitingForServer = true;
        saveError = false;
        saveButton.active = false;
        PacketDistributor.sendToServer(new CharacterAppearanceUpdatePayload(requestedAppearance));
    }

    @Override
    public void tick() {
        super.tick();
        if (!waitingForServer) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            waitingForServer = false;
            return;
        }
        var uuid = mc.player.getUUID();
        if (CharacterAppearanceClientCache.revision(uuid) > revisionBeforeSave) {
            CharacterAppearance confirmed = CharacterAppearanceClientCache.get(uuid);
            if (confirmed.created() && confirmed.pack() == requestedAppearance) {
                mc.setScreen(new CharacterHubScreen());
                return;
            }
            // Ignorar sincronizacoes antigas/concorrentes; aguardar a confirmacao correta.
        }
        if (++waitTicks >= 200) { // 10 segundos sem confirmacao do servidor
            failSaving();
        }
    }

    private void failSaving() {
        waitingForServer = false;
        saveError = true;
        saveButton.active = true;
    }

    @Override
    public void onClose() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && CharacterAppearanceClientCache.get(mc.player.getUUID()).created()) {
            mc.setScreen(new CharacterHubScreen());
        } else {
            super.onClose();
        }
    }

    private Component label(int field) {
        String fieldKey = "gui.tupinicraft.creator.";
        return switch (field) {
            case 0 -> Component.translatable(fieldKey + "body").append(": ")
                    .append(Component.translatable(fieldKey + "body." + CharacterAppearance.BODY_IDS[bodyType]));
            case 1 -> Component.translatable(fieldKey + "hair").append(": ")
                    .append(Component.translatable(fieldKey + "hair." + CharacterAppearance.HAIR_IDS[hairStyle]));
            case 2 -> Component.translatable(fieldKey + "color").append(": ")
                    .append(Component.translatable(fieldKey + "color." + CharacterAppearance.COLOR_IDS[hairColor]));
            case 3 -> Component.translatable(fieldKey + "skin").append(": ")
                    .append(Component.translatable(fieldKey + "skin." + CharacterAppearance.SKIN_IDS[skinTone]));
            default -> Component.translatable(fieldKey + "face").append(": ")
                    .append(Component.translatable(fieldKey + "face." + CharacterAppearance.FACE_IDS[face]));
        };
    }

    private void cycle(int field) {
        switch (field) {
            case 0 -> bodyType = (bodyType + 1) % CharacterAppearance.BODY_IDS.length;
            case 1 -> hairStyle = (hairStyle + 1) % CharacterAppearance.HAIR_IDS.length;
            case 2 -> hairColor = (hairColor + 1) % CharacterAppearance.COLOR_IDS.length;
            case 3 -> skinTone = (skinTone + 1) % CharacterAppearance.SKIN_IDS.length;
            default -> face = (face + 1) % CharacterAppearance.FACE_IDS.length;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Nao usar Screen#renderBackground: aplica blur ao mundo na 1.21.1.
        // Escurecimento sutil sem desfoque para manter o jogo visivel ao fundo.
        graphics.fill(0, 0, width, height, 0x66000000);
        graphics.fill(px, py, px + pw, py + 219, 0xFF_88949C);
        graphics.fill(px + 2, py + 2, px + pw - 2, py + 217, 0xFF_262F38);
        graphics.fill(px + 3, py + 3, px + pw - 3, py + 30, 0xFF_131B24);
        graphics.drawString(font, title, px + 10, py + 13, 0xFF_E7F0F7, false);
        graphics.drawString(font, Component.translatable("gui.tupinicraft.creator.preview"), px + 13, py + 39, 0xFF_CDD9E3, false);
        drawPortrait(graphics, px + 16, py + 55);
        if (waitingForServer || saveError) {
            graphics.drawString(font, Component.translatable(waitingForServer
                    ? "gui.tupinicraft.creator.saving" : "gui.tupinicraft.creator.save_error"),
                    px + 12, py + 174, saveError ? 0xFFFF9999 : 0xFFB3E1BB, false);
        } else {
            graphics.drawString(font, Component.translatable("gui.tupinicraft.creator.note"), px + 12, py + 172, 0xFF_BCC8D0, false);
            graphics.drawString(font, Component.translatable("gui.tupinicraft.creator.note2"), px + 12, py + 183, 0xFF_BCC8D0, false);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** Retrato 2D do cabelo; as mesmas opções produzem acessórios voxel na cabeça em 3D. */
    private void drawPortrait(GuiGraphics g, int left, int top) {
        g.fill(left, top, left + 104, top + 110, 0xFF_151D25);
        g.fill(left + 2, top + 2, left + 102, top + 108, 0xFF_3B4652);
        int skin = CharacterAppearance.SKIN_ARGB[skinTone];
        int hair = CharacterAppearance.COLOR_ARGB[hairColor];
        // Corpo/busto e pescoço: apenas ilustração, ainda não altera a skin vanilla.
        g.fill(left + 29, top + 86, left + 76, top + 108, 0xFF_4D7995);
        g.fill(left + 46, top + 77, left + 59, top + 92, skin);
        g.fill(left + 30, top + 27, left + 75, top + 81, skin);
        g.fill(left + 26, top + 46, left + 30, top + 66, skin);
        g.fill(left + 75, top + 46, left + 79, top + 66, skin);
        // Olhos e boca de prévia.
        g.fill(left + 42, top + 51, left + 47, top + 56, 0xFF_2B2628);
        g.fill(left + 58, top + 51, left + 63, top + 56, 0xFF_2B2628);
        if (face == 1) {
            g.fill(left + 45, top + 68, left + 61, top + 70, 0xFF_743B43);
            g.fill(left + 48, top + 70, left + 58, top + 72, 0xFF_743B43);
        } else if (face == 2) {
            g.fill(left + 45, top + 68, left + 61, top + 70, 0xFF_743B43);
            g.fill(left + 38, top + 46, left + 50, top + 49, hair);
            g.fill(left + 56, top + 46, left + 68, top + 49, hair);
        } else {
            g.fill(left + 47, top + 69, left + 59, top + 71, 0xFF_743B43);
        }
        if (hairStyle != 0) {
            g.fill(left + 29, top + 21, left + 76, top + 32, hair);
            g.fill(left + 29, top + 30, left + 35, top + 43, hair);
            g.fill(left + 70, top + 30, left + 76, top + 43, hair);
        }
        switch (hairStyle) {
            case 2 -> g.fill(left + 32, top + 31, left + 64, top + 41, hair);
            case 3 -> {
                for (int i = 0; i < 5; i++) g.fill(left + 30 + i * 9, top + 17 - (i % 2) * 4,
                        left + 38 + i * 9, top + 26, hair);
            }
            case 4 -> {
                g.fill(left + 21, top + 30, left + 30, top + 91, hair);
                g.fill(left + 75, top + 30, left + 84, top + 91, hair);
            }
            case 5 -> {
                g.fill(left + 75, top + 30, left + 87, top + 43, hair);
                g.fill(left + 84, top + 39, left + 94, top + 75, hair);
            }
            case 6 -> {
                g.fill(left + 45, top + 6, left + 60, top + 21, hair);
                g.fill(left + 33, top + 15, left + 43, top + 24, hair);
            }
            default -> { /* careca e curto */ }
        }
    }

    @Override public boolean isPauseScreen() { return false; }
}
