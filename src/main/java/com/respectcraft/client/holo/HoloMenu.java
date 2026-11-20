package com.respectcraft.client.holo;

import com.respectcraft.client.holo.HoloCharacterScreen.Section;
import com.respectcraft.client.ui.Ui;
import com.respectcraft.registry.ModSounds;
import com.respectcraft.ui.HoloGeometry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Nucleo do menu holografico do personagem (Etapa 4.8.4-A), comum as duas cascas:
 * <ul>
 *   <li>{@link HoloWorld}: placas paradas no mundo, camera real, jogador andando (modo padrao);</li>
 *   <li>{@link HoloCharacterScreen}: a tela da Etapa 4.8.1 com camera virtual (reserva temporaria de desenvolvimento).</li>
 * </ul>
 *
 * <p>Aqui ficam a composicao em arco (secoes, chips, fita de identidade, painel Display), as animacoes de montagem,
 * troca de secao e fechamento, as superficies de cada placa e a passada 3D (placas, contornos, feixes, brilhos,
 * particulas, pedestal e holograma do jogador). Tudo em "unidades de layout" em volta de um olho em
 * {@code (centerX, layoutH / 2, eyeZ)}; a casca so entrega as matrizes do quadro ({@link #view}, {@link #layout},
 * {@link #projection}) e quantas unidades do olho valem uma unidade de layout ({@link #unit}).
 *
 * <p>Dados: so as caches do cliente; compras e telas vao como pedido. Nada e criado no mundo.
 */
final class HoloMenu {
    /** O que muda de uma casca para a outra. */
    interface Host {
        void openCreator();
        void openRoulette();
        /** Botao de fechar da fita / tecla: fecha com a animacao. */
        void requestClose();
        /** A escala do menu mudou (painel Display). */
        void relayout();
    }

    /** Campo de visao vertical da camera virtual do menu (tambem define o raio do arco: {@link #eyeZ}). */
    static final float FOV = 42F;
    /** Passo angular entre placas vizinhas no arco (graus). */
    static final float ARC_STEP = 31F;
    /** Ultima altura de conteudo de cada secao (cache so visual): a placa ja abre no tamanho certo. */
    private static final float[] FIT_CACHE = new float[Section.values().length];
    static final float CLOSE_TIME = 0.3F;

    final Minecraft mc = Minecraft.getInstance();
    private final Host host;

    // ---------------------------------------------------------------- estado
    Section section;
    float sectionStart = -10F;
    boolean closing;
    float closeStart;
    boolean modalOpen;
    private float modalAmt;
    float time, dt;

    // layout (unidades de layout)
    float uiScale = 1F, layoutW, layoutH, eyeZ;
    float centerX, plateW, plateTop, plateMaxH, headerY, headerW, headerH, chipsY, chipW, chipH;
    boolean dockAvailable;
    /** Altura (layout) do anel e da grade de chao. */
    float floorY;

    // arco (posicao continua em "indices de secao") e placas
    private float carousel;
    private final float[] carouselVel = new float[1];
    final HoloPanel[] plates = new HoloPanel[Section.values().length];
    final HoloPanel[] chips = new HoloPanel[Section.values().length];
    final HoloPanel header = new HoloPanel("header", true);
    final HoloPanel modal = new HoloPanel("modal", true);
    final List<HoloPanel> all = new ArrayList<>();
    private final List<HoloPanel> drawOrder = new ArrayList<>();

    // matrizes do quadro (entregues pela casca)
    final Matrix4f projection = new Matrix4f(), view = new Matrix4f(), layout = new Matrix4f();
    final Matrix4f viewLayout = new Matrix4f(), projViewLayout = new Matrix4f();
    private final Matrix4f scratchA = new Matrix4f(), scratchB = new Matrix4f();
    /** Unidades do espaco do olho por unidade de layout (tela: pixels de GUI; mundo: blocos). */
    float unit = 1F;
    /** Giro somado ao holograma do jogador (mundo: o yaw da ancora; tela: 0). */
    float entityYawOffset;
    /** Tamanho da tela em unidades de GUI, so para a caixa de arrasto do holograma no modo tela (0 = nao usa). */
    float screenW, screenH;

    // holograma do jogador
    private float playerYaw = -24F;
    private float stageAmt;
    boolean rotatingPlayer;
    boolean playerTouched;
    float playerBoxX0, playerBoxY0, playerBoxX1, playerBoxY1;
    boolean playerBoxValid;
    private final float[] placement = new float[4];

    // hover
    HoloPanel hotPanel;
    List<Component> tooltip;
    private String lastHoverKey;

    // efeitos
    final HoloParticles particles = new HoloParticles();
    final Holo3D r3d = new Holo3D();
    private final HoloCanvas canvas = new HoloCanvas();
    private float moteClock;
    private final Vector3f va = new Vector3f(), vb = new Vector3f(), burstAt = new Vector3f();

    // conteudo
    private final HoloView[] views;
    private final HoloNav navView = new HoloNav(this);
    private final HoloSettings settings = new HoloSettings(this);
    private boolean settingsDirty;

    HoloMenu(Host host, Section initial) {
        this.host = host;
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

    /** Limpa a cache visual estatica (logout). */
    static void clearCaches() { java.util.Arrays.fill(FIT_CACHE, 0F); }

    // ================================================================ layout

    /**
     * Composicao para um layout de {@code layoutW x layoutH} unidades com o olho a {@code eyeZ} do plano de
     * referencia. A tela passa o tamanho da janela; o mundo passa um tamanho fixo.
     */
    void layout(float layoutW, float layoutH, float uiScale, float eyeZ) {
        this.uiScale = uiScale;
        this.layoutW = layoutW;
        this.layoutH = layoutH;
        this.eyeZ = eyeZ;
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
        floorY = layoutH + 10F;
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

    void openCreator() { host.openCreator(); }

    void openRoulette() { host.openRoulette(); }

    void requestClose() { host.requestClose(); }

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
        host.relayout();
    }

    void settingsChanged() { settingsDirty = true; }

    /** Refaz o layout (escala do menu mudou no painel Display). */
    void relayout() { host.relayout(); }

    void setHoloTooltip(List<Component> lines) { this.tooltip = lines; }

    // ================================================================ ciclo de vida

    /** Avanca o relogio do menu (segundos desde a abertura). */
    void advance(float t) {
        dt = Mth.clamp(t - time, 0F, 0.1F);
        time = t;
    }

    /** Som de abertura e entrada da secao inicial. */
    void opened() {
        Ui.playUi(ModSounds.UI_HOVER.get(), 0.62F, 0.75F);
        view(section).onEnter();
    }

    /** Recomeca a montagem do zero (chamar de volta): relogio, varredura e particulas. */
    void restartAssembly() {
        time = 0F;
        dt = 0F;
        sectionStart = -10F;
        closing = false;
        modalOpen = false;
        modalAmt = 0F;
        particles.clear();
        opened();
    }

    void tick() {
        for (HoloView v : views) v.tick();
    }

    float closeDuration() { return HoloConfig.reducedMotion() ? 0.1F : CLOSE_TIME; }

    /** Comeca a dissolucao (as placas se dobram); falso se ja estava fechando. */
    boolean beginClose() {
        if (closing) return false;
        closing = true;
        closeStart = time;
        Ui.playUi(ModSounds.UI_HOVER.get(), 0.48F, 0.6F);
        if (HoloConfig.motion() > 0) {
            for (HoloPanel p : plates) if (p.visible && p.alpha > 0.2F) particles.alongEdges(p, 14, p.accent, 46F);
            particles.alongEdges(header, 10, HoloStyle.CYAN, 40F);
        }
        return true;
    }

    /** Dissolucao terminada (ou travada demais). */
    boolean closeFinished() {
        float since = time - closeStart;
        return closing && (since >= closeDuration() || since > 1.5F);
    }

    /** Libera framebuffers e particulas; grava a config se o painel Display mudou algo. */
    void release() {
        if (settingsDirty) HoloConfig.save();
        settingsDirty = false;
        particles.clear();
        hotPanel = null;
        for (HoloPanel p : all) p.close();
    }

    /** 0..1: abertura, menu inteiro, fechamento. */
    float envelope() {
        float open = HoloAnim.smooth(HoloAnim.window(time, 0F, 0.35F));
        if (!closing) return open;
        return open * (1F - HoloAnim.smooth(HoloAnim.window(time - closeStart, 0F, closeDuration())));
    }

    /** Recalcula {@code visao * layout} e {@code projecao * visao * layout} e congela as matrizes dos paineis. */
    void freeze() {
        view.mul(layout, viewLayout);
        projection.mul(viewLayout, projViewLayout);
        for (HoloPanel p : all) if (p.visible) p.freeze(viewLayout, projViewLayout);
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

    /** Raio de uma placa a {@code abs} posicoes da frente (sem hover). */
    float plateRadius(float abs) {
        return eyeZ * (1F + 0.24F * Math.min(1F, abs) + 0.12F * Math.max(0F, abs - 1F));
    }

    /** Estado animado de cada painel neste quadro: arco, abertura, troca de secao, Display e fechamento. */
    void animate() {
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
            float radius = plateRadius(abs) - lift * eyeZ * 0.05F;
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

        // ---- chips de navegacao abaixo, numa fileira curva inclinada para cima
        float chipRadius = eyeZ * 1.01F;
        float stepDeg = chipStepDeg();
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

    /** Passo angular entre chips vizinhos (graus). */
    float chipStepDeg() { return (float) Math.toDegrees(Math.atan((chipW + 6F) / (eyeZ * 1.01F))); }

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

    // ================================================================ hover

    boolean isPlate(HoloPanel p) {
        for (HoloPanel q : plates) if (q == p) return true;
        return false;
    }

    Section sectionOf(HoloPanel p) {
        for (Section s : Section.values()) if (plates[s.ordinal()] == p || chips[s.ordinal()] == p) return s;
        return null;
    }

    /** Painel interativo mais perto sob o cursor (NDC); o modal, quando aberto, captura tudo. */
    HoloPanel pick(float nx, float ny, Vector3f out) {
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

    /**
     * Painel mais perto atingido pelo raio da mira (origem no olho, direcao -z do espaco do olho), ate {@code reach}
     * unidades do olho. {@code out} = (x local, y local, distancia).
     */
    HoloPanel pickRay(float reach, Vector3f out) {
        if (closing) return null;
        HoloPanel best = null;
        float bestT = reach, bx = 0, by = 0;
        for (HoloPanel p : all) {
            if (!p.visible || !p.interactive || (modalOpen && p != modal)) continue;
            if (p.rayHit(out) && out.z < bestT) {
                bestT = out.z;
                best = p;
                bx = out.x;
                by = out.y;
            }
        }
        if (best != null) out.set(bx, by, bestT);
        return best;
    }

    void clearHot() {
        for (HoloPanel p : all) {
            p.hot = false;
            p.cursorX = p.cursorY = -9999F;
        }
        hotPanel = null;
    }

    void setHot(HoloPanel p, float x, float y) {
        p.hot = true;
        p.cursorX = x;
        p.cursorY = y;
        hotPanel = p;
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

    // ================================================================ superficies

    /**
     * Redesenha as superficies dos paineis visiveis. {@code density} pixels por unidade de layout (tela: escala da
     * GUI; mundo: config) e {@code contentScale} a escala do conteudo dentro dela (tela: escala do menu; mundo: 1).
     * Com {@code mipmaps} gera os niveis menores depois do desenho (texto lido de longe, no mundo).
     */
    void drawSurfaces(GuiGraphics g, float density, float contentScale, boolean mipmaps) {
        tooltip = null;
        drawPanel(g, header, density, contentScale, mipmaps, headerW, headerH, () -> navView.drawHeader(canvas, header.w, header.h));
        for (Section s : Section.values()) {
            HoloPanel p = plates[s.ordinal()];
            HoloView v = view(s);
            boolean active = s == section;
            // placas de lado mostram o conteudo de verdade, mas sem hover: um clique nelas so traz a placa para a frente
            boolean hot = p.hot;
            if (!active) p.hot = false;
            drawPanel(g, p, density, contentScale, mipmaps, plateW, plateMaxH, () -> v.draw(canvas, p.w, p.h, time - 0.16F));
            p.hot = hot;
            HoloPanel chip = chips[s.ordinal()];
            drawPanel(g, chip, density, contentScale, mipmaps, chipW, chipH, () -> navView.drawChip(canvas, s, chip.w, chip.h, active));
        }
        if (modal.visible) drawPanel(g, modal, density, contentScale, mipmaps, modal.w, modal.h, () -> settings.draw(canvas, modal.w, modal.h));
    }

    private void drawPanel(GuiGraphics g, HoloPanel p, float density, float contentScale, boolean mipmaps,
                           float surfaceW, float surfaceH, Runnable content) {
        if (!p.visible || p.alpha <= 0.003F) {
            p.resetZones();
            return;
        }
        if (p.culled && p.surface.ready()) return; // fora da vista: mantem a textura e as regioes do ultimo desenho
        p.surface.ensure(surfaceW, surfaceH, density, contentScale);
        p.resetZones();
        GuiGraphics sg = p.surface.begin(g);
        try {
            canvas.bind(sg, p, this, time, dt);
            content.run();
        } finally {
            p.surface.end();
        }
        if (mipmaps) p.surface.mipmap();
    }

    // ================================================================ passada 3D

    /** Passada 3D inteira: {@code world} = dentro da passada do mundo (profundidade do cenario preservada). */
    void render3D(boolean world) {
        if (world) r3d.beginWorld(projection);
        else r3d.begin(projection);
        try {
            draw3D();
        } finally {
            if (world) r3d.endWorld();
            else r3d.end();
        }
    }

    private void draw3D() {
        float env = envelope();
        float motion = HoloConfig.motion();
        backDecor(env, motion);
        drawPlayer(env);

        drawOrder.clear();
        for (HoloPanel p : all) if (p.visible && p.alpha > 0.003F && p.surface.ready()) drawOrder.add(p);
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
            float pulse = 0.85F + 0.15F * Mth.sin(time * 2.4F + p.depth / unit);
            glowCorner(p, 0F, 0F, a * pulse);
            glowCorner(p, p.w, 0F, a * 0.7F);
            glowCorner(p, 0F, p.h, a * 0.7F);
            glowCorner(p, p.w, p.h, a * pulse);
        }
        lightTrails(env, true);
        pedestalGlow(env);
        particles.render(r3d, viewLayout, unit, env);
        if (hotPanel != null && motion > 0F && !closing) {
            Vector3f c = hotPanel.toLayout(hotPanel.cursorX, hotPanel.cursorY, r3d.scratch());
            r3d.glowAt(viewLayout, c.x, c.y, c.z + 0.5F, 4.5F * unit, Ui.alpha(hotPanel.accent, 0.5F));
        }
        r3d.endGlow();
    }

    private void glowCorner(HoloPanel p, float lx, float ly, float a) {
        Vector3f v = p.toLayout(lx, ly, r3d.scratchB());
        r3d.glowAt(viewLayout, v.x, v.y, v.z + 0.3F, 7F * unit, Ui.alpha(p.accent, 0.5F * a));
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
            r3d.beam(ea, eb, 0.9F * unit, Ui.alpha(color, 0.30F * k), Ui.alpha(color, 0.12F * k));
            return;
        }
        if (motion <= 0F) return;
        float f = (time * 0.7F + index * 0.27F) % 1F;
        float x = a.x + (b.x - a.x) * f, y = a.y + (b.y - a.y) * f, z = a.z + (b.z - a.z) * f;
        float fade = Mth.sin(f * Mth.PI);
        r3d.glowAt(viewLayout, x, y, z, 5F * unit, Ui.alpha(color, 0.8F * fade * k));
        r3d.glowAt(viewLayout, x, y, z, 1.6F * unit, Ui.alpha(0xFFFFFFFF, 0.9F * fade * k));
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
        Matrix4f floor = scratchB.set(viewLayout).translate(centerX, floorY, eyeZ).rotateX(-Mth.HALF_PI);
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
            r3d.beam(core, target, 1.4F * unit, Ui.alpha(0xFFFFFFFF, 0.7F * a), Ui.alpha(p.accent, 0.35F * a));
        }
    }

    /** Fase A da abertura: ponto de luz que se forma antes das placas. */
    private void invocationGlow() {
        if (closing) return;
        float t = time;
        float k = HoloAnim.window(t, 0F, 0.09F) * (1F - HoloAnim.window(t, 0.2F, 0.3F));
        if (k <= 0F) return;
        float r = (HoloConfig.reducedMotion() ? 18F : 6F + 70F * HoloAnim.outCubic(HoloAnim.window(t, 0F, 0.3F))) * unit;
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
        if (mc.player == null) return;
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
        r3d.entity(mc.player, view, new Vector3f(feet), blockSize * unit, playerYaw + entityYawOffset);

        // caixa de arrasto (so modo tela): projecao dos pes e da cabeca (mesmas matrizes do desenho)
        if (screenW <= 0F || screenH <= 0F) return;
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
        if (scale <= 0.02F || mc.player == null) return;
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
        if (scale <= 0.02F || mc.player == null) return;
        float[] at = playerPlacement();
        float height = at[3] * scale;
        r3d.glowAt(viewLayout, at[0], at[1], at[2], height * 0.32F * unit, Ui.alpha(HoloStyle.CYAN, 0.28F * scale));
        r3d.glowAt(viewLayout, at[0], at[1] - height * 0.5F, at[2] - 8F, height * 0.55F * unit, Ui.alpha(HoloStyle.CYAN, 0.07F * scale));
    }

    /** Particulas: avanca as existentes e solta as do ambiente. */
    void updateParticles() {
        particles.update(dt);
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

    /** Faiscas no ponto de clique de um painel. */
    void clickBurst(HoloPanel p, float lx, float ly) {
        if (HoloConfig.motion() <= 0F) return;
        Vector3f at = p.toLayout(lx, ly, burstAt);
        particles.burst(at.x, at.y, at.z + 1F, 6, p.accent, 40F);
    }

    // ================================================================ utilidades para as secoes

    /** Faiscas que nascem de um ponto local da placa da frente (ex.: no de habilidade comprado). */
    void burstOnMain(float lx, float ly, int color, int count, float speed) {
        if (HoloConfig.motion() <= 0F) return;
        Vector3f at = plates[section.ordinal()].toLayout(lx, ly, burstAt);
        particles.burst(at.x, at.y, at.z + 2F, count, color, speed);
    }

    HoloPanel mainPanel() { return plates[section.ordinal()]; }

    float playerYaw() { return playerYaw; }

    void rotatePlayer(float degrees) {
        playerTouched = true;
        playerYaw += degrees;
    }
}
