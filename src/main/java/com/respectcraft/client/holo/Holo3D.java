package com.respectcraft.client.holo;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.respectcraft.client.ui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

/**
 * Passada em perspectiva do menu, desenhada por cima do mundo dentro do {@code Screen}: troca a projecao ortografica
 * da GUI pela projecao do menu, limpa so a profundidade, desenha jogador, paineis (quads texturizados com a
 * superficie de cada um), contornos, brilhos e particulas, e devolve exatamente o estado da GUI
 * (projecao, model-view, profundidade, cull, mistura, neblina e luzes).
 *
 * <p>Usa os shaders do proprio jogo ({@code position_tex_color}/{@code position_color}): como os vertices vao com
 * a projecao real, a GPU interpola as UVs com correcao de perspectiva (o {@code w} carrega a profundidade).
 */
final class Holo3D {
    /** Luzes do jogador no espaco do olho: de cima e da frente (a previa do inventario, sem o espelhamento da GUI). */
    private static final Vector3f LIGHT_0 = new Vector3f(0.2F, 1.0F, 1.0F).normalize();
    private static final Vector3f LIGHT_1 = new Vector3f(-0.2F, 1.0F, 0.0F).normalize();

    private final Matrix4f savedProjection = new Matrix4f();
    private VertexSorting savedSorting;
    private float savedFogStart;
    private boolean active;
    private boolean world;
    private boolean entityDrawn;
    private final PoseStack poseStack = new PoseStack();
    private final Vector3f a = new Vector3f(), b = new Vector3f(), cc = new Vector3f(), d = new Vector3f();
    private BufferBuilder batch;

    void begin(Matrix4f projection) {
        savedProjection.set(RenderSystem.getProjectionMatrix());
        savedSorting = RenderSystem.getVertexSorting();
        RenderSystem.setProjectionMatrix(projection, VertexSorting.DISTANCE_TO_ORIGIN);
        Matrix4fStack mv = RenderSystem.getModelViewStack();
        mv.pushMatrix();
        mv.identity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.disableCull();
        savedFogStart = RenderSystem.getShaderFogStart();
        RenderSystem.setShaderFogStart(Float.MAX_VALUE);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        active = true;
    }

    void end() {
        if (!active || world) return;
        active = false;
        discardBatch();
        RenderSystem.setShaderFogStart(savedFogStart);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        Matrix4fStack mv = RenderSystem.getModelViewStack();
        mv.popMatrix();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(savedProjection, savedSorting);
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        Lighting.setupFor3DItems();
    }

    /**
     * Passada dentro do mundo (Etapa 4.8.4-A, {@code RenderLevelStageEvent} AFTER_LEVEL): mesma projecao da camera
     * real, model-view zerada (os vertices ja levam visao * layout * modelo, relativos a camera), teste de
     * profundidade <b>contra o cenario</b> sem gravar e sem limpar o depth buffer (parede na frente esconde a placa),
     * sem neblina. {@link #endWorld} devolve o estado que o resto do quadro espera.
     */
    void beginWorld(Matrix4f projection) {
        savedProjection.set(RenderSystem.getProjectionMatrix());
        savedSorting = RenderSystem.getVertexSorting();
        RenderSystem.setProjectionMatrix(projection, VertexSorting.DISTANCE_TO_ORIGIN);
        Matrix4fStack mv = RenderSystem.getModelViewStack();
        mv.pushMatrix();
        mv.identity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        savedFogStart = RenderSystem.getShaderFogStart();
        RenderSystem.setShaderFogStart(Float.MAX_VALUE);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        entityDrawn = false;
        world = true;
        active = true;
    }

