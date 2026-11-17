package com.tupinicraft.client.character;

import com.tupinicraft.character.CharacterAppearance;
import com.tupinicraft.client.roulette.BirthRouletteScreen;
import com.tupinicraft.client.ui.CycleSelector;
import com.tupinicraft.client.ui.EntityPreview;
import com.tupinicraft.client.ui.TupiButton;
import com.tupinicraft.client.ui.TupiScreen;
import com.tupinicraft.client.ui.Ui;
import com.tupinicraft.network.CharacterAppearanceClientCache;
import com.tupinicraft.network.CharacterAppearanceUpdatePayload;
import com.tupinicraft.network.ProfileClientCache;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Criador de personagem: previa 3D do proprio jogador (rig + Body Layers) atualizada ao vivo, abas Corpo,
 * Cabelo e Rosto, botao aleatorio. A previa e so local; o servidor valida e confirma ao salvar.
 */
public final class CharacterCreationScreen extends TupiScreen {
    private static final String KEY = "gui.tupinicraft.creator.";
    private enum Tab { BODY, HAIR, FACE }

    private int bodyType, hairStyle, hairColor, skinTone, face;
    private Tab tab = Tab.BODY;
    private final List<TupiButton> tabButtons = new ArrayList<>();
    private TupiButton saveButton;
    private boolean waitingForServer;
    private boolean saveError;
    private int requestedAppearance;
    private long revisionBeforeSave;
    private int waitTicks;
    // Previa: giro por arrasto e "respiro" leve.
    private float previewYaw = -25F;
    private float yawVelocity;
    private boolean dragging;
    private int previewX1, previewY1, previewX2, previewY2;

    public CharacterCreationScreen() {
        super(Component.translatable(KEY + "title"));
        Minecraft mc = Minecraft.getInstance();
        CharacterAppearance current = mc.player == null ? CharacterAppearance.defaults()
                : CharacterAppearanceClientCache.confirmed(mc.player.getUUID());
        bodyType = current.bodyType();
        hairStyle = current.hairStyle();
        hairColor = current.hairColor();
        skinTone = current.skinTone();
        face = current.face();
    }

    @Override
    protected void init() {
        layoutPanel(380, 236);
        tabButtons.clear();
        previewX1 = left + 10;
        previewY1 = top + 28;
        previewX2 = left + Math.min(140, panelWidth / 2 - 10);
        previewY2 = top + panelHeight - 52;
        int colX = previewX2 + 10;
        int colW = left + panelWidth - 10 - colX;

        Tab[] tabs = Tab.values();
        int tabW = (colW - 4 * (tabs.length - 1)) / tabs.length;
        for (int i = 0; i < tabs.length; i++) {
            Tab t = tabs[i];
            TupiButton b = addRenderableWidget(new TupiButton(colX + i * (tabW + 4), top + 28, tabW, 18,
                    Component.translatable(KEY + "tab." + t.name().toLowerCase()), button -> {
                tab = t;
                rebuildWidgets();
            }).selected(t == tab));
            tabButtons.add(b);
        }
        int rowY = top + 54;
        switch (tab) {
            case BODY -> {
                addRenderableWidget(new CycleSelector(colX, rowY, colW, 20, Component.translatable(KEY + "body"),
                        CharacterAppearance.BODY_IDS.length, bodyType,
                        i -> Component.translatable(KEY + "body." + CharacterAppearance.BODY_IDS[i]), i -> { bodyType = i; preview(); }));
                addRenderableWidget(new CycleSelector(colX, rowY + 24, colW, 20, Component.translatable(KEY + "skin"),
                        CharacterAppearance.SKIN_IDS.length, skinTone,
                        i -> Component.translatable(KEY + "skin." + CharacterAppearance.SKIN_IDS[i]), i -> { skinTone = i; preview(); })
                        .swatches(i -> CharacterAppearance.SKIN_ARGB[i]));
            }
            case HAIR -> {
                addRenderableWidget(new CycleSelector(colX, rowY, colW, 20, Component.translatable(KEY + "hair"),
                        CharacterAppearance.HAIR_IDS.length, hairStyle,
                        i -> Component.translatable(KEY + "hair." + CharacterAppearance.HAIR_IDS[i]), i -> { hairStyle = i; preview(); }));
                addRenderableWidget(new CycleSelector(colX, rowY + 24, colW, 20, Component.translatable(KEY + "color"),
                        CharacterAppearance.COLOR_IDS.length, hairColor,
                        i -> Component.translatable(KEY + "color." + CharacterAppearance.COLOR_IDS[i]), i -> { hairColor = i; preview(); })
                        .swatches(i -> CharacterAppearance.COLOR_ARGB[i]));
                // Paleta clicavel
                int sw = Math.min(18, (colW - 4 * (CharacterAppearance.COLOR_IDS.length - 1)) / CharacterAppearance.COLOR_IDS.length);
                for (int i = 0; i < CharacterAppearance.COLOR_IDS.length; i++) {
                    final int color = i;
                    addRenderableWidget(new TupiButton(colX + i * (sw + 4), rowY + 52, sw, sw, Component.empty(), b -> {
                        hairColor = color;
                        preview();
                        rebuildWidgets();
                    }).selected(i == hairColor).accent(CharacterAppearance.COLOR_ARGB[i]).swatch(CharacterAppearance.COLOR_ARGB[i])
                            .tooltip(Component.translatable(KEY + "color." + CharacterAppearance.COLOR_IDS[i])));
                }
            }
            case FACE -> addRenderableWidget(new CycleSelector(colX, rowY, colW, 20, Component.translatable(KEY + "face"),
                    CharacterAppearance.FACE_IDS.length, face,
                    i -> Component.translatable(KEY + "face." + CharacterAppearance.FACE_IDS[i]), i -> { face = i; preview(); }));
        }
        int footerY = top + panelHeight - 28;
        addRenderableWidget(new TupiButton(previewX1, previewY2 + 4, previewX2 - previewX1, 18,
                Component.translatable(KEY + "random"), b -> randomize()).accent(0xFF8FB3C9));
        addRenderableWidget(new TupiButton(left + 10, footerY, 80, 20, Component.translatable(KEY + "back"), b -> onClose()));
        saveButton = addRenderableWidget(new TupiButton(left + panelWidth - 100, footerY, 90, 20,
                Component.translatable(KEY + "save"), b -> saveCharacter()).accent(Ui.GREEN));
        saveButton.active = !waitingForServer;
        preview();
    }

