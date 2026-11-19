package com.respectcraft.client.holo;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/**
 * Superficie de um painel: um {@link TextureTarget} transparente, reaproveitado entre quadros (so realoca quando o
 * tamanho em pixels muda: resize, escala de GUI ou escala do menu) e liberado em {@link #close()}.
 *
 * <p>O conteudo e desenhado com {@link GuiGraphics} em coordenadas logicas do painel, na densidade real da tela
 * ({@code guiScale * uiScale} pixels por unidade): com o painel parado no plano de referencia cada texel cai num
 * pixel. A mistura acumula alfa ({@code ONE, ONE_MINUS_SRC_ALPHA} no canal alfa), entao a textura sai com cor
 * pre-multiplicada e e composta na cena com {@code ONE, ONE_MINUS_SRC_ALPHA}.
 */
final class HoloSurface {
    private TextureTarget target;
    private int pxW, pxH;
    private float guiScale = 1F, uiScale = 1F;
    private GuiGraphics graphics;
    private final Matrix4f savedProjection = new Matrix4f();
    private VertexSorting savedSorting;
    private final Matrix4f ortho = new Matrix4f();
    private boolean drawing;

    /** Garante a textura para {@code logicalW x logicalH} unidades de layout. */
    void ensure(float logicalW, float logicalH, float guiScale, float uiScale) {
        this.guiScale = guiScale;
        this.uiScale = uiScale;
        int w = Math.max(1, Mth.ceil(logicalW * guiScale * uiScale));
        int h = Math.max(1, Mth.ceil(logicalH * guiScale * uiScale));
        if (target == null) {
            target = new TextureTarget(w, h, false, Minecraft.ON_OSX);
            target.setClearColor(0F, 0F, 0F, 0F);
        } else if (w != pxW || h != pxH) {
            target.resize(w, h, Minecraft.ON_OSX);
        }
        target.setFilterMode(GL11.GL_LINEAR);
        pxW = w;
        pxH = h;
    }

    boolean ready() { return target != null; }
    int textureId() { return target.getColorTextureId(); }
    int pixelWidth() { return pxW; }
    int pixelHeight() { return pxH; }
    float density() { return guiScale * uiScale; }

    /** Coordenada U da borda direita de um conteudo com {@code logicalW} de largura. */
    float u(float logicalW) { return Math.min(1F, logicalW * density() / pxW); }
    /** Coordenada V da borda de baixo (a textura do framebuffer comeca embaixo). */
    float vBottom(float logicalH) { return Math.max(0F, 1F - logicalH * density() / pxH); }

    /** Limpa e passa a desenhar na superficie. Sempre em par com {@link #end()}. */
    GuiGraphics begin(GuiGraphics screen) {
        Minecraft mc = Minecraft.getInstance();
        screen.flush();
        target.clear(Minecraft.ON_OSX);
        target.bindWrite(true);
        savedProjection.set(RenderSystem.getProjectionMatrix());
        savedSorting = RenderSystem.getVertexSorting();
        RenderSystem.setProjectionMatrix(ortho.setOrtho(0F, pxW / guiScale, pxH / guiScale, 0F, 1000F,
                net.neoforged.neoforge.client.ClientHooks.getGuiFarPlane()), VertexSorting.ORTHOGRAPHIC_Z);
        if (graphics == null) graphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
        graphics.pose().pushPose();
        graphics.pose().scale(uiScale, uiScale, 1F);
        accumulate();
        drawing = true;
        return graphics;
    }

    /** Volta para o framebuffer principal e restaura projecao, recorte e mistura. */
    void end() {
        if (!drawing) return;
        drawing = false;
        graphics.flush();
        RenderSystem.disableScissor();
        graphics.pose().popPose();
        graphics.setColor(1F, 1F, 1F, 1F);
        RenderSystem.setProjectionMatrix(savedProjection, savedSorting);
        RenderSystem.defaultBlendFunc();
        Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
    }

    /** Recorte em unidades logicas do painel (o recorte do GuiGraphics usa a janela, nao este alvo). */
    void scissor(float x, float y, float w, float h) {
        graphics.flush();
        float d = density();
        int left = Mth.clamp(Mth.floor(x * d), 0, pxW), right = Mth.clamp(Mth.ceil((x + w) * d), 0, pxW);
        int top = Mth.clamp(Mth.floor(y * d), 0, pxH), bottom = Mth.clamp(Mth.ceil((y + h) * d), 0, pxH);
        RenderSystem.enableScissor(left, pxH - bottom, Math.max(0, right - left), Math.max(0, bottom - top));
    }

    void unscissor() {
        graphics.flush();
        RenderSystem.disableScissor();
    }

    /** Mistura que acumula alfa corretamente num alvo transparente. */
    static void accumulate() {
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
    }

    /** Mistura aditiva que soma luz sem mexer no alfa (brilho que nao "fura" o vidro). */
    static void additive() {
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
    }

    void close() {
        if (drawing) end();
        if (target != null) target.destroyBuffers();
        target = null;
        pxW = pxH = 0;
    }
}
