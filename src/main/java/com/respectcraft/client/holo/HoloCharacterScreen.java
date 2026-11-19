package com.respectcraft.client.holo;

import com.respectcraft.client.character.CharacterCreationScreen;
import com.respectcraft.client.character.CharacterCreatorKeybinds;
import com.respectcraft.client.roulette.BirthRouletteScreen;
import com.respectcraft.client.ui.RespectScreen;
import com.respectcraft.client.ui.Ui;
import com.respectcraft.network.ProfileClientCache;
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
 * Menu do personagem da tecla K (Etapa 4.8.1): interface holografica com paineis projetados em perspectiva real.
 *
 * <p><b>Arquitetura.</b> Cada painel ({@link HoloPanel}) desenha o conteudo numa superficie propria
 * ({@link HoloSurface}, um framebuffer reaproveitado) com {@code GuiGraphics}; depois a passada {@link Holo3D}
 * projeta as superficies como quads 3D com a projecao fora do eixo de {@link HoloGeometry}, junto com o proprio
 * jogador (rig GeckoLib), contornos, feixes e particulas. O clique faz o caminho inverso com as mesmas matrizes do
 * quadro: raio do cursor -> plano do painel -> coordenada local -> regiao registrada durante o desenho.
 *
 * <p><b>Dados.</b> So exibe as caches do cliente ({@link ProfileClientCache}, aparencia); compras de habilidade vao
 * como pedido ao servidor, que continua dono de tudo. Nenhuma transicao visual muda dados de jogo.
 *
 * <p><b>Ciclo de vida.</b> K ou Esc tocam o fechamento (~0,25 s) e devolvem o controle ao jogo; qualquer outra
 * troca de tela (morte, troca de dimensao, desconexao, outra tela) chama {@link #removed()}, que libera as
 * superficies, o efeito do Rimulib, os arrastos e as particulas.
 */
public final class HoloCharacterScreen extends RespectScreen {
    /** Secoes do menu, na ordem da barra lateral. */
    public enum Section {
        OVERVIEW("overview", "carteira_trabalho", HoloStyle.CYAN, false),
        CHARACTER("character", "identidade_dinheiro", HoloStyle.CYAN_SOFT, true),
        BODY("body", "penico", HoloStyle.GREEN, false),
        SPECIALITY("speciality", "moeda_real", HoloStyle.VIOLET, false),
        SKILLS("skills", "niobio", HoloStyle.GOLD, true);

        final String id;
        final ResourceLocation icon;
        final int accent;
        /** Usa o painel largo (sem a coluna do holograma do jogador). */
        final boolean wide;

        Section(String id, String icon, int accent, boolean wide) {
            this.id = id;
            this.icon = SkillDef.iconTexture(icon);
            this.accent = accent;
            this.wide = wide;
        }

        String key() { return "gui.respectcraft.holo.section." + id; }
    }

    /** Campo de visao vertical da camera virtual do menu (independente do FOV do jogo, para a leitura nao mudar). */
    static final float FOV = 42F;
    /** Ultima altura de conteudo de cada secao (cache so visual): o painel ja abre no tamanho certo. */
    private static final float[] FIT_CACHE = new float[Section.values().length];
    private static final float CLOSE_TIME = 0.26F;

    // ---------------------------------------------------------------- estado
    private Section section;
    private Section previous;
    private float sectionStart = -10F;
    private boolean closing;
    private float closeStart;
    private boolean modalOpen;
    private float modalAmt;

    private long openNanos;
    private float time, dt;
    private boolean opened;

    // layout (unidades de layout)
    private float uiScale = 1F, screenW, screenH, layoutW, layoutH;
    final Rect navRect = new Rect(), narrow = new Rect(), wide = new Rect(), plateRect = new Rect(), modalRect = new Rect();
    private boolean dockAvailable;

    // paineis
    final HoloPanel nav = new HoloPanel("nav", true);
    final HoloPanel plate = new HoloPanel("plate", true);
    final HoloPanel modal = new HoloPanel("modal", true);
    private HoloPanel incoming = new HoloPanel("main_a", true);
    private HoloPanel outgoing = new HoloPanel("main_b", true);
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
    private float stageAmt, dockAmt;
    private boolean rotatingPlayer;
    private boolean playerTouched;
    private float playerBoxX0, playerBoxY0, playerBoxX1, playerBoxY1;
    private boolean playerBoxValid;

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
        this.views = new HoloView[]{new OverviewView(this), new CharacterView(this), new BodyView(this),
                new SpecialityView(this), new SkillsView(this)};
    }

    // ================================================================ layout

    @Override
    protected void init() {
        var window = minecraft.getWindow();
        uiScale = HoloConfig.scale();
        screenW = (float) (window.getWidth() / window.getGuiScale());
        screenH = (float) (window.getHeight() / window.getGuiScale());
        layoutW = screenW / uiScale;
        layoutH = screenH / uiScale;
        // O menu ocupa no maximo 660 x 370 unidades, centralizado: em telas grandes (GUI 1) continua um
        // conjunto compacto com o mundo em volta; na menor GUI (320 x 240) usa a tela toda.
        float effW = Math.min(layoutW, 660F), effH = Math.min(layoutH, 370F);
        float ox = (layoutW - effW) / 2F, oy = (layoutH - effH) / 2F;
        float m = 8F, gap = 8F;
        float navW = effW < 360 ? 90F : 108F;
        navRect.set(ox + m + 6, oy + m, navW, effH - 2 * m);
        float mainX = navRect.x + navW + gap;
        float right = ox + effW - m - 6;
        float dockW = effW >= 400 ? Mth.clamp(effW * 0.24F, 96F, 150F) : 0F;
        dockAvailable = dockW > 0 && effH >= 200;
        wide.set(mainX, oy + m, right - mainX, effH - 2 * m);
        if (dockAvailable) {
            narrow.set(mainX, oy + m, wide.w - dockW - gap, wide.h);
            plateRect.set(right - dockW + 4, oy + effH - m - 66, dockW - 8, 62);
        } else {
            narrow.set(wide.x, wide.y, wide.w, wide.h);
            plateRect.set(0, 0, 0, 0);
        }
        float mw = Math.min(236F, effW - 40F), mh = Math.min(196F, effH - 24F);
        modalRect.set((layoutW - mw) / 2F, (layoutH - mh) / 2F, mw, mh);
        nav.rect(navRect.x, navRect.y, navRect.w, navRect.h);
        plate.rect(plateRect.x, plateRect.y, plateRect.w, plateRect.h);
        modal.rect(modalRect.x, modalRect.y, modalRect.w, modalRect.h);
        Rect r = rectFor(section);
        incoming.rect(r.x, r.y, r.w, r.h);
        incoming.h = 0F; // fitHeight escolhe a altura no proximo quadro, sem animar
    }

    Rect rectFor(Section s) { return s.wide ? wide : narrow; }

    // ================================================================ secoes

    Section section() { return section; }
    HoloView view(Section s) { return views[s.ordinal()]; }
    float time() { return time; }
    float sectionSince() { return time - sectionStart; }
    boolean dockAvailable() { return dockAvailable; }

    void select(Section next) {
        if (next == null || next == section || closing) return;
        // painel que entra vira o que sai (troca de superficies, sem realocar)
        HoloPanel swap = outgoing;
        outgoing = incoming;
        incoming = swap;
        previous = section;
        section = next;
        sectionStart = time;
        Rect r = rectFor(next);
        incoming.rect(r.x, r.y, r.w, r.h);
        incoming.h = 0F;
        incoming.resetZones();
        view(next).onEnter();
        Ui.playClick(1.15F);
        if (HoloConfig.motion() > 0) particles.alongEdges(incoming, 14, next.accent, 30F);
    }

    void cycle(int dir) {
        Section[] all = Section.values();
        select(all[Math.floorMod(section.ordinal() + dir, all.length)]);
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

    /** Esc, K ou "Back to game": toca a dissolucao e so entao devolve o jogo. */
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
            particles.alongEdges(incoming, 26, view(section).accent(), 46F);
            particles.alongEdges(nav, 18, HoloStyle.CYAN, 40F);
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
        nav.close();
        plate.close();
        modal.close();
        incoming.close();
        outgoing.close();
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
        freezePanels();
        pickHot();
        tooltip = null;
        drawSurfaces(g);

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

    /** Escurecimento leve do mundo com vinheta azul (alem do efeito do Rimulib), sem desfoque. */
    private void backdrop2D(GuiGraphics g, float k) {
        int top = Ui.alpha(0xFF020812, 0.50F * k), mid = Ui.alpha(0xFF04101C, 0.30F * k), bottom = Ui.alpha(0xFF02060C, 0.62F * k);
        g.fillGradient(0, 0, width, height / 2, top, mid);
        g.fillGradient(0, height / 2, width, height, mid, bottom);
    }

    private void updateCamera() {
        var window = minecraft.getWindow();
        float ndcX = (float) (minecraft.mouseHandler.xpos() / Math.max(1, window.getScreenWidth()) * 2.0 - 1.0);
        float ndcY = (float) (1.0 - minecraft.mouseHandler.ypos() / Math.max(1, window.getScreenHeight()) * 2.0);
        ndcX = Mth.clamp(ndcX, -1F, 1F);
        ndcY = Mth.clamp(ndcY, -1F, 1F);
        float motion = HoloConfig.motion();
        float weight = motion * HoloAnim.outCubic(HoloAnim.window(time, 0.42F, 0.45F));
        eyeX = HoloAnim.approach(eyeX, ndcX * 38F * weight, 5F, dt);
        eyeY = HoloAnim.approach(eyeY, ndcY * 22F * weight, 5F, dt);

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
        HoloGeometry.offAxis(projection, screenW, screenH, distance, eyeX, eyeY, 8F, distance * 6F);
        HoloGeometry.view(view, distance, eyeX, eyeY, orbitYaw + worldYaw, orbitPitch + worldPitch);
        HoloGeometry.layoutToMenu(layout, screenW, screenH, uiScale);
        view.mul(layout, viewLayout);
        projection.mul(viewLayout, projViewLayout);
    }

    /** Estado animado de cada painel neste quadro: abertura, troca de secao, painel Display e fechamento. */
    private void animate() {
        boolean reduced = HoloConfig.reducedMotion();
        modalAmt = HoloAnim.approach(modalAmt, modalOpen ? 1F : 0F, reduced ? 40F : 14F, dt);
        boolean docked = dockAvailable && !section.wide;
        dockAmt = HoloAnim.approach(dockAmt, docked ? 1F : 0F, reduced ? 40F : 9F, dt);
        stageAmt = HoloAnim.approach(stageAmt, section == Section.CHARACTER ? 1F : 0F, reduced ? 40F : 8F, dt);

        // barra lateral
        // barra lateral e placa giram pela borda de fora: a borda de dentro recua (cabine), nada sai da tela
        nav.rest();
        nav.pivotX = 0F;
        nav.yaw = 16F;
        nav.dz = 10F;
        nav.accent = HoloStyle.CYAN;
        assemble(nav, 0.05F, 0.03F);

        // painel principal (entra) e o anterior (sai); secoes estreitas encolhem ate o conteudo
        float since = time - sectionStart;
        fitHeight(incoming, section, reduced);
        incoming.rest();
        incoming.accent = view(section).accent();
        assemble(incoming, 0.11F, 0F);
        incoming.sweep = -1F;
        if (previous != null && since < 0.45F) {
            if (reduced) {
                incoming.alpha *= HoloAnim.window(since, 0F, 0.12F);
                incoming.contentAlpha *= HoloAnim.window(since, 0F, 0.12F);
            } else {
                float k = HoloAnim.outCubic(HoloAnim.window(since, 0F, 0.30F));
                incoming.dz += HoloAnim.lerp(k, 70F, 0F);
                incoming.scaleX *= HoloAnim.lerp(k, 0.96F, 1F);
                incoming.alpha *= HoloAnim.window(since, 0F, 0.16F);
                incoming.contentAlpha *= HoloAnim.window(since, 0.05F, 0.2F);
                incoming.outline *= HoloAnim.window(since, 0F, 0.3F);
                incoming.sweep = HoloAnim.window(since, 0.02F, 0.34F);
                if (incoming.sweep >= 1F) incoming.sweep = -1F;
            }
            outgoing.rest();
            outgoing.accent = view(previous).accent();
            float k = reduced ? HoloAnim.window(since, 0F, 0.12F) : HoloAnim.inCubic(HoloAnim.window(since, 0F, 0.26F));
            if (!reduced) {
                outgoing.dz = HoloAnim.lerp(k, 0F, -130F);
                outgoing.yaw = -7F * k;
                outgoing.scaleX = outgoing.scaleY = HoloAnim.lerp(k, 1F, 0.94F);
            }
            outgoing.alpha = 1F - k;
            outgoing.contentAlpha = 1F - Math.min(1F, k * 1.6F);
            outgoing.outline = 1F - k;
            outgoing.visible = k < 1F;
            outgoing.interactive = false;
            if (closing) closeOut(outgoing, 0F);
        } else {
            outgoing.visible = false;
        }

        // placa de identidade sob o holograma
        plate.rest();
        plate.pivotX = 1F;
        plate.pivotY = 1F;
        plate.yaw = -16F;
        plate.pitch = 14F;
        plate.dz = 14F;
        plate.accent = HoloStyle.VIOLET;
        assemble(plate, 0.2F, 0.06F);
        float d = HoloAnim.outCubic(dockAmt);
        plate.alpha *= d;
        plate.contentAlpha *= d;
        plate.outline *= d;
        plate.dz -= (1F - d) * 90F;
        plate.scaleY *= HoloAnim.lerp(d, 0.3F, 1F);
        plate.visible = dockAvailable && plate.alpha > 0.01F;
        plate.interactive = plate.visible && d > 0.6F;

        // painel Display (modal): na frente, empurra o resto para tras
        modal.rest();
        modal.accent = HoloStyle.VIOLET;
        float mk = HoloAnim.outCubic(modalAmt);
        modal.dz = HoloAnim.lerp(mk, -40F, 34F);
        modal.scaleX = modal.scaleY = HoloAnim.lerp(mk, 0.9F, 1F);
        modal.alpha = mk;
        modal.contentAlpha = HoloAnim.window(modalAmt, 0.4F, 0.6F);
        modal.outline = mk;
        modal.visible = modalAmt > 0.01F;
        modal.interactive = modalOpen && modalAmt > 0.5F;
        if (modal.visible) {
            for (HoloPanel p : new HoloPanel[]{nav, incoming, plate}) {
                p.dz -= 28F * mk;
                p.alpha *= 1F - 0.45F * mk;
                p.contentAlpha *= 1F - 0.55F * mk;
                p.interactive = false;
            }
        }
        if (closing) closeOut(modal, 0F);
    }

    /** Altura do painel principal: o retangulo inteiro, ou so o necessario para o conteudo (ultima medida). */
    private void fitHeight(HoloPanel p, Section s, boolean reduced) {
        Rect r = rectFor(s);
        HoloView v = view(s);
        float target = r.h;
        if (v.fitsContent()) {
            float desired = v.desiredHeight();
            if (desired <= 0F) desired = FIT_CACHE[s.ordinal()];
            if (desired > 0F) target = Mth.clamp(desired, Math.min(r.h, 120F), r.h);
            FIT_CACHE[s.ordinal()] = desired;
        }
        p.y = r.y;
        p.h = reduced || p.h <= 1F || Math.abs(p.h - target) < 0.5F ? target : HoloAnim.approach(p.h, target, 12F, dt);
    }

    /** Abertura de um painel: risco horizontal que desdobra, vindo do fundo, com contorno desenhado. */
    private void assemble(HoloPanel p, float delay, float closeDelay) {
        p.visible = true;
        p.interactive = true;
        if (HoloConfig.reducedMotion()) {
            float k = HoloAnim.window(time, 0F, 0.15F);
            p.alpha = k;
            p.contentAlpha = k;
            p.outline = k;
        } else {
            float form = HoloAnim.outCubic(HoloAnim.window(time, delay, 0.24F));
            float unfold = HoloAnim.outBack(HoloAnim.window(time, delay + 0.07F, 0.22F), 1.05F);
            p.scaleX *= HoloAnim.lerp(form, 0.55F, 1F);
            p.scaleY *= HoloAnim.lerp(unfold, 0.025F, 1F);
            p.dz += HoloAnim.lerp(form, -170F, 0F);
            p.alpha = HoloAnim.window(time, delay + 0.03F, 0.18F);
            p.outline = HoloAnim.outCubic(HoloAnim.window(time, delay, 0.28F));
            p.contentAlpha = HoloAnim.window(time, delay + 0.17F, 0.2F);
        }
        p.interactive = p.alpha > 0.35F;
        if (closing) closeOut(p, closeDelay);
    }

    /** Dissolucao: conteudo some primeiro, o painel recolhe numa linha e recua. */
    private void closeOut(HoloPanel p, float delay) {
        float tc = time - closeStart;
        p.interactive = false;
        if (HoloConfig.reducedMotion()) {
            float k = HoloAnim.window(tc, 0F, closeDuration());
            p.alpha *= 1F - k;
            p.contentAlpha *= 1F - k;
            p.outline *= 1F - k;
            return;
        }
        float k = HoloAnim.inCubic(HoloAnim.window(tc, delay + 0.05F, 0.18F));
        p.contentAlpha *= 1F - HoloAnim.window(tc, 0F, 0.09F);
        p.scaleY *= HoloAnim.lerp(k, 1F, 0.02F);
        p.dz += HoloAnim.lerp(k, 0F, -90F);
        p.alpha *= 1F - HoloAnim.window(tc, delay + 0.12F, 0.12F);
        p.outline *= 1F - k;
    }

    private void freezePanels() {
        for (HoloPanel p : panels()) if (p.visible) p.freeze(viewLayout, projViewLayout);
    }

    private HoloPanel[] panels() { return new HoloPanel[]{nav, outgoing, incoming, plate, modal}; }

    // ================================================================ cursor

    private float ndcX() {
        return (float) (minecraft.mouseHandler.xpos() / Math.max(1, minecraft.getWindow().getScreenWidth()) * 2.0 - 1.0);
    }

    private float ndcY() {
        return (float) (1.0 - minecraft.mouseHandler.ypos() / Math.max(1, minecraft.getWindow().getScreenHeight()) * 2.0);
    }

    /** Painel interativo mais perto sob o cursor (o modal, quando aberto, captura tudo). */
    private HoloPanel pick(float nx, float ny, Vector3f out) {
        if (closing) return null;
        if (modalOpen) return modal.interactive && modal.hit(nx, ny, out) ? modal : null;
        HoloPanel best = null;
        float bestT = Float.MAX_VALUE, bx = 0, by = 0;
        for (HoloPanel p : new HoloPanel[]{nav, incoming, plate}) {
            if (!p.visible || !p.interactive) continue;
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
        for (HoloPanel p : panels()) {
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
        HoloView current = view(section);
        // As duas superficies do painel principal tem o tamanho do retangulo largo: trocar de secao nao realoca.
        drawPanel(g, nav, guiScale, nav.w, nav.h, () -> navView.drawNav(canvas, nav.w, nav.h));
        if (outgoing.visible && previous != null) {
            HoloView old = view(previous);
            drawPanel(g, outgoing, guiScale, wide.w, wide.h, () -> old.draw(canvas, outgoing.w, outgoing.h, 10F));
        }
        drawPanel(g, incoming, guiScale, wide.w, wide.h, () -> current.draw(canvas, incoming.w, incoming.h, time - Math.max(sectionStart, 0.16F)));
        if (plate.visible) drawPanel(g, plate, guiScale, plate.w, plate.h, () -> navView.drawPlate(canvas, plate.w, plate.h));
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
        for (HoloPanel p : panels()) if (p.visible && p.alpha > 0.003F) drawOrder.add(p);
        drawOrder.sort(Comparator.comparingDouble(p -> p.depth));
        for (HoloPanel p : drawOrder) {
            float u = p.surface.u(p.w), v0 = p.surface.vBottom(p.h);
            r3d.texturedPanel(p.modelView, p.w, p.h, p.surface.textureId(), u, v0, p.alpha);
        }

        // contornos, varreduras, feixes de montagem, pedestal e mira
        r3d.beginLines();
        for (HoloPanel p : drawOrder) {
            float glow = 0.55F + 0.45F * motion;
            if (p.hot) glow += 0.25F;
            r3d.outline(p.modelView, p.w, p.h, p.outline * Math.min(1F, p.alpha * 1.5F), p.accent, glow * p.alpha);
            if (p.sweep >= 0F && p.sweep < 1F) {
                float y = p.h * p.sweep;
                int c = Ui.alpha(p.accent, 0.75F * (1F - p.sweep * 0.6F));
                r3d.segment(p.modelView, 0F, y, p.w, y, 2.2F, c, c);
                r3d.rect(p.modelView, 0F, Math.max(0F, y - 16F), p.w, Math.min(16F, y), Ui.alpha(p.accent, 0F), Ui.alpha(p.accent, 0.12F));
            }
        }
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
            if (a <= 0.01F) continue;
            float pulse = 0.85F + 0.15F * Mth.sin(time * 2.4F + p.depth);
            glowCorner(p, 0F, 0F, a * pulse);
            glowCorner(p, p.w, 0F, a * 0.7F);
            glowCorner(p, 0F, p.h, a * 0.7F);
            glowCorner(p, p.w, p.h, a * pulse);
        }
        pedestalGlow(env);
        particles.render(r3d, viewLayout, uiScale, env);
        if (hotPanel != null && motion > 0F && !closing) {
            hotPanel.toLayout(hotPanel.cursorX, hotPanel.cursorY, r3d.scratch());
            Vector3f c = r3d.scratch();
            r3d.glowAt(viewLayout, c.x, c.y, c.z + 0.5F, 4.5F * uiScale, Ui.alpha(hotPanel.accent, 0.5F));
        }
        r3d.endGlow();
    }

    private void glowCorner(HoloPanel p, float lx, float ly, float a) {
        Vector3f v = p.toLayout(lx, ly, r3d.scratchB());
        r3d.glowAt(viewLayout, v.x, v.y, v.z + 0.3F, 7F * uiScale, Ui.alpha(p.accent, 0.5F * a));
    }

    /** Anel e arcos de fundo, bem atras dos paineis: dao a profundidade e a paralaxe do cenario. */
    private void backDecor(float env, float motion) {
        r3d.beginLines();
        Matrix4f m = scratchA.set(viewLayout).translate(layoutW * 0.52F, layoutH * 0.5F, -320F);
        float spin = time * 0.12F * (0.3F + motion);
        float r = layoutH * 0.62F;
        int c1 = Ui.alpha(HoloStyle.CYAN, 0.16F * env), c2 = Ui.alpha(HoloStyle.VIOLET, 0.12F * env);
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
        // chao de grade sumindo na profundidade (perspectiva real)
        Matrix4f floor = scratchB.set(viewLayout).translate(0F, layoutH + 6F, 0F).rotateX(-Mth.HALF_PI);
        for (int i = -6; i <= 14; i++) {
            float x = layoutW * 0.5F + i * 48F - 4 * 48F;
            r3d.segment(floor, x, -40F, x, 520F, 1F, Ui.alpha(HoloStyle.CYAN, 0.10F * env), Ui.alpha(HoloStyle.CYAN, 0F));
        }
        for (int i = 0; i < 9; i++) {
            float z = -40F + i * 64F;
            float a = 0.10F * (1F - i / 9F) * env;
            r3d.segment(floor, -layoutW * 0.5F, z, layoutW * 1.5F, z, 1F, Ui.alpha(HoloStyle.CYAN, a), Ui.alpha(HoloStyle.CYAN, a));
        }
        r3d.endLines();
    }

    /** Feixes do ponto de invocacao ate cada painel durante a montagem. */
    private void assemblyBeams(float env) {
        if (HoloConfig.reducedMotion() || closing) return;
        float k = 1F - HoloAnim.window(time, 0.12F, 0.32F);
        if (k <= 0F) return;
        Vector3f core = r3d.scratchC();
        viewLayout.transformPosition(core.set(layoutW / 2F, layoutH / 2F, 60F));
        for (HoloPanel p : new HoloPanel[]{nav, incoming, plate}) {
            if (!p.visible) continue;
            Vector3f target = p.toLayout(p.w * p.pivotX, p.h * 0.5F, r3d.scratchB());
            viewLayout.transformPosition(target);
            float a = k * HoloAnim.window(time, 0.04F, 0.08F);
            r3d.beam(core, target, 1.4F, Ui.alpha(0xFFFFFFFF, 0.7F * a), Ui.alpha(p.accent, 0.35F * a));
        }
    }

    /** Fase A da abertura: ponto de luz e anel que se forma antes dos paineis. */
    private void invocationGlow() {
        if (closing) return;
        float t = time;
        float k = HoloAnim.window(t, 0F, 0.09F) * (1F - HoloAnim.window(t, 0.18F, 0.3F));
        if (k <= 0F) return;
        float r = (HoloConfig.reducedMotion() ? 18F : 6F + 70F * HoloAnim.outCubic(HoloAnim.window(t, 0F, 0.3F))) * uiScale;
        r3d.glowAt(viewLayout, layoutW / 2F, layoutH / 2F, 60F, r, Ui.alpha(HoloStyle.CYAN, 0.8F * k));
        r3d.glowAt(viewLayout, layoutW / 2F, layoutH / 2F, 60F, r * 0.25F, Ui.alpha(0xFFFFFFFF, k));
    }

    // ================================================================ holograma do jogador

    private float playerScale(float env) {
        float vis = Math.max(dockAvailable ? HoloAnim.outCubic(dockAmt) : 0F, HoloAnim.outCubic(stageAmt));
        float appear = HoloConfig.reducedMotion() ? HoloAnim.window(time, 0F, 0.15F)
                : HoloAnim.outBack(HoloAnim.window(time, 0.22F, 0.3F), 1.2F);
        float closeK = closing ? 1F - HoloAnim.inCubic(HoloAnim.window(time - closeStart, 0F, closeDuration())) : 1F;
        return vis * appear * closeK * (modalOpen ? 1F - 0.3F * modalAmt : 1F);
    }

    /** Pes (layout) e altura do holograma: encaixado sobre a placa ou no palco da secao Character. */
    private float[] playerPlacement() {
        float s = HoloAnim.inOutCubic(stageAmt);
        float dockX = plateRect.x + plateRect.w / 2F, dockY = plateRect.y - 6F, dockZ = 36F;
        float dockH = Math.min(plateRect.y - 22F, 122F);
        Rect r = wide;
        float stageX = r.x + 10F + CharacterView.stageWidth(r.w) / 2F;
        float stageY = r.y + r.h - 34F;
        float stageZ = 30F;
        float stageH = Math.min(r.h - 96F, 160F);
        if (!dockAvailable) {
            dockX = stageX;
            dockY = stageY;
            dockZ = stageZ;
            dockH = stageH;
        }
        return new float[]{HoloAnim.lerp(s, dockX, stageX), HoloAnim.lerp(s, dockY, stageY), HoloAnim.lerp(s, dockZ, stageZ),
                HoloAnim.lerp(s, dockH, stageH)};
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
        moteClock += dt * 9F * motion;
        while (moteClock >= 1F) {
            moteClock -= 1F;
            int color = Math.random() < 0.7 ? HoloStyle.CYAN : HoloStyle.VIOLET;
            particles.mote(-20F, layoutW + 20F, layoutH * 0.25F, layoutH + 10F, -260F, 70F, color);
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
        if (p == nav) {
            if (scrollY != 0) cycle(scrollY > 0 ? -1 : 1);
        } else if (p == incoming) {
            view(section).scrollBy(scrollY);
        }
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
            case GLFW.GLFW_KEY_UP -> cycle(-1);
            case GLFW.GLFW_KEY_DOWN -> cycle(1);
            case GLFW.GLFW_KEY_PAGE_UP -> view(section).scrollBy(4);
            case GLFW.GLFW_KEY_PAGE_DOWN -> view(section).scrollBy(-4);
            case GLFW.GLFW_KEY_LEFT -> view(section).arrow(-1);
            case GLFW.GLFW_KEY_RIGHT -> view(section).arrow(1);
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

    /** Toca o som de hover quando o cursor entra num item novo da barra lateral. */
    void hoverSound(String key, boolean hot) {
        if (hot && !key.equals(lastHoverKey)) {
            lastHoverKey = key;
            Ui.playUi(ModSounds.UI_HOVER.get(), 1.4F, 0.18F);
        } else if (!hot && key.equals(lastHoverKey)) {
            lastHoverKey = null;
        }
    }

    // ================================================================ utilidades para as secoes

    /** Faiscas que nascem de um ponto local do painel principal (ex.: no de habilidade comprado). */
    void burstOnMain(float lx, float ly, int color, int count, float speed) {
        if (HoloConfig.motion() <= 0F) return;
        Vector3f at = incoming.toLayout(lx, ly, new Vector3f());
        particles.burst(at.x, at.y, at.z + 2F, count, color, speed);
    }

    HoloPanel mainPanel() { return incoming; }

    float playerYaw() { return playerYaw; }

    /** Retangulo simples em unidades de layout. */
    static final class Rect {
        float x, y, w, h;

        void set(float x, float y, float w, float h) {
            this.x = x;
            this.y = y;
            this.w = Math.max(1F, w);
            this.h = Math.max(1F, h);
        }
    }
}
