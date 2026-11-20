package com.respectcraft.client.character;

import com.respectcraft.character.CharacterAppearance;
import com.respectcraft.client.holo.HoloCharacterScreen;
import com.respectcraft.client.roulette.BirthRouletteScreen;
import com.respectcraft.client.ui.CycleSelector;
import com.respectcraft.client.ui.EntityPreview;
import com.respectcraft.client.ui.RespectButton;
import com.respectcraft.client.ui.RespectScreen;
import com.respectcraft.client.ui.Ui;
import com.respectcraft.network.ProfileClientCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Criador de personagem em tela (reserva do modo {@code SCREEN}; no modo mundo o criador e a placa holografica
 * {@code CreatorView}). Previa 3D do proprio jogador (rig + Body Layers) atualizada ao vivo e, ao lado, Body,
 * Hairstyle e a paleta de Hair Color, mais o botao aleatorio. Etapa 4.8.4-C: Skin e Face sairam; o estado e o
 * {@link AppearanceDraft} comum as duas cascas. A previa e so local; o servidor valida e confirma ao salvar.
 */
public final class CharacterCreationScreen extends RespectScreen {
    private static final String KEY = "gui.respectcraft.creator.";
    /** Distancia do topo da coluna ate a paleta de cor do cabelo (o nome da cor fica 12 px acima). */
    private static final int PALETTE_Y = 70;

    private final AppearanceDraft draft = new AppearanceDraft();
    private RespectButton saveButton;
    // Previa: giro por arrasto e "respiro" leve.
    private float previewYaw = -25F;
    private float yawVelocity;
    private boolean dragging;
    private int previewX1, previewY1, previewX2, previewY2;

    public CharacterCreationScreen() {
        super(Component.translatable(KEY + "title"));
    }

    @Override
    protected void init() {
        layoutPanel(380, 236);
        previewX1 = left + 10;
        previewY1 = top + 28;
        // A previa 3D e o elemento principal: ocupa quase metade do painel.
        previewX2 = left + Math.max(120, panelWidth * 9 / 20);
        previewY2 = top + panelHeight - 52;
        int colX = previewX2 + 10;
        int colW = left + panelWidth - 10 - colX;

        // Uma pagina so (sem abas): corpo, estilo do cabelo e a paleta de cor do cabelo.
        int y = top + 28;
        addRenderableWidget(new CycleSelector(colX, y, colW, 20, Component.translatable(KEY + "body"),
                CharacterAppearance.BODY_IDS.length, draft.bodyType(),
                i -> Component.translatable(KEY + "body." + CharacterAppearance.BODY_IDS[i]), draft::setBodyType));
        addRenderableWidget(new CycleSelector(colX, y + 30, colW, 20, Component.translatable(KEY + "hair"),
                CharacterAppearance.HAIR_IDS.length, draft.hairStyle(),
                i -> Component.translatable(KEY + "hair." + CharacterAppearance.HAIR_IDS[i]), draft::setHairStyle));
        // Cor do cabelo: so a paleta clicavel (o nome da cor aparece acima dela e na dica).
        int count = CharacterAppearance.COLOR_IDS.length;
        int sw = Math.min(22, (colW - 4 * (count - 1)) / count);
        for (int i = 0; i < count; i++) {
            final int color = i;
            addRenderableWidget(new RespectButton(colX + i * (sw + 4), y + PALETTE_Y, sw, sw, Component.empty(), b -> {
                draft.setHairColor(color);
                rebuildWidgets();
            }).selected(i == draft.hairColor()).accent(CharacterAppearance.COLOR_ARGB[i]).swatch(CharacterAppearance.COLOR_ARGB[i])
                    .tooltip(Component.translatable(KEY + "color." + CharacterAppearance.COLOR_IDS[i])));
        }
        int footerY = top + panelHeight - 28;
        addRenderableWidget(new RespectButton(previewX1, previewY2 + 4, previewX2 - previewX1, 18,
                Component.translatable(KEY + "random"), b -> randomize()).accent(0xFF8FB3C9));
        addRenderableWidget(new RespectButton(left + 10, footerY, 80, 20, Component.translatable(KEY + "back"), b -> onClose()));
        saveButton = addRenderableWidget(new RespectButton(left + panelWidth - 100, footerY, 90, 20,
                Component.translatable(KEY + "save"), b -> saveCharacter()).accent(Ui.GREEN));
        saveButton.active = !draft.saving();
        draft.preview();
    }

    private void randomize() {
        draft.randomize();
        yawVelocity = 25F;
        vfx.burst((previewX1 + previewX2) / 2F, (previewY1 + previewY2) / 2F, 24, Ui.GOLD, 3F);
        rebuildWidgets();
    }

    /** So considera a criacao concluida quando o servidor confirma a aparencia via sync. */
    private void saveCharacter() {
        if (draft.save()) saveButton.active = false;
    }

    @Override
    public void tick() {
        super.tick();
        previewYaw += yawVelocity;
        yawVelocity *= 0.85F;
        if (draft.tick()) {
            // Personagem novo ainda sem nascimento: segue para a roleta.
            minecraft.setScreen(ProfileClientCache.profile().born()
                    ? new HoloCharacterScreen(HoloCharacterScreen.Section.CHARACTER) : new BirthRouletteScreen());
            return;
        }
        if (saveButton != null) saveButton.active = !draft.saving();
    }

    @Override
    public void removed() {
        draft.discard();
        super.removed();
    }

    @Override
    public void onClose() {
        Minecraft mc = Minecraft.getInstance();
        draft.discard();
        if (!AppearanceDraft.firstTime()) {
            mc.setScreen(new HoloCharacterScreen(HoloCharacterScreen.Section.CHARACTER));
        } else {
            super.onClose();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseX >= previewX1 && mouseX < previewX2 && mouseY >= previewY1 && mouseY < previewY2) {
            dragging = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging) {
            previewYaw -= (float) dragX * 2.2F;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Pedestal da previa
        g.fill(previewX1, previewY1, previewX2, previewY2, 0xC0101820);
        g.fillGradient(previewX1, previewY1, previewX2, previewY2, 0x00000000, 0x6030A060);
        int cx = (previewX1 + previewX2) / 2;
        Ui.glow(g, cx, previewY2 - 10, 40, 0x6043D17A);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            float bob = Mth.sin((ticks + partialTick) * 0.08F) * 2F;
            EntityPreview.render(g, previewX1, previewY1, previewX2, previewY2,
                    Math.min(60, (previewY2 - previewY1) * 0.38F), previewYaw + bob, 6F, mc.player);
        }
        g.drawCenteredString(font, Component.translatable(KEY + "drag"), cx, previewY1 + 4, Ui.MUTED);

        int colX = previewX2 + 10;
        Component name = Component.translatable(KEY + "color." + CharacterAppearance.COLOR_IDS[draft.hairColor()]);
        g.drawString(font, Component.translatable(KEY + "hair_color").getString() + ": " + name.getString(),
                colX + 2, top + 28 + PALETTE_Y - 12, Ui.TEXT, false);
        boolean error = draft.state() == AppearanceDraft.State.ERROR;
        if (draft.saving() || error) {
            g.drawString(font, Component.translatable(draft.saving() ? KEY + "saving" : KEY + "save_error"),
                    colX, top + panelHeight - 44, error ? Ui.RED : Ui.GREEN, false);
        }
    }
}