    void endWorld() {
        if (!active || !world) return;
        active = false;
        world = false;
        discardBatch();
        RenderSystem.setShaderFogStart(savedFogStart);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        RenderSystem.depthMask(true);
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        Matrix4fStack mv = RenderSystem.getModelViewStack();
        mv.popMatrix();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(savedProjection, savedSorting);
        if (entityDrawn) {
            // a mao em primeira pessoa (desenhada depois) usa as luzes do mundo
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && mc.level.effects().constantAmbientLight()) Lighting.setupNetherLevel();
            else Lighting.setupLevel();
        }
    }

    private void discardBatch() {
        if (batch != null) {
            MeshData mesh = batch.build();
            if (mesh != null) mesh.close();
            batch = null;
        }
    }

    // ---------------------------------------------------------------- paineis

    /**
     * Quad do painel com a textura da superficie (cor pre-multiplicada). {@code mv} = visao * layout * modelo;
     * {@code w x h} = conteudo em unidades locais; {@code u1}/{@code v0} = recorte da textura.
     */
    void texturedPanel(Matrix4f mv, float w, float h, int texture, float u1, float v0, float opacity) {
        if (opacity <= 0.003F) return;
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        depthTestNoWrite();
        BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        float o = Math.min(1F, opacity);
        buf.addVertex(mv, 0F, 0F, 0F).setUv(0F, 1F).setColor(o, o, o, o);
        buf.addVertex(mv, 0F, h, 0F).setUv(0F, v0).setColor(o, o, o, o);
        buf.addVertex(mv, w, h, 0F).setUv(u1, v0).setColor(o, o, o, o);
        buf.addVertex(mv, w, 0F, 0F).setUv(u1, 1F).setColor(o, o, o, o);
        BufferUploader.drawWithShader(buf.buildOrThrow());
        RenderSystem.defaultBlendFunc();
    }

    /**
     * Teste de profundidade ligado sem gravar: o jogador (desenhado antes, com profundidade) tampa o que esta atras
     * dele. Necessario porque os RenderTypes do jogo desligam o teste ao terminar o proprio lote.
     */
    private static void depthTestNoWrite() {
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(false);
        // os RenderTypes do jogador religam o cull ao terminar: no mundo a placa vista por tras sumiria
        RenderSystem.disableCull();
    }

    // ---------------------------------------------------------------- lote de cor aditiva

    void beginLines() {
        batch = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
    }

    void endLines() {
        if (batch == null) return;
        MeshData mesh = batch.build();
        batch = null;
        if (mesh == null) return;
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        depthTestNoWrite();
        BufferUploader.drawWithShader(mesh);
        RenderSystem.defaultBlendFunc();
    }

    /** Segmento no plano local z = 0 de {@code m}, com espessura e cores nas pontas (aditivo). */
    void segment(Matrix4f m, float x1, float y1, float x2, float y2, float thickness, int c1, int c2) {
        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-3F || batch == null) return;
        float nx = -dy / len * thickness / 2F, ny = dx / len * thickness / 2F;
        vertex(m, x1 + nx, y1 + ny, 0F, c1);
        vertex(m, x1 - nx, y1 - ny, 0F, c1);
        vertex(m, x2 - nx, y2 - ny, 0F, c2);
        vertex(m, x2 + nx, y2 + ny, 0F, c2);
    }

    /** Segmento 3D entre dois pontos do espaco do olho (feixes), com largura em tela aproximada. */
    void beam(Vector3f p1, Vector3f p2, float thickness, int c1, int c2) {
        if (batch == null) return;
        float dx = p2.x - p1.x, dy = p2.y - p1.y;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-3F) return;
        float nx = -dy / len * thickness / 2F, ny = dx / len * thickness / 2F;
        eyeVertex(p1.x + nx, p1.y + ny, p1.z, c1);
        eyeVertex(p1.x - nx, p1.y - ny, p1.z, c1);
        eyeVertex(p2.x - nx, p2.y - ny, p2.z, c2);
        eyeVertex(p2.x + nx, p2.y + ny, p2.z, c2);
    }

    /** Retangulo cheio no plano local (gradiente vertical). */
    void rect(Matrix4f m, float x, float y, float w, float h, int top, int bottom) {
        if (batch == null) return;
        vertex(m, x, y, 0F, top);
        vertex(m, x, y + h, 0F, bottom);
        vertex(m, x + w, y + h, 0F, bottom);
        vertex(m, x + w, y, 0F, top);
    }

    /**
     * Contorno do painel desenhado ate {@code progress} (0..1) a partir do canto superior esquerdo nos dois sentidos,
     * com uma faixa de brilho larga e uma linha fina por cima.
     */
    void outline(Matrix4f m, float w, float h, float progress, int color, float glow) {
        if (progress <= 0.001F || batch == null) return;
        float per = 2F * (w + h);
        float half = per / 2F * Math.min(1F, progress);
        int soft = Ui.alpha(color, 0.22F * glow);
        int sharp = Ui.alpha(color, 0.85F * Math.min(1F, glow + 0.2F));
        // sentido horario: topo -> direita; anti-horario: esquerda -> base
        path(m, w, h, half, true, 5F, soft);
        path(m, w, h, half, false, 5F, soft);
        path(m, w, h, half, true, 1F, sharp);
        path(m, w, h, half, false, 1F, sharp);
    }

    private void path(Matrix4f m, float w, float h, float length, boolean clockwise, float t, int color) {
        float[][] pts = clockwise
                ? new float[][]{{0, 0}, {w, 0}, {w, h}, {0, h}}
                : new float[][]{{0, 0}, {0, h}, {w, h}, {w, 0}};
        float left = length;
        for (int i = 0; i < 3 && left > 0; i++) {
            float x1 = pts[i][0], y1 = pts[i][1], x2 = pts[i + 1][0], y2 = pts[i + 1][1];
            float seg = Math.abs(x2 - x1) + Math.abs(y2 - y1);
            float k = Math.min(1F, left / seg);
            segment(m, x1, y1, x1 + (x2 - x1) * k, y1 + (y2 - y1) * k, t, color, color);
            left -= seg;
        }
    }

    /** Anel no plano XZ (chao) do espaco de menu transformado por {@code m} (elipse real em perspectiva). */
    void ring(Matrix4f m, float cx, float cy, float cz, float radius, float thickness, int color, int segments) {
        if (batch == null) return;
        for (int i = 0; i < segments; i++) {
            float a0 = (float) (i * Math.PI * 2 / segments), a1 = (float) ((i + 1) * Math.PI * 2 / segments);
            float r0 = radius - thickness / 2F, r1 = radius + thickness / 2F;
            vertex(m, cx + (float) Math.cos(a0) * r0, cy, cz + (float) Math.sin(a0) * r0, color);
            vertex(m, cx + (float) Math.cos(a0) * r1, cy, cz + (float) Math.sin(a0) * r1, color);
            vertex(m, cx + (float) Math.cos(a1) * r1, cy, cz + (float) Math.sin(a1) * r1, color);
            vertex(m, cx + (float) Math.cos(a1) * r0, cy, cz + (float) Math.sin(a1) * r0, color);
        }
    }

    /** Arco no plano local XY (anel de fundo). */
    void arc(Matrix4f m, float cx, float cy, float radius, float thickness, float from, float to, int color, int segments) {
        if (batch == null) return;
        for (int i = 0; i < segments; i++) {
            float a0 = from + (to - from) * i / segments, a1 = from + (to - from) * (i + 1) / segments;
            float r0 = radius - thickness / 2F, r1 = radius + thickness / 2F;
            vertex(m, cx + (float) Math.cos(a0) * r0, cy + (float) Math.sin(a0) * r0, 0F, color);
            vertex(m, cx + (float) Math.cos(a0) * r1, cy + (float) Math.sin(a0) * r1, 0F, color);
            vertex(m, cx + (float) Math.cos(a1) * r1, cy + (float) Math.sin(a1) * r1, 0F, color);
            vertex(m, cx + (float) Math.cos(a1) * r0, cy + (float) Math.sin(a1) * r0, 0F, color);
        }
    }

    private void vertex(Matrix4f m, float x, float y, float z, int argb) {
        batch.addVertex(m, x, y, z).setColor((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, argb >>> 24);
    }

    private void eyeVertex(float x, float y, float z, int argb) {
        batch.addVertex(x, y, z).setColor((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, argb >>> 24);
    }

    // ---------------------------------------------------------------- brilhos (sprites)

    void beginGlow() {
        batch = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
    }

    /** Sprite de brilho virado para a camera, centro no espaco do olho, raio em unidades do olho. */
    void glow(float ex, float ey, float ez, float radius, int argb) {
        if (batch == null || (argb >>> 24) == 0 || radius <= 0.05F) return;
        int r = (argb >> 16) & 255, g = (argb >> 8) & 255, bl = argb & 255, al = argb >>> 24;
        batch.addVertex(ex - radius, ey + radius, ez).setUv(0F, 0F).setColor(r, g, bl, al);
        batch.addVertex(ex - radius, ey - radius, ez).setUv(0F, 1F).setColor(r, g, bl, al);
        batch.addVertex(ex + radius, ey - radius, ez).setUv(1F, 1F).setColor(r, g, bl, al);
        batch.addVertex(ex + radius, ey + radius, ez).setUv(1F, 0F).setColor(r, g, bl, al);
    }

    /** Brilho num ponto do espaco de layout (transformado por visao * layout). */
    void glowAt(Matrix4f viewLayout, float x, float y, float z, float radius, int argb) {
        viewLayout.transformPosition(a.set(x, y, z));
        glow(a.x, a.y, a.z, radius, argb);
    }

    void endGlow() {
        if (batch == null) return;
        MeshData mesh = batch.build();
        batch = null;
        if (mesh == null) return;
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, Ui.GLOW);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        depthTestNoWrite();
        BufferUploader.drawWithShader(mesh);
        RenderSystem.defaultBlendFunc();
    }

    // ---------------------------------------------------------------- jogador

    /**
     * O proprio jogador (rig GeckoLib, Body Layers, skin e camadas pelo renderer vanilla) em perspectiva real.
     * {@code view} = so a visao (y para cima); {@code feet} = pes no espaco de menu; {@code blockSize} = unidades
     * por bloco. Os angulos da entidade sao restaurados depois, como na previa do inventario.
     */
    void entity(LivingEntity entity, Matrix4f view, Vector3f feet, float blockSize, float yaw) {
        Minecraft mc = Minecraft.getInstance();
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        float bodyRot = entity.yBodyRot, bodyRotO = entity.yBodyRotO, yRot = entity.getYRot(), yRotO = entity.yRotO;
        float xRot = entity.getXRot(), xRotO = entity.xRotO, headRot = entity.yHeadRot, headRotO = entity.yHeadRotO;
        entity.yBodyRot = yaw;
        entity.yBodyRotO = yaw;
        entity.setYRot(yaw);
        entity.yRotO = yaw;
        entity.setXRot(0F);
        entity.xRotO = 0F;
        entity.yHeadRot = yaw;
        entity.yHeadRotO = yaw;
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.setShaderLights(LIGHT_0, LIGHT_1);
        poseStack.pushPose();
        poseStack.setIdentity();
        poseStack.mulPose(view);
        poseStack.translate(feet.x, feet.y, feet.z);
        float s = blockSize / Math.max(0.1F, entity.getScale());
        poseStack.scale(s, s, s);
        Quaternionf savedOrientation = dispatcher.cameraOrientation();
        dispatcher.overrideCameraOrientation(new Quaternionf().rotateY((float) Math.PI));
        dispatcher.setRenderShadow(false);
        entityDrawn = true;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        try {
            RenderSystem.runAsFancy(() -> dispatcher.render(entity, 0.0, 0.0, 0.0, 0.0F, 1.0F, poseStack, buffers, 15728880));
            buffers.endBatch();
        } finally {
            dispatcher.setRenderShadow(true);
            if (savedOrientation != null) dispatcher.overrideCameraOrientation(savedOrientation);
            poseStack.popPose();
            entity.yBodyRot = bodyRot;
            entity.yBodyRotO = bodyRotO;
            entity.setYRot(yRot);
            entity.yRotO = yRotO;
            entity.setXRot(xRot);
            entity.xRotO = xRotO;
            entity.yHeadRot = headRot;
            entity.yHeadRotO = headRotO;
        }
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
    }

    Vector3f scratch() { return d; }
    Vector3f scratchB() { return b; }
    Vector3f scratchC() { return cc; }
}
