package com.respectcraft.client.holo;

import com.respectcraft.client.character.CharacterCreationScreen;
import com.respectcraft.client.character.CharacterCreatorKeybinds;
import com.respectcraft.client.roulette.BirthRouletteScreen;
import com.respectcraft.client.ui.RespectScreen;
import com.respectcraft.client.ui.Ui;
import com.respectcraft.skill.SkillDef;
import com.respectcraft.ui.HoloGeometry;
import net.minecraft.client.Camera;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

/**
 * Menu do personagem da tecla K (Etapa 4.8.1): placas holograficas soltas no espaco em volta do ponto de vista.
 *
 * <p><b>Composicao espacial.</b> Cada secao e uma placa propria num <b>arco</b> (cilindro) centrado no olho: a
 * secao escolhida fica na frente, no plano de referencia (texto pixel a pixel), e as vizinhas ficam giradas para o
 * olho, mais fundas e mais apagadas nas laterais, com o mundo visivel entre elas. Navegar gira o arco com uma mola:
 * a placa nova vem para a frente, a antiga recua para o lado. Acima flutua a fita de identidade (inclinada para
 * baixo), abaixo uma fileira curva de chips de navegacao (inclinada para cima), e o holograma 3D do proprio jogador
 * fica de pe na frente, num pedestal; na secao Character ele sobe no palco da propria placa e acompanha o arco.
 *
 * <p><b>Arquitetura.</b> Cada placa desenha o conteudo numa superficie propria ({@link HoloSurface}, framebuffer
 * reaproveitado) e a passada {@link Holo3D} projeta tudo com a perspectiva fora do eixo de {@link HoloGeometry},
 * junto com o jogador, feixes de luz entre as placas e particulas. O clique faz o caminho inverso com as matrizes do
 * mesmo quadro (raio do cursor -> plano da placa -> coordenada local -> regiao registrada no desenho).
 *
 * <p><b>Dados.</b> So exibe as caches do cliente; compras de habilidade vao como pedido ao servidor. Nenhuma
 * transicao visual muda dados de jogo. Nada e criado no mundo: o menu e privado deste cliente.
 *
 * <p><b>Etapa 4.8.4-A.</b> O padrao passou a ser o holograma no mundo ({@link HoloWorld}); esta tela ficou como
 * reserva temporaria de desenvolvimento ({@code mode = "screen"} na config de cliente), sem recursos novos. A
 * composicao, as superficies e a passada 3D moraram para {@link HoloMenu}; aqui sobram a camera virtual, a entrada
 * pelo cursor e o fundo do Rimulib.
 */
public final class HoloCharacterScreen extends RespectScreen {
    /** Secoes do menu, na ordem do arco. */
    public enum Section {
        OVERVIEW("overview", "carteira_trabalho", HoloStyle.CYAN, false),
        CHARACTER("character", "identidade_dinheiro", HoloStyle.CYAN_SOFT, true),
        BODY("body", "penico", HoloStyle.GREEN, false),
        SPECIALITY("speciality", "moeda_real", HoloStyle.VIOLET, false),
        SKILLS("skills", "niobio", HoloStyle.GOLD, true);

        final String id;
        final ResourceLocation icon;
        final int accent;
        /** Usa a altura inteira (palco/arvore) em vez de encolher ate o conteudo. */
        final boolean tall;

        Section(String id, String icon, int accent, boolean tall) {
            this.id = id;
            this.icon = SkillDef.iconTexture(icon);
            this.accent = accent;
            this.tall = tall;
        }

        String key() { return "gui.respectcraft.holo.section." + id; }
    }

    /** Campo de visao vertical da camera virtual do menu (independente do FOV do jogo, para a leitura nao mudar). */
    static final float FOV = HoloMenu.FOV;

    // ---------------------------------------------------------------- estado da casca
    final HoloMenu menu;
    private long openNanos;
    private boolean opened;
    private float screenW, screenH;

    // camera virtual
    private float distance;
    private float eyeX, eyeY;
    private float orbitYaw, orbitPitch;
    private final float[] orbitYawVel = new float[1], orbitPitchVel = new float[1];
    private boolean orbiting;
    private float anchorYaw, anchorPitch;
    private boolean anchored;

    // entrada
    private final Vector3f hit = new Vector3f();
    private HoloPanel dragPanel;
    private HoloZone dragZone;
    private int dragButton = -1;
    private float dragStartX, dragStartY;
    private boolean dragMoved;

    /** Tecla K: sempre abre na visao geral. */
    public HoloCharacterScreen() { this(Section.OVERVIEW); }

