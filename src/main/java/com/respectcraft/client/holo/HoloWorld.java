package com.respectcraft.client.holo;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.respectcraft.RespectCraft;
import com.respectcraft.client.character.CharacterCreationScreen;
import com.respectcraft.client.character.CharacterCreatorKeybinds;
import com.respectcraft.client.cinematic.CinematicCamera;
import com.respectcraft.client.holo.HoloCharacterScreen.Section;
import com.respectcraft.client.roulette.BirthRouletteScreen;
import com.respectcraft.ui.HoloGeometry;
import com.respectcraft.ui.HoloPlacement;
import cyou.rimuru.rimulib.mc1211.client.api.MenuApi;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.WinScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;

/**
 * Menu holografico do personagem <b>no mundo</b> (Etapa 4.8.4-A): a tecla K invoca o arco de placas parado num
 * ponto do mundo, na frente de onde o jogador olhava; ele continua andando, pulando e olhando em volta (nao ha
 * {@code Screen}, nada de entrada capturada). Andar em volta mostra as placas de lado e por tras; virar de costas
 * as tira do campo de visao.
 *
 * <p><b>Espaco.</b> O mesmo arranjo da Etapa 4.8.1 ({@link HoloMenu}), em unidades de layout num tamanho fixo, vira
 * mundo por uma unica matriz ({@link HoloGeometry#layoutToWorld}): o "olho" do arco e a posicao dos olhos do jogador
 * na invocacao e a frente do arco aponta para o yaw dele. A camera e a real ({@code RenderLevelStageEvent}
 * AFTER_LEVEL: rotacao da camera e projecao com o balanco da caminhada), em coordenadas relativas a camera.
 *
 * <p><b>Quadro.</b> {@code RenderFrameEvent.Pre} (antes do mundo): relogio, animacao, hover pela mira e redesenho das
 * superficies (framebuffers reaproveitados; as que ficaram fora da vista nao sao redesenhadas). AFTER_LEVEL:
 * matrizes, esmaecimento de perto/por tras e a passada 3D com teste de profundidade contra o cenario (parede na
 * frente esconde a placa; o depth buffer nunca e limpo).
 *
 * <p><b>Escopo da 4.8.4-A.</b> So invocar (K), dispensar (K) e chamar de volta (tecla Recall, R por padrao), mais um
 * prototipo de hover pela mira para validar as coordenadas. Cliques, rolagem e arrasto nos paineis ficam para a
 * 4.8.4-B: aqui o botao do mouse continua sendo do mundo.
 *
 * <p>Estado so visual e so deste cliente (instancia estatica, limpa no logout, na troca de nivel, na morte e no
 * respawn). Nenhuma entidade, bloco ou pacote.
 */
public final class HoloWorld {
    /** Layout fixo do modo mundo (unidades de layout), independente da janela e da escala da GUI. */
    static final float LAYOUT_W = 640F, LAYOUT_H = 360F;
    /** Menor distancia da placa da frente (blocos) que a escolha de posicao aceita. */
    static final float MIN_DISTANCE = 0.7F;
    /** Alcance do hover pela mira (blocos). */
    static final float HOVER_REACH = 6F;

    private static HoloWorld current;

    private final Minecraft mc = Minecraft.getInstance();
    private final HoloMenu menu;
    private final Vector3f hit = new Vector3f(), local = new Vector3f();
    private ClientLevel level;
    /** Olho do arco no mundo, yaw da frente (graus do Minecraft) e blocos por unidade de layout. */
    private double anchorX, anchorY, anchorZ;
    private float yaw;
    private float scale;
    private long openNanos;
    private long closeRequestedNanos;
    /** Ja passou por uma passada do mundo (matrizes validas para hover e recorte). */
    private boolean rendered;

    private HoloWorld(Section initial) {
        this.menu = new HoloMenu(new HoloMenu.Host() {
            @Override public void openCreator() {
                closeNow();
                mc.setScreen(new CharacterCreationScreen());
            }
            @Override public void openRoulette() {
                closeNow();
                mc.setScreen(new BirthRouletteScreen());
            }
            @Override public void requestClose() { dismiss(); }
            @Override public void relayout() {}
        }, initial);
        float eyeZ = HoloGeometry.referenceDistance(LAYOUT_H, HoloMenu.FOV);
        menu.layout(LAYOUT_W, LAYOUT_H, 1F, eyeZ);
    }

