package com.respectcraft.client.holo;

import com.respectcraft.client.character.CharacterCreationScreen;
import com.respectcraft.client.character.CharacterCreatorKeybinds;
import com.respectcraft.client.roulette.BirthRouletteScreen;
import com.respectcraft.client.ui.RespectScreen;
import com.respectcraft.client.ui.Ui;
import com.respectcraft.registry.ModSounds;
import com.respectcraft.skill.SkillDef;
import com.respectcraft.ui.HoloGeometry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
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
    static final float FOV = 42F;
    /** Passo angular entre placas vizinhas no arco (graus). */
    static final float ARC_STEP = 31F;
    /** Ultima altura de conteudo de cada secao (cache so visual): a placa ja abre no tamanho certo. */
    private static final float[] FIT_CACHE = new float[Section.values().length];
    private static final float CLOSE_TIME = 0.3F;

    // ---------------------------------------------------------------- estado
    private Section section;
    private float sectionStart = -10F;
    private boolean closing;
    private float closeStart;
    private boolean modalOpen;
    private float modalAmt;

    private long openNanos;
    private float time, dt;
    private boolean opened;

    // layout (unidades de layout)
    private float uiScale = 1F, screenW, screenH, layoutW, layoutH, eyeZ;
    private float centerX, plateW, plateTop, plateMaxH, headerY, headerW, headerH, chipsY, chipW, chipH;
    private boolean dockAvailable;

    // arco (posicao continua em "indices de secao") e placas
    private float carousel;
    private final float[] carouselVel = new float[1];
    private final HoloPanel[] plates = new HoloPanel[Section.values().length];
    private final HoloPanel[] chips = new HoloPanel[Section.values().length];
    final HoloPanel header = new HoloPanel("header", true);
    final HoloPanel modal = new HoloPanel("modal", true);
    private final List<HoloPanel> all = new ArrayList<>();
    private final List<HoloPanel> drawOrder = new ArrayList<>();

    // camera
    private final Matrix4f projection = new Matrix4f(), view = new Matrix4f(), layout = new Matrix4f();
    private final Matrix4f viewLayout = new Matrix4f(), projViewLayout = new Matrix4f();
    private final Matrix4f scratchA = new Matrix4f(), scratchB = new Matrix4f();
    private float distance;
    private float eyeX, eyeY;
    private float orbitYaw, orbitPitch;
    private final float[] orbitYawVel = new float[1], orbitPitchVel = new float[1];
    private boolean orbiting;
    private float anchorYaw, anchorPitch;
    private boolean anchored;

    // holograma do jogador
    private float playerYaw = -24F;
    private float stageAmt;
    private boolean rotatingPlayer;
    private boolean playerTouched;
    private float playerBoxX0, playerBoxY0, playerBoxX1, playerBoxY1;
    private boolean playerBoxValid;
    private final float[] placement = new float[4];

    // entrada
    private final Vector3f hit = new Vector3f();
    private HoloPanel hotPanel;
    private HoloPanel dragPanel;
    private HoloZone dragZone;
    private int dragButton = -1;
    private float dragStartX, dragStartY;
    private boolean dragMoved;
    private List<Component> tooltip;
    private String lastHoverKey;

    // efeitos
    final HoloParticles particles = new HoloParticles();
    private final Holo3D r3d = new Holo3D();
    private final HoloCanvas canvas = new HoloCanvas();
    private float moteClock;
    private final Vector3f va = new Vector3f(), vb = new Vector3f();

    // conteudo
    private final HoloView[] views;
    private final HoloNav navView = new HoloNav(this);
    private final HoloSettings settings = new HoloSettings(this);
    private boolean settingsDirty;

    /** Tecla K: sempre abre na visao geral. */
    public HoloCharacterScreen() { this(Section.OVERVIEW); }

    public HoloCharacterScreen(Section initial) {
        super(Component.translatable("gui.respectcraft.holo.title"));
        this.section = initial == null ? Section.OVERVIEW : initial;
        this.carousel = section.ordinal();
        this.views = new HoloView[]{new OverviewView(this), new CharacterView(this), new BodyView(this),
                new SpecialityView(this), new SkillsView(this)};
        for (Section s : Section.values()) {
            plates[s.ordinal()] = new HoloPanel("plate_" + s.id, true);
            chips[s.ordinal()] = new HoloPanel("chip_" + s.id, true);
        }
        all.add(header);
        for (HoloPanel p : plates) all.add(p);
        for (HoloPanel p : chips) all.add(p);
        all.add(modal);
    }

    // ================================================================ layout

    @Override
    protected void init() {
        var window = minecraft.getWindow();
        screenW = (float) (window.getWidth() / window.getGuiScale());
        screenH = (float) (window.getHeight() / window.getGuiScale());
        // "Menu size" acima de 100% nunca deixa o layout menor que a menor GUI (320 x 240)
        uiScale = Math.min(HoloConfig.scale(), Math.max(0.5F, Math.min(screenW / 320F, screenH / 240F)));
        layoutW = screenW / uiScale;
        layoutH = screenH / uiScale;
        distance = HoloGeometry.referenceDistance(screenH, FOV);
        eyeZ = distance / uiScale;
        // Em telas grandes (GUI 1) o conjunto continua compacto no meio, com o mundo em volta.
        float effH = Math.min(layoutH, 390F);
        float oy = (layoutH - effH) / 2F;
        centerX = layoutW / 2F;
        boolean small = layoutW < 420F;
        plateW = small ? Math.min(layoutW - 52F, 290F) : Mth.clamp(layoutW * 0.46F, 290F, 350F);
        headerH = small ? 26F : 30F;
        headerY = oy + 6F;
        headerW = Math.min(plateW + 80F, layoutW - 20F);
        chipH = small ? 16F : 18F;
        chipsY = oy + effH - chipH - (small ? 7F : 16F);
        chipW = Math.min(74F, (Math.min(layoutW - 30F, plateW + 70F)) / Section.values().length - 5F);
        plateTop = headerY + headerH + (small ? 8F : 12F);
        plateMaxH = chipsY - (small ? 8F : 14F) - plateTop;
        dockAvailable = layoutW >= 420F && effH >= 220F;
        float mw = Math.min(236F, layoutW - 40F), mh = Math.min(196F, effH - 24F);
        modal.rect((layoutW - mw) / 2F, (layoutH - mh) / 2F, mw, mh);
        for (HoloPanel p : plates) p.h = 0F; // fitHeight escolhe a altura no proximo quadro, sem animar
    }

    // ================================================================ secoes

    Section section() { return section; }
    HoloView view(Section s) { return views[s.ordinal()]; }
    float time() { return time; }
    boolean dockAvailable() { return dockAvailable; }

    void select(Section next) {
        if (next == null || next == section || closing) return;
        section = next;
        sectionStart = time;
        view(next).onEnter();
        Ui.playUi(ModSounds.UI_HOVER.get(), 1.15F, 0.45F);
        if (HoloConfig.motion() > 0) particles.alongEdges(plates[next.ordinal()], 16, next.accent, 30F);
    }

    void cycle(int dir) {
        Section[] s = Section.values();
        int i = section.ordinal() + dir;
        if (i >= 0 && i < s.length) select(s[i]);
    }

    void openCreator() { minecraft.setScreen(new CharacterCreationScreen()); }

    void openRoulette() { minecraft.setScreen(new BirthRouletteScreen()); }

    void openSettings() {
        modalOpen = true;
        Ui.playClick(0.9F);
    }

    void closeSettings() {
        if (!modalOpen) return;
        modalOpen = false;
        HoloConfig.save();
        settingsDirty = false;
        Ui.playClick(0.8F);
        init();
    }

    void settingsChanged() { settingsDirty = true; }

    /** Refaz o layout (escala do menu mudou no painel Display). */
    void relayout() { init(); }

    void setHoloTooltip(List<Component> lines) { this.tooltip = lines; }

    // ================================================================ ciclo de vida

    private void onOpened() {
        opened = true;
        Camera camera = minecraft.gameRenderer.getMainCamera();
        anchorYaw = camera.getYRot();
        anchorPitch = camera.getXRot();
        anchored = true;
        Ui.playUi(ModSounds.UI_HOVER.get(), 0.62F, 0.75F);
        view(section).onEnter();
    }

    @Override
    public void tick() {
        super.tick();
        for (HoloView v : views) v.tick();
        if (closing) {
            float since = (System.nanoTime() - openNanos) / 1e9F - closeStart;
            if (since >= closeDuration() || since > 1.5F) finishClose();
        }
    }

    private float closeDuration() { return HoloConfig.reducedMotion() ? 0.1F : CLOSE_TIME; }

    /** Esc, K ou o botao de fechar: as placas se dobram e so entao o jogo volta. */
    @Override
    public void onClose() {
        if (closing) return;
        if (!opened) {
            finishClose();
            return;
        }
        closing = true;
        closeStart = time;
        releaseDrags();
        Ui.playUi(ModSounds.UI_HOVER.get(), 0.48F, 0.6F);
        if (HoloConfig.motion() > 0) {
            for (HoloPanel p : plates) if (p.visible && p.alpha > 0.2F) particles.alongEdges(p, 14, p.accent, 46F);
            particles.alongEdges(header, 10, HoloStyle.CYAN, 40F);
        }
    }

    private void finishClose() {
        if (minecraft != null && minecraft.screen == this) super.onClose();
    }

    @Override
    public void removed() {
        releaseDrags();
        if (settingsDirty) HoloConfig.save();
        settingsDirty = false;
        HoloBackdrop.clear();
        particles.clear();
        for (HoloPanel p : all) p.close();
        super.removed();
    }

    @Override
    public boolean shouldCloseOnEsc() { return true; }

    private void releaseDrags() {
        if (dragZone != null && dragZone.drag != null) dragZone.drag.release(dragStartX, dragStartY, false);
        dragZone = null;
        dragPanel = null;
        dragButton = -1;
        rotatingPlayer = false;
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
        float t = (now - openNanos) / 1e9F;
        dt = Mth.clamp(t - time, 0F, 0.1F);
        time = t;
        if (!opened) onOpened();
        checkLostButtons();

        float envelope = envelope();
        HoloBackdrop.set(envelope);
        backdrop2D(g, envelope);

        updateCamera();
        animate();
        for (HoloPanel p : all) if (p.visible) p.freeze(viewLayout, projViewLayout);
        pickHot();
        tooltip = null;
        drawSurfaces(g);
        if (hotPanel != null && isPlate(hotPanel) && sectionOf(hotPanel) != section) {
            Section s = sectionOf(hotPanel);
            tooltip = List.of(Component.translatable(s.key()).withStyle(net.minecraft.ChatFormatting.BOLD),
                    Component.translatable("gui.respectcraft.holo.bring_forward").withStyle(net.minecraft.ChatFormatting.GRAY));
        }

        g.flush();
        r3d.begin(projection);
        try {
            draw3D();
        } finally {
            r3d.end();
        }
        particles.update(dt);
        spawnAmbient();

        boolean settled = hotPanel != null && hotPanel.contentAlpha > 0.6F;
        if (tooltip != null && settled && !closing && dragZone == null && !rotatingPlayer && !orbiting) {
            g.pose().pushPose();
            g.pose().translate(0F, 0F, 400F);
            g.renderComponentTooltip(font, tooltip, mouseX, mouseY);
            g.pose().popPose();
        }
    }

    /** 0..1: abertura, menu inteiro, fechamento. */
    private float envelope() {
        float open = HoloAnim.smooth(HoloAnim.window(time, 0F, 0.35F));
        if (!closing) return open;
        return open * (1F - HoloAnim.smooth(HoloAnim.window(time - closeStart, 0F, closeDuration())));
    }

    /** Escurecimento leve do mundo com vinheta (alem da passada do Rimulib), sem desfoque. */
    private void backdrop2D(GuiGraphics g, float k) {
        int top = Ui.alpha(0xFF020812, 0.42F * k), mid = Ui.alpha(0xFF04101C, 0.18F * k), bottom = Ui.alpha(0xFF02060C, 0.5F * k);
        g.fillGradient(0, 0, width, height / 2, top, mid);
        g.fillGradient(0, height / 2, width, height, mid, bottom);
    }

    private void updateCamera() {
        float ndcX = Mth.clamp(ndcX(), -1F, 1F), ndcY = Mth.clamp(ndcY(), -1F, 1F);
        float motion = HoloConfig.motion();
        float weight = motion * HoloAnim.outCubic(HoloAnim.window(time, 0.42F, 0.45F));
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
        eyeZ = distance / uiScale;
        HoloGeometry.offAxis(projection, screenW, screenH, distance, eyeX, eyeY, 8F, distance * 6F);
        HoloGeometry.view(view, distance, eyeX, eyeY, orbitYaw + worldYaw, orbitPitch + worldPitch);
        HoloGeometry.layoutToMenu(layout, screenW, screenH, uiScale);
        view.mul(layout, viewLayout);
        projection.mul(viewLayout, projViewLayout);
    }

    // ================================================================ composicao espacial

    /**
     * Posiciona um painel no cilindro em volta do olho: angulo {@code deg} (0 = em frente), raio {@code radius} (o
     * plano de referencia tem raio {@code eyeZ}), virado para o olho. {@code y} e o topo do painel.
     */
    private void onArc(HoloPanel p, float deg, float radius, float y, float w, float h) {
        double a = Math.toRadians(deg);
        float cx = centerX + radius * (float) Math.sin(a);
        float cz = eyeZ - radius * (float) Math.cos(a);
        p.rect(cx - w / 2F, y, w, h);
        p.pivotX = 0.5F;
        p.dz = cz;
        p.yaw = -deg;
    }

    /** Estado animado de cada painel neste quadro: arco, abertura, troca de secao, Display e fechamento. */
    private void animate() {
        boolean reduced = HoloConfig.reducedMotion();
        modalAmt = HoloAnim.approach(modalAmt, modalOpen ? 1F : 0F, reduced ? 40F : 14F, dt);
        stageAmt = HoloAnim.approach(stageAmt, section == Section.CHARACTER ? 1F : 0F, reduced ? 40F : 7F, dt);
        float target = section.ordinal();
        if (reduced) {
            carousel = target;
            carouselVel[0] = 0F;
        } else {
            // mola um pouco subamortecida: o arco passa levemente do ponto e assenta
            carousel = HoloAnim.spring(carousel, target, carouselVel, 42F, 9.5F, dt);
        }

        // ---- placas das secoes no arco
        for (Section s : Section.values()) {
            HoloPanel p = plates[s.ordinal()];
            fitHeight(p, s, reduced);
            float a = s.ordinal() - carousel;
            float abs = Math.abs(a);
            p.rest();
            p.pivotY = 0.5F;
            p.accent = view(s).accent();
            float hover = p == hotPanel && s != section && !closing ? 1F : 0F;
            float lift = HoloAnim.clamp01(hover);
            float radius = eyeZ * (1F + 0.24F * Math.min(1F, abs) + 0.12F * Math.max(0F, abs - 1F)) - lift * eyeZ * 0.05F;
            onArc(p, a * ARC_STEP, radius, plateTop + 12F * Math.min(1F, abs), plateW, p.h);
            // leve inclinacao das placas de lado: parecem penduradas no arco, nao alinhadas a tela
            p.pitch = 3F * Math.min(1F, abs);
            float side = abs <= 1F ? HoloAnim.lerp(abs, 1F, 0.62F) : 0.62F * HoloAnim.clamp01(2.05F - abs);
            int delay = Math.min(3, Math.round(abs));
            assemble(p, 0.08F + delay * 0.07F, a < 0 ? -1F : 1F, 0.02F * delay);
            p.alpha *= Math.min(1F, side + 0.2F * lift);
            p.contentAlpha *= side;
            p.outline *= Math.min(1F, side + 0.3F * lift);
            p.visible = p.visible && abs < 2.05F && p.alpha > 0.01F;
            p.interactive = p.interactive && abs < 1.6F;
            p.sweep = -1F;
            if (s == section && !reduced) {
                float since = time - sectionStart;
                if (since < 0.5F) p.sweep = HoloAnim.window(since, 0.06F, 0.4F);
                if (p.sweep >= 1F) p.sweep = -1F;
            }
        }

        // ---- fita de identidade acima (inclinada para baixo, de frente para o olho)
        header.rest();
        header.pivotY = 1F;
        onArc(header, 0F, eyeZ * 1.06F, headerY, headerW, headerH);
        header.pitch = -15F;
        header.accent = HoloStyle.CYAN;
        assemble(header, 0.03F, 1F, 0.08F);
        header.yaw += 0F;

        // ---- chips de navegacao abaixo, numa fileira curva inclinada para cima
        float chipRadius = eyeZ * 1.01F;
        float stepDeg = (float) Math.toDegrees(Math.atan((chipW + 6F) / chipRadius));
        for (Section s : Section.values()) {
            HoloPanel p = chips[s.ordinal()];
            p.rest();
            p.pivotY = 0F;
            boolean active = s == section;
            float k = p == hotPanel ? 1F : 0F;
            onArc(p, (s.ordinal() - 2) * stepDeg, chipRadius - (active ? eyeZ * 0.03F : 0F) - k * eyeZ * 0.012F, chipsY, chipW, chipH);
            p.pitch = 16F;
            p.accent = s.accent;
            assemble(p, 0.2F + s.ordinal() * 0.035F, 1F, 0.04F);
        }

        // ---- painel Display (modal): na frente, empurra o resto para tras
        modal.rest();
        modal.accent = HoloStyle.VIOLET;
        float mk = HoloAnim.outCubic(modalAmt);
        modal.dz = HoloAnim.lerp(mk, -40F, 40F);
        modal.scaleX = modal.scaleY = HoloAnim.lerp(mk, 0.9F, 1F);
        modal.alpha = mk;
        modal.contentAlpha = HoloAnim.window(modalAmt, 0.4F, 0.6F);
        modal.outline = mk;
        modal.visible = modalAmt > 0.01F;
        modal.interactive = modalOpen && modalAmt > 0.5F;
        if (modal.visible) {
            for (HoloPanel p : all) {
                if (p == modal) continue;
                p.dz -= 40F * mk;
                p.alpha *= 1F - 0.45F * mk;
                p.contentAlpha *= 1F - 0.55F * mk;
                p.interactive = false;
            }
        }
        if (closing) closeOut(modal, 0F, 1F);
    }

    /** Altura de uma placa: a inteira, ou so o necessario para o conteudo (ultima medida). */
    private void fitHeight(HoloPanel p, Section s, boolean reduced) {
        HoloView v = view(s);
        float target = plateMaxH;
        if (!s.tall && v.fitsContent()) {
            float desired = v.desiredHeight();
            if (desired <= 0F) desired = FIT_CACHE[s.ordinal()];
            if (desired > 0F) target = Mth.clamp(desired, Math.min(plateMaxH, 120F), plateMaxH);
            FIT_CACHE[s.ordinal()] = desired;
        }
        p.h = reduced || p.h <= 1F || Math.abs(p.h - target) < 0.5F ? target : HoloAnim.approach(p.h, target, 12F, dt);
    }

    /**
     * Abertura de um painel no ar: chega de lado (girado quase de perfil, {@code side} = sentido do giro), vindo do
     * fundo, e desdobra com uma mola curta; o contorno se desenha a partir do canto.
     */
    private void assemble(HoloPanel p, float delay, float side, float closeDelay) {
        p.visible = true;
        p.interactive = true;
        if (HoloConfig.reducedMotion()) {
            float k = HoloAnim.window(time, 0F, 0.15F);
            p.alpha = k;
            p.contentAlpha = k;
            p.outline = k;
        } else {
            float turn = HoloAnim.outBack(HoloAnim.window(time, delay, 0.34F), 1.15F);
            float form = HoloAnim.outCubic(HoloAnim.window(time, delay, 0.3F));
            p.yaw += (1F - turn) * 78F * side;
            p.scaleX *= HoloAnim.lerp(form, 0.6F, 1F);
            p.scaleY *= HoloAnim.lerp(HoloAnim.outBack(HoloAnim.window(time, delay + 0.05F, 0.26F), 1.05F), 0.05F, 1F);
            p.dz += HoloAnim.lerp(form, -160F, 0F);
            p.alpha = HoloAnim.window(time, delay + 0.02F, 0.18F);
            p.outline = HoloAnim.outCubic(HoloAnim.window(time, delay, 0.3F));
            p.contentAlpha = HoloAnim.window(time, delay + 0.2F, 0.2F);
        }
        p.interactive = p.alpha > 0.35F;
        if (closing) closeOut(p, closeDelay, side);
    }

    /** Dissolucao: conteudo some primeiro, a placa gira de perfil, encolhe e recua. */
    private void closeOut(HoloPanel p, float delay, float side) {
        float tc = time - closeStart;
        p.interactive = false;
        if (HoloConfig.reducedMotion()) {
            float k = HoloAnim.window(tc, 0F, closeDuration());
            p.alpha *= 1F - k;
            p.contentAlpha *= 1F - k;
            p.outline *= 1F - k;
            return;
        }
        float k = HoloAnim.inCubic(HoloAnim.window(tc, delay + 0.04F, 0.2F));
        p.contentAlpha *= 1F - HoloAnim.window(tc, 0F, 0.09F);
        p.yaw += k * 70F * side;
        p.scaleY *= HoloAnim.lerp(k, 1F, 0.3F);
        p.dz += HoloAnim.lerp(k, 0F, -110F);
        p.alpha *= 1F - HoloAnim.window(tc, delay + 0.12F, 0.14F);
        p.outline *= 1F - k;
    }

    // ================================================================ cursor

    private float ndcX() {
        return (float) (minecraft.mouseHandler.xpos() / Math.max(1, minecraft.getWindow().getScreenWidth()) * 2.0 - 1.0);
    }

    private float ndcY() {
        return (float) (1.0 - minecraft.mouseHandler.ypos() / Math.max(1, minecraft.getWindow().getScreenHeight()) * 2.0);
    }

    private boolean isPlate(HoloPanel p) {
        for (HoloPanel q : plates) if (q == p) return true;
        return false;
    }

    private Section sectionOf(HoloPanel p) {
        for (Section s : Section.values()) if (plates[s.ordinal()] == p || chips[s.ordinal()] == p) return s;
        return null;
    }

    /** Painel interativo mais perto sob o cursor (o modal, quando aberto, captura tudo). */
    private HoloPanel pick(float nx, float ny, Vector3f out) {
        if (closing) return null;
        if (modalOpen) return modal.interactive && modal.hit(nx, ny, out) ? modal : null;
        HoloPanel best = null;
        float bestT = Float.MAX_VALUE, bx = 0, by = 0;
        for (HoloPanel p : all) {
            if (p == modal || !p.visible || !p.interactive) continue;
            if (p.hit(nx, ny, out) && out.z < bestT) {
                bestT = out.z;
                best = p;
                bx = out.x;
                by = out.y;
            }
        }
        if (best != null) out.set(bx, by, bestT);
        return best;
    }

    private void pickHot() {
        for (HoloPanel p : all) {
            p.hot = false;
            p.cursorX = p.cursorY = -9999F;
        }
        hotPanel = null;
        if (dragZone != null || rotatingPlayer || orbiting) return;
        float nx = ndcX(), ny = ndcY();
        if (!modalOpen && playerHit(nx, ny)) return;
        HoloPanel p = pick(nx, ny, hit);
        if (p != null) {
            p.hot = true;
            p.cursorX = hit.x;
            p.cursorY = hit.y;
            hotPanel = p;
        }
    }

    private boolean playerHit(float nx, float ny) {
        if (!playerBoxValid) return false;
        float gx = HoloGeometry.ndcToGuiX(nx, screenW), gy = HoloGeometry.ndcToGuiY(ny, screenH);
        return gx >= playerBoxX0 && gx <= playerBoxX1 && gy >= playerBoxY0 && gy <= playerBoxY1;
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
        if (rotatingPlayer && GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) rotatingPlayer = false;
        if (orbiting && GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_RIGHT) != GLFW.GLFW_PRESS
                && GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_MIDDLE) != GLFW.GLFW_PRESS) orbiting = false;
    }

    // ================================================================ superficies

    private void drawSurfaces(GuiGraphics g) {
        float guiScale = (float) minecraft.getWindow().getGuiScale();
        drawPanel(g, header, guiScale, headerW, headerH, () -> navView.drawHeader(canvas, header.w, header.h));
        for (Section s : Section.values()) {
            HoloPanel p = plates[s.ordinal()];
            HoloView v = view(s);
            boolean active = s == section;
            // placas de lado mostram o conteudo de verdade, mas sem hover: um clique nelas so traz a placa para a frente
            boolean hot = p.hot;
            if (!active) p.hot = false;
            drawPanel(g, p, guiScale, plateW, plateMaxH, () -> v.draw(canvas, p.w, p.h, time - 0.16F));
            p.hot = hot;
            HoloPanel chip = chips[s.ordinal()];
            drawPanel(g, chip, guiScale, chipW, chipH, () -> navView.drawChip(canvas, s, chip.w, chip.h, active));
        }
        if (modal.visible) drawPanel(g, modal, guiScale, modal.w, modal.h, () -> settings.draw(canvas, modal.w, modal.h));
    }

    private void drawPanel(GuiGraphics g, HoloPanel p, float guiScale, float surfaceW, float surfaceH, Runnable content) {
        if (!p.visible || p.alpha <= 0.003F) {
            p.resetZones();
            return;
        }
        p.surface.ensure(surfaceW, surfaceH, guiScale, uiScale);
        p.resetZones();
        GuiGraphics sg = p.surface.begin(g);
        try {
            canvas.bind(sg, p, this, time, dt);
            content.run();
        } finally {
            p.surface.end();
        }
    }

    // ================================================================ passada 3D

    private void draw3D() {
        float env = envelope();
        float motion = HoloConfig.motion();
        backDecor(env, motion);
        drawPlayer(env);

        drawOrder.clear();
        for (HoloPanel p : all) if (p.visible && p.alpha > 0.003F) drawOrder.add(p);
        drawOrder.sort(Comparator.comparingDouble(p -> p.depth));
        for (HoloPanel p : drawOrder) {
            float u = p.surface.u(p.w), v0 = p.surface.vBottom(p.h);
            r3d.texturedPanel(p.modelView, p.w, p.h, p.surface.textureId(), u, v0, p.alpha);
        }

        // contornos, varreduras, feixes, pedestal e mira
        r3d.beginLines();
        for (HoloPanel p : drawOrder) {
            float glow = 0.55F + 0.45F * motion;
            if (p.hot) glow += 0.35F;
            r3d.outline(p.modelView, p.w, p.h, p.outline * Math.min(1F, p.alpha * 1.5F), p.accent, glow * p.alpha);
            if (p.sweep >= 0F && p.sweep < 1F) {
                float y = p.h * p.sweep;
                int c = Ui.alpha(p.accent, 0.75F * (1F - p.sweep * 0.6F));
                r3d.segment(p.modelView, 0F, y, p.w, y, 2.2F, c, c);
                r3d.rect(p.modelView, 0F, Math.max(0F, y - 16F), p.w, Math.min(16F, y), Ui.alpha(p.accent, 0F), Ui.alpha(p.accent, 0.12F));
            }
        }
        lightTrails(env, false);
        assemblyBeams(env);
        pedestalLines();
        if (hotPanel != null && motion > 0F && !closing) {
            float x = hotPanel.cursorX, y = hotPanel.cursorY;
            int c = Ui.alpha(hotPanel.accent, 0.55F);
            r3d.segment(hotPanel.modelView, x - 5F, y, x - 2F, y, 0.8F, c, c);
            r3d.segment(hotPanel.modelView, x + 2F, y, x + 5F, y, 0.8F, c, c);
            r3d.segment(hotPanel.modelView, x, y - 5F, x, y - 2F, 0.8F, c, c);
            r3d.segment(hotPanel.modelView, x, y + 2F, x, y + 5F, 0.8F, c, c);
        }
        r3d.endLines();

        r3d.beginGlow();
        invocationGlow();
        for (HoloPanel p : drawOrder) {
            float a = p.alpha * (0.35F + 0.4F * motion);
            if (a <= 0.01F || p.w < 90F) continue;
            float pulse = 0.85F + 0.15F * Mth.sin(time * 2.4F + p.depth);
            glowCorner(p, 0F, 0F, a * pulse);
            glowCorner(p, p.w, 0F, a * 0.7F);
            glowCorner(p, 0F, p.h, a * 0.7F);
            glowCorner(p, p.w, p.h, a * pulse);
        }
        lightTrails(env, true);
        pedestalGlow(env);
        particles.render(r3d, viewLayout, uiScale, env);
        if (hotPanel != null && motion > 0F && !closing) {
            Vector3f c = hotPanel.toLayout(hotPanel.cursorX, hotPanel.cursorY, r3d.scratch());
            r3d.glowAt(viewLayout, c.x, c.y, c.z + 0.5F, 4.5F * uiScale, Ui.alpha(hotPanel.accent, 0.5F));
        }
        r3d.endGlow();
    }

    private void glowCorner(HoloPanel p, float lx, float ly, float a) {
        Vector3f v = p.toLayout(lx, ly, r3d.scratchB());
        r3d.glowAt(viewLayout, v.x, v.y, v.z + 0.3F, 7F * uiScale, Ui.alpha(p.accent, 0.5F * a));
    }

    /**
     * Rastros de luz ligando as regioes no espaco: fita -> placa ativa, placa ativa -> chip ativo e placa ativa ->
     * placas vizinhas. {@code pulses = false} desenha os fios (lote de linhas); {@code true} os pulsos que correm por
     * eles (lote de brilho).
     */
    private void lightTrails(float env, boolean pulses) {
        HoloPanel active = plates[section.ordinal()];
        if (!active.visible || closing || env < 0.05F) return;
        float k = env * HoloAnim.window(time, 0.45F, 0.3F);
        if (k <= 0.01F) return;
        float motion = HoloConfig.motion();
        int idx = 0;
        if (header.visible) trail(header, header.w / 2F, header.h, active, active.w / 2F, 0F, HoloStyle.CYAN, k, pulses, idx++, motion);
        HoloPanel chip = chips[section.ordinal()];
        if (chip.visible) trail(active, active.w / 2F, active.h, chip, chip.w / 2F, 0F, section.accent, k, pulses, idx++, motion);
        int i = section.ordinal();
        if (i > 0 && plates[i - 1].visible) trail(active, 0F, active.h * 0.35F, plates[i - 1], plates[i - 1].w, plates[i - 1].h * 0.35F,
                plates[i - 1].accent, k * 0.8F, pulses, idx++, motion);
        if (i < plates.length - 1 && plates[i + 1].visible) trail(active, active.w, active.h * 0.35F, plates[i + 1], 0F,
                plates[i + 1].h * 0.35F, plates[i + 1].accent, k * 0.8F, pulses, idx, motion);
    }

    private void trail(HoloPanel from, float fx, float fy, HoloPanel to, float tx, float ty, int color, float k, boolean pulses,
                       int index, float motion) {
        Vector3f a = from.toLayout(fx, fy, va);
        Vector3f b = to.toLayout(tx, ty, vb);
        if (!pulses) {
            Vector3f ea = viewLayout.transformPosition(r3d.scratchB().set(a));
            Vector3f eb = viewLayout.transformPosition(r3d.scratchC().set(b));
            r3d.beam(ea, eb, 0.9F * uiScale, Ui.alpha(color, 0.30F * k), Ui.alpha(color, 0.12F * k));
            return;
        }
        if (motion <= 0F) return;
        float f = (time * 0.7F + index * 0.27F) % 1F;
        float x = a.x + (b.x - a.x) * f, y = a.y + (b.y - a.y) * f, z = a.z + (b.z - a.z) * f;
        float fade = Mth.sin(f * Mth.PI);
        r3d.glowAt(viewLayout, x, y, z, 5F * uiScale, Ui.alpha(color, 0.8F * fade * k));
        r3d.glowAt(viewLayout, x, y, z, 1.6F * uiScale, Ui.alpha(0xFFFFFFFF, 0.9F * fade * k));
    }

    /** Anel e arcos de fundo, bem atras das placas, e grade de chao: profundidade e paralaxe do cenario. */
    private void backDecor(float env, float motion) {
        r3d.beginLines();
        Matrix4f m = scratchA.set(viewLayout).translate(centerX, layoutH * 0.5F, -360F);
        float spin = time * 0.12F * (0.3F + motion);
        float r = layoutH * 0.66F;
        int c1 = Ui.alpha(HoloStyle.CYAN, 0.15F * env), c2 = Ui.alpha(HoloStyle.VIOLET, 0.11F * env);
        float grow = HoloAnim.outCubic(HoloAnim.window(time, 0F, 0.5F));
        r3d.arc(m, 0F, 0F, r * grow, 1.6F, spin, spin + 4.2F, c1, 48);
        r3d.arc(m, 0F, 0F, r * 0.82F * grow, 1F, -spin * 1.4F + 1F, -spin * 1.4F + 3.2F, c2, 36);
        r3d.arc(m, 0F, 0F, r * 1.12F * grow, 0.8F, spin * 0.6F + 3F, spin * 0.6F + 5.6F, c1, 40);
        for (int i = 0; i < 24; i++) {
            float a = spin * 0.5F + i * Mth.TWO_PI / 24F;
            float cos = Mth.cos(a), sin = Mth.sin(a);
            r3d.segment(m, cos * r * 0.94F * grow, sin * r * 0.94F * grow, cos * r * 0.99F * grow, sin * r * 0.99F * grow, 1.2F,
                    Ui.alpha(HoloStyle.CYAN, 0.18F * env), Ui.alpha(HoloStyle.CYAN, 0.04F * env));
        }
        // anel de chao em volta do ponto de vista e grade sumindo na profundidade (perspectiva real)
        Matrix4f floor = scratchB.set(viewLayout).translate(centerX, layoutH + 10F, eyeZ).rotateX(-Mth.HALF_PI);
        r3d.arc(floor, 0F, 0F, eyeZ * 1.05F * grow, 1.2F, Mth.PI * 0.15F, Mth.PI * 0.85F, Ui.alpha(HoloStyle.CYAN, 0.16F * env), 48);
        r3d.arc(floor, 0F, 0F, eyeZ * 1.3F * grow, 0.9F, Mth.PI * 0.2F + spin * 0.2F, Mth.PI * 0.8F + spin * 0.2F,
                Ui.alpha(HoloStyle.VIOLET, 0.12F * env), 48);
        for (int i = -7; i <= 7; i++) {
            float a = Mth.HALF_PI + i * 0.11F;
            r3d.segment(floor, Mth.cos(a) * eyeZ * 0.7F, Mth.sin(a) * eyeZ * 0.7F, Mth.cos(a) * eyeZ * 1.9F, Mth.sin(a) * eyeZ * 1.9F,
                    0.9F, Ui.alpha(HoloStyle.CYAN, 0.10F * env), Ui.alpha(HoloStyle.CYAN, 0F));
        }
        r3d.endLines();
    }

    /** Feixes do ponto de invocacao ate cada placa durante a montagem. */
    private void assemblyBeams(float env) {
        if (HoloConfig.reducedMotion() || closing) return;
        float k = 1F - HoloAnim.window(time, 0.16F, 0.36F);
        if (k <= 0F) return;
        Vector3f core = r3d.scratchC();
        viewLayout.transformPosition(core.set(centerX, layoutH / 2F, 80F));
        for (HoloPanel p : all) {
            if (!p.visible || p == modal || p.w < 60F) continue;
            Vector3f target = p.toLayout(p.w * 0.5F, p.h * 0.5F, r3d.scratchB());
            viewLayout.transformPosition(target);
            float a = k * HoloAnim.window(time, 0.04F, 0.08F);
            r3d.beam(core, target, 1.4F, Ui.alpha(0xFFFFFFFF, 0.7F * a), Ui.alpha(p.accent, 0.35F * a));
        }
    }

    /** Fase A da abertura: ponto de luz que se forma antes das placas. */
    private void invocationGlow() {
        if (closing) return;
        float t = time;
        float k = HoloAnim.window(t, 0F, 0.09F) * (1F - HoloAnim.window(t, 0.2F, 0.3F));
        if (k <= 0F) return;
        float r = (HoloConfig.reducedMotion() ? 18F : 6F + 70F * HoloAnim.outCubic(HoloAnim.window(t, 0F, 0.3F))) * uiScale;
        r3d.glowAt(viewLayout, centerX, layoutH / 2F, 80F, r, Ui.alpha(HoloStyle.CYAN, 0.8F * k));
        r3d.glowAt(viewLayout, centerX, layoutH / 2F, 80F, r * 0.25F, Ui.alpha(0xFFFFFFFF, k));
    }

    // ================================================================ holograma do jogador

    private float playerScale(float env) {
        float vis = Math.max(dockAvailable ? 1F : 0F, HoloAnim.outCubic(stageAmt));
        float appear = HoloConfig.reducedMotion() ? HoloAnim.window(time, 0F, 0.15F)
                : HoloAnim.outBack(HoloAnim.window(time, 0.3F, 0.32F), 1.2F);
        float closeK = closing ? 1F - HoloAnim.inCubic(HoloAnim.window(time - closeStart, 0F, closeDuration())) : 1F;
        return vis * appear * closeK * (modalOpen ? 1F - 0.3F * modalAmt : 1F);
    }

    /**
     * Pes (layout) e altura do holograma: de pe na frente, a esquerda da placa ativa, ou no palco da placa Character
     * (preso a ela: acompanha o arco quando ela gira).
     */
    private float[] playerPlacement() {
        float dockH = Math.min(plateMaxH * 0.5F, 112F);
        float dockX = centerX - plateW / 2F - 44F, dockY = plateTop + plateMaxH - 8F, dockZ = 56F;
        HoloPanel ch = plates[Section.CHARACTER.ordinal()];
        float sx = 10F + CharacterView.stageWidth(ch.w) / 2F;
        Vector3f stage = ch.toLayout(sx, ch.h - 34F, va);
        float stageZ = stage.z + 30F;
        float stageH = Math.min(ch.h - 96F, 160F);
        if (!dockAvailable) {
            dockX = stage.x;
            dockY = stage.y;
            dockZ = stageZ;
            dockH = stageH;
        }
        float s = HoloAnim.inOutCubic(stageAmt);
        placement[0] = HoloAnim.lerp(s, dockX, stage.x);
        placement[1] = HoloAnim.lerp(s, dockY, stage.y);
        placement[2] = HoloAnim.lerp(s, dockZ, stageZ);
        placement[3] = HoloAnim.lerp(s, dockH, Math.max(40F, stageH));
        return placement;
    }

    private void drawPlayer(float env) {
        playerBoxValid = false;
        if (minecraft.player == null) return;
        float scale = playerScale(env);
        if (scale <= 0.02F) return;
        float[] at = playerPlacement();
        float height = at[3] * scale;
        float blockSize = height / 1.95F;
        if (!rotatingPlayer && HoloConfig.motion() > 0F && stageAmt < 0.5F) playerYaw += dt * 14F * HoloConfig.motion();
        // no palco o holograma vira de frente (3/4) ate o jogador arrastar
        if (section == Section.CHARACTER && !playerTouched && !rotatingPlayer) {
            float front = -22F + 360F * Math.round((playerYaw + 22F) / 360F);
            playerYaw = HoloConfig.reducedMotion() ? front : HoloAnim.approach(playerYaw, front, 6F, dt);
        }
        Vector3f feet = layout.transformPosition(r3d.scratch().set(at[0], at[1], at[2]));
        r3d.entity(minecraft.player, view, new Vector3f(feet), blockSize * uiScale, playerYaw);

        // caixa de arrasto: projecao dos pes e da cabeca (mesmas matrizes do desenho)
        Vector3f ndc = r3d.scratchB();
        if (HoloGeometry.project(projViewLayout, at[0], at[1], at[2], ndc)) {
            float fx = HoloGeometry.ndcToGuiX(ndc.x, screenW), fy = HoloGeometry.ndcToGuiY(ndc.y, screenH);
            if (HoloGeometry.project(projViewLayout, at[0], at[1] - height, at[2], ndc)) {
                float hy = HoloGeometry.ndcToGuiY(ndc.y, screenH);
                float half = (fy - hy) * 0.3F;
                playerBoxX0 = fx - half;
                playerBoxX1 = fx + half;
                playerBoxY0 = hy;
                playerBoxY1 = fy;
                playerBoxValid = scale > 0.5F && !closing;
            }
        }
    }

    private void pedestalLines() {
        float scale = playerScale(1F);
        if (scale <= 0.02F || minecraft.player == null) return;
        float[] at = playerPlacement();
        float height = at[3] * scale;
        float radius = height * 0.26F;
        Matrix4f floor = scratchA.set(viewLayout).translate(at[0], at[1], at[2]).rotateX(-Mth.HALF_PI);
        int c = Ui.alpha(HoloStyle.CYAN, 0.55F * scale);
        r3d.arc(floor, 0F, 0F, radius, 1.4F, 0F, Mth.TWO_PI, c, 40);
        r3d.arc(floor, 0F, 0F, radius * 1.35F, 0.8F, time * 0.8F, time * 0.8F + 4.4F, Ui.alpha(HoloStyle.VIOLET, 0.45F * scale), 32);
        r3d.arc(floor, 0F, 0F, radius * 0.6F, 0.6F, -time * 1.3F, -time * 1.3F + 2.2F, Ui.alpha(HoloStyle.CYAN_SOFT, 0.5F * scale), 20);
        if (HoloConfig.motion() > 0F) {
            // anel de varredura subindo pelo corpo
            float f = (time * 0.45F) % 1F;
            Matrix4f scanRing = scratchB.set(viewLayout).translate(at[0], at[1] - height * f, at[2]).rotateX(-Mth.HALF_PI);
            float a = Mth.sin(f * Mth.PI) * 0.35F * scale * HoloConfig.motion();
            r3d.arc(scanRing, 0F, 0F, radius * 0.75F, 0.7F, 0F, Mth.TWO_PI, Ui.alpha(HoloStyle.CYAN, a), 32);
        }
    }

    private void pedestalGlow(float env) {
        float scale = playerScale(env);
        if (scale <= 0.02F || minecraft.player == null) return;
        float[] at = playerPlacement();
        float height = at[3] * scale;
        r3d.glowAt(viewLayout, at[0], at[1], at[2], height * 0.32F * uiScale, Ui.alpha(HoloStyle.CYAN, 0.28F * scale));
        r3d.glowAt(viewLayout, at[0], at[1] - height * 0.5F, at[2] - 8F, height * 0.55F * uiScale, Ui.alpha(HoloStyle.CYAN, 0.07F * scale));
    }

    private void spawnAmbient() {
        float motion = HoloConfig.motion();
        if (motion <= 0F || closing || HoloConfig.highContrast()) return;
        moteClock += dt * 11F * motion;
        while (moteClock >= 1F) {
            moteClock -= 1F;
            int color = Math.random() < 0.7 ? HoloStyle.CYAN : HoloStyle.VIOLET;
            // espalhadas em volume, inclusive entre as placas e na frente delas
            particles.mote(centerX - layoutW * 0.7F, centerX + layoutW * 0.7F, layoutH * 0.2F, layoutH + 10F, -300F, 140F, color);
        }
        if (playerScale(1F) > 0.5F && Math.random() < dt * 6F * motion) {
            float[] at = playerPlacement();
            particles.ember(at[0] + ((float) Math.random() - 0.5F) * at[3] * 0.4F, at[1] - (float) Math.random() * 4F, at[2],
                    HoloStyle.CYAN, 8F);
        }
    }

    // ================================================================ entrada

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (closing || time < 0.06F) return true;
        float nx = ndcX(), ny = ndcY();
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT || button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            if (!modalOpen && !HoloConfig.reducedMotion()) {
                orbiting = true;
                dragButton = -1;
            }
            return true;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;
        if (!modalOpen && playerHit(nx, ny)) {
            rotatingPlayer = true;
            return true;
        }
        HoloPanel p = pick(nx, ny, hit);
        if (p == null) {
            if (modalOpen) closeSettings();
            return true;
        }
        // placa de lado: o clique traz a secao para a frente (navegacao espacial)
        if (isPlate(p) && sectionOf(p) != section) {
            select(sectionOf(p));
            return true;
        }
        HoloZone z = p.zoneAt(hit.x, hit.y);
        if (z == null) return true;
        if (z.drag != null && z.enabled) {
            dragPanel = p;
            dragZone = z;
            dragButton = button;
            dragStartX = hit.x;
            dragStartY = hit.y;
            dragMoved = false;
            z.drag.press(hit.x, hit.y);
            return true;
        }
        if (z.click != null && z.enabled) {
            Ui.playClick(z.pitch);
            if (HoloConfig.motion() > 0F) {
                Vector3f at = p.toLayout(hit.x, hit.y, new Vector3f());
                particles.burst(at.x, at.y, at.z + 1F, 6, p.accent, 40F);
            }
            z.click.run();
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (closing) return true;
        if (orbiting) {
            orbitYaw = Mth.clamp(orbitYaw + (float) dragX * 0.35F, -26F, 26F);
            orbitPitch = Mth.clamp(orbitPitch + (float) dragY * 0.3F, -16F, 16F);
            orbitYawVel[0] = orbitPitchVel[0] = 0F;
            return true;
        }
        if (rotatingPlayer) {
            playerTouched = true;
            playerYaw += (float) dragX * 1.6F;
            return true;
        }
        if (dragZone != null && dragPanel != null) {
            Vector3f v = new Vector3f();
            if (HoloGeometry.intersect(dragPanel.inverse, ndcX(), ndcY(), v)) {
                if (Math.abs(v.x - dragStartX) + Math.abs(v.y - dragStartY) > 3F) dragMoved = true;
                dragZone.drag.move(v.x, v.y);
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
        if (rotatingPlayer && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            rotatingPlayer = false;
            return true;
        }
        if (dragZone != null && button == dragButton) {
            Vector3f v = new Vector3f(dragStartX, dragStartY, 0F);
            if (dragPanel != null) HoloGeometry.intersect(dragPanel.inverse, ndcX(), ndcY(), v);
            HoloZone z = dragZone;
            dragZone = null;
            dragPanel = null;
            dragButton = -1;
            z.drag.release(v.x, v.y, !dragMoved);
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (closing || modalOpen) return true;
        HoloPanel p = pick(ndcX(), ndcY(), hit);
        // sobre a placa da frente rola o conteudo; em qualquer outro lugar gira o arco
        boolean scrolled = p == plates[section.ordinal()] && view(section).scrollBy(scrollY);
        if (!scrolled && scrollY != 0) cycle(scrollY > 0 ? -1 : 1);
        // sempre consumido: com o menu aberto a roda nunca chega na hotbar
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (modalOpen) closeSettings();
            else onClose();
            return true;
        }
        if (CharacterCreatorKeybinds.isMenuKey(keyCode, scanCode)) {
            if (modalOpen) closeSettings();
            onClose();
            return true;
        }
        if (closing) return true;
        if (modalOpen) return true;
        switch (keyCode) {
            case GLFW.GLFW_KEY_TAB -> cycle((modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? -1 : 1);
            case GLFW.GLFW_KEY_LEFT -> cycle(-1);
            case GLFW.GLFW_KEY_RIGHT -> cycle(1);
            case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_PAGE_UP -> view(section).scrollBy(keyCode == GLFW.GLFW_KEY_UP ? 1 : 4);
            case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_PAGE_DOWN -> view(section).scrollBy(keyCode == GLFW.GLFW_KEY_DOWN ? -1 : -4);
            case GLFW.GLFW_KEY_LEFT_BRACKET -> view(section).arrow(-1);
            case GLFW.GLFW_KEY_RIGHT_BRACKET -> view(section).arrow(1);
            case GLFW.GLFW_KEY_R -> recenter();
            default -> {
                if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_5) {
                    select(Section.values()[keyCode - GLFW.GLFW_KEY_1]);
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

    /** Toca o som de hover quando o cursor entra num chip novo. */
    void hoverSound(String key, boolean hot) {
        if (hot && !key.equals(lastHoverKey)) {
            lastHoverKey = key;
            Ui.playUi(ModSounds.UI_HOVER.get(), 1.4F, 0.18F);
        } else if (!hot && key.equals(lastHoverKey)) {
            lastHoverKey = null;
        }
    }

    // ================================================================ utilidades para as secoes

    /** Faiscas que nascem de um ponto local da placa da frente (ex.: no de habilidade comprado). */
    void burstOnMain(float lx, float ly, int color, int count, float speed) {
        if (HoloConfig.motion() <= 0F) return;
        Vector3f at = plates[section.ordinal()].toLayout(lx, ly, new Vector3f());
        particles.burst(at.x, at.y, at.z + 2F, count, color, speed);
    }

    HoloPanel mainPanel() { return plates[section.ordinal()]; }

    float playerYaw() { return playerYaw; }
}