    public HoloCharacterScreen(Section initial) {
        super(Component.translatable("gui.respectcraft.holo.title"));
        this.menu = new HoloMenu(new HoloMenu.Host() {
            @Override public void openCreator() { minecraft.setScreen(new CharacterCreationScreen()); }
            @Override public void openRoulette() { minecraft.setScreen(new BirthRouletteScreen()); }
            @Override public void requestClose() { onClose(); }
            @Override public void relayout() { init(); }
        }, initial);
    }

    /** Secao em que a tela foi aberta (o modo mundo usa ao assumir a abertura no lugar dela). */
    Section initialSection() { return menu.section(); }

    // ================================================================ layout

    @Override
    protected void init() {
        var window = minecraft.getWindow();
        screenW = (float) (window.getWidth() / window.getGuiScale());
        screenH = (float) (window.getHeight() / window.getGuiScale());
        // "Menu size" acima de 100% nunca deixa o layout menor que a menor GUI (320 x 240)
        float uiScale = Math.min(HoloConfig.scale(), Math.max(0.5F, Math.min(screenW / 320F, screenH / 240F)));
        distance = HoloGeometry.referenceDistance(screenH, FOV);
        menu.layout(screenW / uiScale, screenH / uiScale, uiScale, distance / uiScale);
        menu.unit = uiScale;
        menu.screenW = screenW;
        menu.screenH = screenH;
    }

    // ================================================================ ciclo de vida

    private void onOpened() {
        opened = true;
        Camera camera = minecraft.gameRenderer.getMainCamera();
        anchorYaw = camera.getYRot();
        anchorPitch = camera.getXRot();
        anchored = true;
        menu.opened();
    }

    @Override
    public void tick() {
        super.tick();
        menu.tick();
        if (menu.closing) {
            float since = (System.nanoTime() - openNanos) / 1e9F - menu.closeStart;
            if (since >= menu.closeDuration() || since > 1.5F) finishClose();
        }
    }

    /** Esc, K ou o botao de fechar: as placas se dobram e so entao o jogo volta. */
    @Override
    public void onClose() {
        if (menu.closing) return;
        if (!opened) {
            finishClose();
            return;
        }
        releaseDrags();
        menu.beginClose();
    }

    private void finishClose() {
        if (minecraft != null && minecraft.screen == this) super.onClose();
    }

    @Override
    public void removed() {
        releaseDrags();
        HoloBackdrop.clear();
        menu.release();
        super.removed();
    }

    @Override
    public boolean shouldCloseOnEsc() { return true; }

    private void releaseDrags() {
        if (dragZone != null && dragZone.drag != null) dragZone.drag.release(dragStartX, dragStartY, false);
        dragZone = null;
        dragPanel = null;
        dragButton = -1;
        menu.rotatingPlayer = false;
        orbiting = false;
    }

    // ================================================================ desenho

    /** Nao usado: {@link #render} substitui o desenho plano da base pela passada holografica. */
    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {}

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long now = System.nanoTime();
        if (openNanos == 0L) openNanos = now;
        menu.advance((now - openNanos) / 1e9F);
        if (!opened) onOpened();
        checkLostButtons();

        float envelope = menu.envelope();
        HoloBackdrop.set(envelope);
        backdrop2D(g, envelope);

        updateCamera();
        menu.animate();
        menu.freeze();
        pickHot();
        menu.drawSurfaces(g, (float) minecraft.getWindow().getGuiScale(), menu.uiScale, false);
        HoloPanel hot = menu.hotPanel;
        menu.sidePlateTooltip();

        g.flush();
        menu.render3D(false);
        menu.updateParticles();