    // ================================================================ API (teclas)

    public static boolean isOpen() { return current != null; }

    /** K: invoca na frente da mira; com os paineis abertos, dispensa. */
    public static void toggle() {
        if (current != null && !current.menu.closing) dismiss();
        else summon(Section.OVERVIEW);
    }

    /** Invoca (ou, se ja houver paineis, chama de volta para a mira atual). */
    public static void summon(Section initial) {
        Minecraft mc = Minecraft.getInstance();
        if (!canSummon(mc)) return;
        if (current != null) {
            if (current.menu.closing) current.closeNow();
            else {
                current.recallHere();
                return;
            }
        }
        HoloWorld w = new HoloWorld(initial);
        w.level = mc.level;
        boolean fits = w.place(mc.player);
        current = w;
        w.start();
        w.hint(fits);
        w.logAnchor("summoned", fits);
    }

    /** Tecla Recall: leva os paineis abertos para a frente da mira atual, com a montagem de novo. */
    public static void recall() {
        if (current == null || current.menu.closing) return;
        current.recallHere();
    }

    /** Dispensa com a animacao de dissolucao. */
    public static void dismiss() {
        if (current == null) return;
        if (current.menu.beginClose()) current.closeRequestedNanos = System.nanoTime();
    }

    /** Fecha na hora e libera framebuffers (morte, respawn, troca de nivel, logout, tela incompativel). */
    public static void closeAll() {
        if (current != null) current.closeNow();
    }

    private static boolean canSummon(Minecraft mc) {
        LocalPlayer player = mc.player;
        return player != null && mc.level != null && player.isAlive() && !player.isSpectator();
    }

    // ================================================================ posicao