    private CharacterAppearance current(boolean created) {
        return new CharacterAppearance(created, bodyType, hairStyle, hairColor, skinTone, face);
    }

    private void preview() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) CharacterAppearanceClientCache.setPreview(mc.player.getUUID(), current(true));
    }

    private void randomize() {
        RandomSource r = RandomSource.create();
        bodyType = r.nextInt(CharacterAppearance.BODY_IDS.length);
        hairStyle = r.nextInt(CharacterAppearance.HAIR_IDS.length);
        hairColor = r.nextInt(CharacterAppearance.COLOR_IDS.length);
        skinTone = r.nextInt(CharacterAppearance.SKIN_IDS.length);
        face = r.nextInt(CharacterAppearance.FACE_IDS.length);
        yawVelocity = 25F;
        vfx.burst((previewX1 + previewX2) / 2F, (previewY1 + previewY2) / 2F, 24, Ui.GOLD, 3F);
        rebuildWidgets();
    }

    /** So considera a criacao concluida quando o servidor confirma a aparencia via sync. */
    private void saveCharacter() {
        Minecraft mc = Minecraft.getInstance();
        if (waitingForServer || mc.player == null) return;
        requestedAppearance = current(true).pack();
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
        previewYaw += yawVelocity;
        yawVelocity *= 0.85F;
        if (!waitingForServer) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            waitingForServer = false;
            return;
        }
        var uuid = mc.player.getUUID();
        if (CharacterAppearanceClientCache.revision(uuid) > revisionBeforeSave) {
            CharacterAppearance confirmed = CharacterAppearanceClientCache.confirmed(uuid);
            if (confirmed.created() && confirmed.pack() == requestedAppearance) {
                CharacterAppearanceClientCache.clearPreview();
                // Personagem novo ainda sem nascimento: segue para a roleta.
                mc.setScreen(ProfileClientCache.profile().born() ? new CharacterHubScreen() : new BirthRouletteScreen());
                return;
            }
        }
        if (++waitTicks >= 200) {
            waitingForServer = false;
            saveError = true;
            saveButton.active = true;
        }
    }

    @Override
    public void removed() {
        CharacterAppearanceClientCache.clearPreview();
        super.removed();
    }

    @Override
    public void onClose() {
        Minecraft mc = Minecraft.getInstance();
        CharacterAppearanceClientCache.clearPreview();
        if (mc.player != null && CharacterAppearanceClientCache.confirmed(mc.player.getUUID()).created()) {
            mc.setScreen(new CharacterHubScreen());
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
                    Math.min(48, (previewY2 - previewY1) * 0.38F), previewYaw + bob, 6F, mc.player);
        }
        g.drawCenteredString(font, Component.translatable(KEY + "drag"), cx, previewY1 + 4, Ui.MUTED);

        int colX = previewX2 + 10;
        if (tab == Tab.FACE || tab == Tab.BODY) drawPortrait(g, left + panelWidth - 66, top + 108);
        int infoY = top + panelHeight - 50;
        if (waitingForServer || saveError) {
            g.drawString(font, Component.translatable(waitingForServer ? KEY + "saving" : KEY + "save_error"),
                    colX, infoY, saveError ? Ui.RED : Ui.GREEN, false);
        } else {
            g.drawString(font, Component.translatable(KEY + "note"), colX, infoY, Ui.MUTED, false);
        }
    }

    /** Retrato 2D com pele e expressao (as mesmas escolhas aparecem na previa 3D pelas Body Layers de rosto e pele). */
    private void drawPortrait(GuiGraphics g, int x, int y) {
        int skin = CharacterAppearance.SKIN_ARGB[skinTone];
        int hair = CharacterAppearance.COLOR_ARGB[hairColor];
        g.fill(x - 2, y - 2, x + 54, y + 54, 0xFF06080A);
        g.fill(x, y, x + 52, y + 52, 0xFF2A3644);
        g.fill(x + 10, y + 10, x + 42, y + 46, skin);
        if (hairStyle != 0) g.fill(x + 10, y + 6, x + 42, y + 14, hair);
        g.fill(x + 17, y + 22, x + 21, y + 26, 0xFF2B2628);
        g.fill(x + 31, y + 22, x + 35, y + 26, 0xFF2B2628);
        switch (face) {
            case 1 -> {
                g.fill(x + 18, y + 34, x + 34, y + 36, 0xFF743B43);
                g.fill(x + 20, y + 36, x + 32, y + 38, 0xFF743B43);
            }
            case 2 -> {
                g.fill(x + 18, y + 35, x + 34, y + 37, 0xFF743B43);
                g.fill(x + 15, y + 18, x + 23, y + 20, hair);
                g.fill(x + 29, y + 18, x + 37, y + 20, hair);
            }
            default -> g.fill(x + 20, y + 35, x + 32, y + 37, 0xFF743B43);
        }
    }
}