        boolean settled = hot != null && hot.contentAlpha > 0.6F;
        if (menu.tooltip != null && settled && !menu.closing && dragZone == null && !menu.rotatingPlayer && !orbiting) {
            g.pose().pushPose();
            g.pose().translate(0F, 0F, 400F);
            g.renderComponentTooltip(font, menu.tooltip, mouseX, mouseY);
            g.pose().popPose();
        }
    }

    /** Escurecimento leve do mundo com vinheta (alem da passada do Rimulib), sem desfoque. */
    private void backdrop2D(GuiGraphics g, float k) {
        int top = Ui.alpha(0xFF020812, 0.42F * k), mid = Ui.alpha(0xFF04101C, 0.18F * k), bottom = Ui.alpha(0xFF02060C, 0.5F * k);
        g.fillGradient(0, 0, width, height / 2, top, mid);
        g.fillGradient(0, height / 2, width, height, mid, bottom);
    }

    private void updateCamera() {
        float dt = menu.dt;
        float ndcX = Mth.clamp(ndcX(), -1F, 1F), ndcY = Mth.clamp(ndcY(), -1F, 1F);
        float motion = HoloConfig.motion();
        float weight = motion * HoloAnim.outCubic(HoloAnim.window(menu.time, 0.42F, 0.45F));
        eyeX = HoloAnim.approach(eyeX, ndcX * 44F * weight, 5F, dt);
        eyeY = HoloAnim.approach(eyeY, ndcY * 26F * weight, 5F, dt);

        // Ancora hibrida: a cena acompanha um pouco a camera real (empurroes, giro do servidor) e se recentraliza.
        Camera camera = minecraft.gameRenderer.getMainCamera();
        float worldYaw = 0F, worldPitch = 0F;
        if (anchored) {
            float dyaw = Mth.wrapDegrees(camera.getYRot() - anchorYaw);
            float dpitch = camera.getXRot() - anchorPitch;
            if (Math.abs(dyaw) > 20F) anchorYaw = camera.getYRot() - Math.signum(dyaw) * 20F;
            if (Math.abs(dpitch) > 14F) anchorPitch = camera.getXRot() - Math.signum(dpitch) * 14F;
            anchorYaw += Mth.wrapDegrees(camera.getYRot() - anchorYaw) * (1F - (float) Math.exp(-1.4F * dt));
            anchorPitch += (camera.getXRot() - anchorPitch) * (1F - (float) Math.exp(-1.4F * dt));
            if (!HoloConfig.reducedMotion()) {
                worldYaw = Mth.wrapDegrees(camera.getYRot() - anchorYaw) * 0.6F;
                worldPitch = (camera.getXRot() - anchorPitch) * 0.6F;
            }
        }
        if (!orbiting) {
            if (HoloConfig.reducedMotion()) {
                orbitYaw = orbitPitch = 0F;
            } else {
                orbitYaw = HoloAnim.spring(orbitYaw, 0F, orbitYawVel, 38F, 10F, dt);
                orbitPitch = HoloAnim.spring(orbitPitch, 0F, orbitPitchVel, 38F, 10F, dt);
            }
        }
        distance = HoloGeometry.referenceDistance(screenH, FOV);
        menu.eyeZ = distance / menu.uiScale;
        HoloGeometry.offAxis(menu.projection, screenW, screenH, distance, eyeX, eyeY, 8F, distance * 6F);
        HoloGeometry.view(menu.view, distance, eyeX, eyeY, orbitYaw + worldYaw, orbitPitch + worldPitch);
        HoloGeometry.layoutToMenu(menu.layout, screenW, screenH, menu.uiScale);
    }

    // ================================================================ cursor

    private float ndcX() {
        return (float) (minecraft.mouseHandler.xpos() / Math.max(1, minecraft.getWindow().getScreenWidth()) * 2.0 - 1.0);
    }

    private float ndcY() {
        return (float) (1.0 - minecraft.mouseHandler.ypos() / Math.max(1, minecraft.getWindow().getScreenHeight()) * 2.0);
    }

    private void pickHot() {
        menu.clearHot();
        if (dragZone != null || menu.rotatingPlayer || orbiting) return;
        float nx = ndcX(), ny = ndcY();
        if (!menu.modalOpen && playerHit(nx, ny)) return;
        HoloPanel p = menu.pick(nx, ny, hit);
        if (p != null) menu.setHot(p, hit.x, hit.y);
    }

    private boolean playerHit(float nx, float ny) {
        if (!menu.playerBoxValid) return false;
        float gx = HoloGeometry.ndcToGuiX(nx, screenW), gy = HoloGeometry.ndcToGuiY(ny, screenH);
        return gx >= menu.playerBoxX0 && gx <= menu.playerBoxX1 && gy >= menu.playerBoxY0 && gy <= menu.playerBoxY1;
    }

    /** Janela perdeu o foco com botao apertado: solta o arrasto em vez de prender o cursor. */
    private void checkLostButtons() {
        long handle = minecraft.getWindow().getWindow();
        if (dragButton >= 0 && GLFW.glfwGetMouseButton(handle, dragButton) != GLFW.GLFW_PRESS) {
            if (dragZone != null && dragZone.drag != null) dragZone.drag.release(dragStartX, dragStartY, false);
            dragZone = null;
            dragPanel = null;
            dragButton = -1;
        }
        if (menu.rotatingPlayer && GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) menu.rotatingPlayer = false;
        if (orbiting && GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_RIGHT) != GLFW.GLFW_PRESS
                && GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_MIDDLE) != GLFW.GLFW_PRESS) orbiting = false;
    }

    // ================================================================ entrada

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (menu.closing || menu.time < 0.06F) return true;
        float nx = ndcX(), ny = ndcY();
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT || button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            if (!menu.modalOpen && !HoloConfig.reducedMotion()) {
                orbiting = true;
                dragButton = -1;
            }
            return true;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;
        if (!menu.modalOpen && playerHit(nx, ny)) {
            menu.rotatingPlayer = true;
            return true;
        }
        HoloPanel p = menu.pick(nx, ny, hit);
        if (p == null) {
            if (menu.modalOpen) menu.closeSettings();
            return true;
        }
        // placa de lado vem para a frente; regiao de arrasto e capturada; clique roda a acao (caminho comum ao mundo)
        HoloZone z = menu.press(p, hit.x, hit.y);
        if (z != null) {
            dragPanel = p;
            dragZone = z;
            dragButton = button;
            dragStartX = hit.x;
            dragStartY = hit.y;
            dragMoved = false;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (menu.closing) return true;
        if (orbiting) {
            orbitYaw = Mth.clamp(orbitYaw + (float) dragX * 0.35F, -26F, 26F);
            orbitPitch = Mth.clamp(orbitPitch + (float) dragY * 0.3F, -16F, 16F);
            orbitYawVel[0] = orbitPitchVel[0] = 0F;
            return true;
        }
        if (menu.rotatingPlayer) {
            menu.rotatePlayer((float) dragX * 1.6F);
            return true;
        }
        if (dragZone != null && dragPanel != null) {
            if (HoloGeometry.intersect(dragPanel.inverse, ndcX(), ndcY(), hit)) {
                if (Math.abs(hit.x - dragStartX) + Math.abs(hit.y - dragStartY) > 3F) dragMoved = true;
                dragZone.drag.move(hit.x, hit.y);
            }
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (orbiting && (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT || button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE)) {
            orbiting = false;
            return true;
        }
        if (menu.rotatingPlayer && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            menu.rotatingPlayer = false;
            return true;
        }
        if (dragZone != null && button == dragButton) {
            hit.set(dragStartX, dragStartY, 0F);
            if (dragPanel != null) HoloGeometry.intersect(dragPanel.inverse, ndcX(), ndcY(), hit);
            HoloZone z = dragZone;
            dragZone = null;
            dragPanel = null;
            dragButton = -1;
            z.drag.release(hit.x, hit.y, !dragMoved);
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (menu.closing || menu.modalOpen) return true;
        HoloPanel p = menu.pick(ndcX(), ndcY(), hit);
        // sobre a placa da frente rola o conteudo; em qualquer outro lugar gira o arco
        boolean scrolled = p == menu.mainPanel() && menu.view(menu.section()).scrollBy(scrollY);
        if (!scrolled && scrollY != 0) menu.cycle(scrollY > 0 ? -1 : 1);
        // sempre consumido: com o menu aberto a roda nunca chega na hotbar
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (menu.modalOpen) menu.closeSettings();
            else onClose();
            return true;
        }
        if (CharacterCreatorKeybinds.isMenuKey(keyCode, scanCode)) {
            if (menu.modalOpen) menu.closeSettings();
            onClose();
            return true;
        }
        if (menu.closing) return true;
        if (menu.modalOpen) return true;
        Section section = menu.section();
        switch (keyCode) {
            case GLFW.GLFW_KEY_TAB -> menu.cycle((modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? -1 : 1);
            case GLFW.GLFW_KEY_LEFT -> menu.cycle(-1);
            case GLFW.GLFW_KEY_RIGHT -> menu.cycle(1);
            case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_PAGE_UP -> menu.view(section).scrollBy(keyCode == GLFW.GLFW_KEY_UP ? 1 : 4);
            case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_PAGE_DOWN -> menu.view(section).scrollBy(keyCode == GLFW.GLFW_KEY_DOWN ? -1 : -4);
            case GLFW.GLFW_KEY_LEFT_BRACKET -> menu.view(section).arrow(-1);
            case GLFW.GLFW_KEY_RIGHT_BRACKET -> menu.view(section).arrow(1);
            case GLFW.GLFW_KEY_R -> recenter();
            default -> {
                if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_5) {
                    menu.select(Section.values()[keyCode - GLFW.GLFW_KEY_1]);
                } else {
                    return super.keyPressed(keyCode, scanCode, modifiers);
                }
            }
        }
        return true;
    }

    /** Volta a cena ao centro (giro do arrasto direito e ancora da camera). */
    void recenter() {
        orbitYaw = orbitPitch = 0F;
        orbitYawVel[0] = orbitPitchVel[0] = 0F;
        Camera camera = minecraft.gameRenderer.getMainCamera();
        anchorYaw = camera.getYRot();
        anchorPitch = camera.getXRot();
        Ui.playClick(1.3F);
    }
}