    /**
     * Escolhe a ancora a partir dos olhos e do yaw do jogador (so leitura do nivel: um {@code level.clip} por ponto
     * de amostra). Devolve se o arranjo coube de fato.
     */
    private boolean place(LocalPlayer player) {
        Vec3 eye = player.getEyePosition();
        HoloPlacement.Result r = HoloPlacement.choose(eye.x, eye.y, eye.z, player.getYRot(), HoloConfig.worldDistance(),
                MIN_DISTANCE, samples(), (fx, fy, fz, tx, ty, tz) -> player.level().clip(new ClipContext(new Vec3(fx, fy, fz),
                        new Vec3(tx, ty, tz), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS);
        anchorX = eye.x;
        anchorY = eye.y;
        anchorZ = eye.z;
        yaw = r.yaw();
        scale = r.distance() / menu.eyeZ;
        menu.unit = scale;
        menu.entityYawOffset = yaw + 180F;
        // anel e grade de chao no chao de verdade (logo acima, sem brigar com a face do bloco)
        menu.floorY = LAYOUT_H / 2F + (float) (player.getEyeHeight() - 0.03) / scale;
        return r.fits();
    }

    /**
     * Pontos de amostra do arranjo em repouso, em raios do arco ({@link HoloPlacement.Sample}): cantos e meio das
     * cinco posicoes de placa do arco, a fita de identidade e a fileira de chips.
     */
    private List<HoloPlacement.Sample> samples() {
        List<HoloPlacement.Sample> out = new ArrayList<>();
        float ez = menu.eyeZ, cy = LAYOUT_H / 2F;
        float half = menu.plateW / 2F * 0.92F;
        for (int a = -2; a <= 2; a++) {
            float abs = Math.abs(a);
            double deg = Math.toRadians(a * HoloMenu.ARC_STEP);
            float r = menu.plateRadius(abs) / ez;
            int kind = a == 0 ? HoloPlacement.ESSENTIAL : abs == 1 ? HoloPlacement.IMPORTANT : HoloPlacement.OPTIONAL;
            float top = menu.plateTop + 12F * Math.min(1F, abs);
            float[] ups = {(cy - top - 8F) / ez, 0F, (cy - (menu.plateTop + menu.plateMaxH) + 8F) / ez};
            float cx = (float) Math.sin(deg) * r, cz = (float) Math.cos(deg) * r;
            float tx = (float) Math.cos(deg), tz = (float) -Math.sin(deg);
            for (float up : ups) {
                for (float k : new float[]{-1F, 0F, 1F}) {
                    float off = k * half / ez;
                    out.add(new HoloPlacement.Sample(cx + tx * off, up, cz + tz * off, kind));
                }
            }
        }
        float headerUp = (cy - menu.headerY - menu.headerH / 2F) / ez;
        for (float k : new float[]{-1F, 0F, 1F}) {
            out.add(new HoloPlacement.Sample(k * menu.headerW / 2F * 0.9F / ez, headerUp, 1.06F, HoloPlacement.ESSENTIAL));
        }
        float chipUp = (cy - menu.chipsY - menu.chipH / 2F) / ez;
        float step = (float) Math.toRadians(menu.chipStepDeg());
        for (int i = -2; i <= 2; i++) {
            out.add(new HoloPlacement.Sample((float) Math.sin(i * step) * 1.01F, chipUp, (float) Math.cos(i * step) * 1.01F,
                    Math.abs(i) <= 1 ? HoloPlacement.ESSENTIAL : HoloPlacement.IMPORTANT));
        }
        return out;
    }

    private void start() {
        openNanos = System.nanoTime();
        rendered = false;
        menu.time = 0F;
        menu.opened();
    }

    private void recallHere() {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;
        level = mc.level;
        boolean fits = place(player);
        openNanos = System.nanoTime();
        rendered = false;
        menu.restartAssembly();
        hint(fits);
        logAnchor("recalled", fits);
    }

    private void hint(boolean fits) {
        LocalPlayer player = mc.player;
        if (player == null) return;
        Component msg = fits
                ? Component.translatable("gui.respectcraft.holo.world.summoned", CharacterCreatorKeybinds.menuKeyName(),
                        CharacterCreatorKeybinds.recallKeyName())
                : Component.translatable("gui.respectcraft.holo.world.cramped", CharacterCreatorKeybinds.recallKeyName());
        player.displayClientMessage(msg, true);
    }

    private void closeNow() {
        menu.release();
        if (current == this) current = null;
    }

    /** Escondido (sem fechar) durante a camera cinematografica e com a GUI oculta (F1). */
    private boolean hidden() {
        return CinematicCamera.active() || mc.options.hideGui;
    }

    // ================================================================ quadro

    /** Antes do mundo: relogio, animacao, hover pela mira e superficies. */
    private void frame() {
        if (mc.level == null || mc.player == null) {
            closeNow();
            return;
        }
        menu.advance((System.nanoTime() - openNanos) / 1e9F);
        if (menu.closeFinished()) {
            closeNow();
            return;
        }
        if (hidden()) return;
        menu.animate();
        hover();
        for (HoloPanel p : menu.all) p.culled = rendered && p.visible && p.offscreen();
        Matrix4fStack mv = RenderSystem.getModelViewStack();
        mv.pushMatrix();
        // mesma model-view da GUI: a projecao ortografica das superficies espera o z da GUI
        mv.translation(0F, 0F, 10000F - ClientHooks.getGuiFarPlane());
        RenderSystem.applyModelViewMatrix();
        Lighting.setupFor3DItems();
        try {
            menu.drawSurfaces(null, HoloConfig.surfaceDensity(), 1F, true);
        } finally {
            mv.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
        menu.updateParticles();
    }

    /**
     * Prototipo de hover (so visual, 4.8.4-A): o raio da mira contra as placas, com as matrizes do ultimo quadro.
     * Realca o elemento sob a mira e desenha o ponto de mira na placa; nao clica, nao rola, nao captura nada.
     */
    private void hover() {
        menu.clearHot();
        if (!rendered || mc.screen != null) return;
        HoloPanel p = menu.pickRay(HOVER_REACH, hit);
        if (p != null) menu.setHot(p, hit.x, hit.y);
    }

    /** Passada no mundo: matrizes da camera real, esmaecimento de perto/por tras e desenho. */
    private void renderWorld(RenderLevelStageEvent event) {
        if (hidden() || mc.level != level) return;
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        HoloGeometry.layoutToWorld(menu.layout, (float) (anchorX - cam.x), (float) (anchorY - cam.y), (float) (anchorZ - cam.z),
                yaw, scale, menu.centerX, LAYOUT_H / 2F, menu.eyeZ);
        menu.view.set(event.getModelViewMatrix());
        menu.projection.set(event.getProjectionMatrix());
        menu.freeze();
        fadeNearAndBehind();
        menu.render3D(true);
        rendered = true;
    }

    /**
     * Placa atravessada pela camera esmaece em vez de cortar no plano proximo; vista por tras fica mais apagada
     * (o vidro e o texto espelhado continuam la, como um holograma de verdade).
     */
    private void fadeNearAndBehind() {
        for (HoloPanel p : menu.all) {
            if (!p.visible) continue;
            Vector3f e = p.eyeLocal(local);
            float blocks = HoloGeometry.distanceToRect(e.x, e.y, e.z, p.w, p.h) * scale;
            float k = HoloAnim.smooth((blocks - 0.08F) / 0.4F);
            if (e.z < 0F) k *= 0.55F;
            p.alpha *= k;
            p.outline *= k;
        }
    }

    // ================================================================ ciclo de vida

    private void tick() {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.level != level || !player.isAlive() || player.isSpectator()
                || incompatible(mc.screen) || rimulibMenuOpen()) {
            closeNow();
            return;
        }
        menu.tick();
        if (menu.closing) {
            // quadros parados (janela minimizada): nao deixa a dissolucao pendurada
            if (System.nanoTime() - closeRequestedNanos > 2_000_000_000L) closeNow();
            return;
        }
        Vec3 eye = player.getEyePosition();
        double dx = eye.x - anchorX, dy = eye.y - anchorY, dz = eye.z - anchorZ;
        float far = HoloConfig.dismissDistance();
        if (dx * dx + dy * dy + dz * dz > far * far) dismiss();
    }

    /** Telas que tiram o jogador do mundo ou ocupam o lugar do menu: fecham os paineis. */
    private static boolean incompatible(Screen screen) {
        return screen instanceof DeathScreen || screen instanceof WinScreen || screen instanceof ReceivingLevelScreen
                || screen instanceof LevelLoadingScreen || screen instanceof ProgressScreen || screen instanceof GenericMessageScreen
                || screen instanceof DisconnectedScreen || screen instanceof TitleScreen || screen instanceof CharacterCreationScreen
                || screen instanceof BirthRouletteScreen || screen instanceof HoloCharacterScreen;
    }

    private static boolean rimulibMenuOpen() {
        try {
            return MenuApi.isOpen();
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    // ================================================================ eventos

    static void onRenderFrame(RenderFrameEvent.Pre event) {
        if (current != null) current.frame();
    }

    static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL && current != null) current.renderWorld(event);
    }

    static void onClientTick(ClientTickEvent.Post event) {
        if (current != null) current.tick();
    }

    /**
     * No modo mundo, quem pedia a tela da 4.8.1 (fim do criador, Continue da roleta, Esc do criador) recebe os
     * paineis no mundo: a tela nao abre, a anterior fecha normalmente ({@code removed}) e o arco e invocado.
     */
    static void onScreenOpening(ScreenEvent.Opening event) {
        if (!(event.getNewScreen() instanceof HoloCharacterScreen holo) || !HoloConfig.worldMode()) return;
        Minecraft mc = Minecraft.getInstance();
        if (!canSummon(mc)) return;
        Section section = holo.initialSection();
        event.setNewScreen(null);
        mc.tell(() -> summon(section));
    }

    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        closeAll();
        HoloMenu.clearCaches();
    }

    static void onClone(ClientPlayerNetworkEvent.Clone event) { closeAll(); }

    static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) closeAll();
    }

    /** Linha de log da ancora (uma por K/Recall): ajuda a conferir no teste em cliente real que ela nao se move. */
    private void logAnchor(String what, boolean fits) {
        RespectCraft.LOGGER.info(String.format(java.util.Locale.ROOT,
                "Holographic panels %s: anchor=(%.2f, %.2f, %.2f) yaw=%.1f frontDistance=%.2f fits=%s", what, anchorX, anchorY,
                anchorZ, yaw, scale * menu.eyeZ, fits));
    }
}
