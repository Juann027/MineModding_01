package com.respectcraft.client.holo;

import com.respectcraft.ui.HoloGeometry;
import java.util.ArrayList;
import java.util.List;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Um painel do menu no espaco 3D: retangulo de repouso (layout), estado animado do quadro (profundidade, giro,
 * escala, opacidade, contorno) e as matrizes congeladas no quadro. Desenho, raio do cursor e particulas usam
 * sempre estas mesmas matrizes.
 */
final class HoloPanel {
    final String id;
    final HoloSurface surface;

    /** Retangulo em unidades de layout. */
    float x, y, w, h;
    /** Pivo relativo do giro/escala (0..1). */
    float pivotX = 0.5F, pivotY = 0.5F;
    /** Estado animado do quadro atual. */
    float dz, yaw, pitch, scaleX = 1F, scaleY = 1F;
    /** Opacidade do vidro/quadro inteiro, do conteudo (texto) e progresso do contorno (0..1). */
    float alpha, contentAlpha, outline;
    /** Varredura holografica 0..1 (negativo = nenhuma). */
    float sweep = -1F;
    int accent = HoloStyle.CYAN;
    boolean visible;
    boolean interactive;

    /** Matrizes do quadro: modelo em layout, PVM e inversa (clique), e visao*modelo (desenho). */
    final Matrix4f model = new Matrix4f();
    final Matrix4f modelView = new Matrix4f();
    final Matrix4f pvm = new Matrix4f();
    final Matrix4f inverse = new Matrix4f();
    /** Inversa de visao*modelo (espaco do olho -> local): raio da mira no modo mundo. */
    final Matrix4f inverseModelView = new Matrix4f();
    private final Vector3f scratch = new Vector3f();
    float depth;
    /** Modo mundo: fora do campo de visao no ultimo quadro (a superficie nao e redesenhada). */
    boolean culled;

    /** Cursor sobre este painel neste quadro (coordenadas locais). */
    boolean hot;
    float cursorX = -9999F, cursorY = -9999F;

    private final List<HoloZone> zones = new ArrayList<>();
    private int zoneCount;

    HoloPanel(String id, boolean withSurface) {
        this.id = id;
        this.surface = withSurface ? new HoloSurface() : null;
    }

    void rect(float x, float y, float w, float h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    void rest() {
        dz = yaw = pitch = 0F;
        scaleX = scaleY = 1F;
    }

    /** Congela as matrizes do quadro a partir de visao*layout e projecao*visao*layout. */
    void freeze(Matrix4f viewLayout, Matrix4f projViewLayout) {
        HoloGeometry.panel(model, x, y, w, h, pivotX, pivotY, dz, yaw, pitch, scaleX, scaleY);
        viewLayout.mul(model, modelView);
        projViewLayout.mul(model, pvm);
        pvm.invert(inverse);
        modelView.invert(inverseModelView);
        modelView.transformPosition(scratch.set(w / 2F, h / 2F, 0F));
        depth = scratch.z;
    }

    /**
     * Raio da mira (origem no olho, direcao -z do espaco do olho) dentro do painel; grava o ponto local e a distancia
     * (unidades do olho) em {@code out}.
     */
    boolean rayHit(Vector3f out) {
        if (Math.abs(scaleX) < 0.05F || Math.abs(scaleY) < 0.05F) return false;
        if (!HoloGeometry.rayLocal(inverseModelView, 0F, 0F, 0F, 0F, 0F, -1F, out)) return false;
        // rayLocal devolve t na unidade da direcao no espaco do olho: a distancia de verdade
        float eps = 1e-3F;
        return out.x >= -eps && out.y >= -eps && out.x <= w + eps && out.y <= h + eps;
    }

    /** Posicao do olho no espaco local do painel (x, y e o afastamento z do plano, em unidades de layout). */
    Vector3f eyeLocal(Vector3f out) {
        return inverseModelView.transformPosition(out.set(0F, 0F, 0F));
    }

    /** Todos os cantos fora do mesmo lado do volume visivel (clip space de {@link #pvm}). */
    boolean offscreen() {
        int left = 0, right = 0, below = 0, above = 0, behind = 0;
        for (int i = 0; i < 4; i++) {
            float lx = (i & 1) == 0 ? 0F : w, ly = (i & 2) == 0 ? 0F : h;
            float cx = pvm.m00() * lx + pvm.m10() * ly + pvm.m30();
            float cy = pvm.m01() * lx + pvm.m11() * ly + pvm.m31();
            float cw = pvm.m03() * lx + pvm.m13() * ly + pvm.m33();
            if (cw <= 0F) behind++;
            if (cx < -cw) left++;
            if (cx > cw) right++;
            if (cy < -cw) below++;
            if (cy > cw) above++;
        }
        return behind == 4 || left == 4 || right == 4 || below == 4 || above == 4;
    }

    /** Raio do cursor (NDC) dentro do painel; grava o ponto local e o {@code t} em {@code out}. */
    boolean hit(float ndcX, float ndcY, Vector3f out) {
        if (Math.abs(scaleX) < 0.05F || Math.abs(scaleY) < 0.05F) return false;
        return HoloGeometry.hit(inverse, ndcX, ndcY, w, h, out);
    }

    /** Ponto local -> espaco de layout (para particulas que nascem do painel). */
    Vector3f toLayout(float lx, float ly, Vector3f out) {
        return model.transformPosition(out.set(lx, ly, 0F));
    }

    void resetZones() { zoneCount = 0; }

    HoloZone addZone(float zx, float zy, float zw, float zh) {
        HoloZone zone;
        if (zoneCount < zones.size()) zone = zones.get(zoneCount);
        else {
            zone = new HoloZone();
            zones.add(zone);
        }
        zoneCount++;
        return zone.set(zx, zy, zw, zh);
    }

    /** Regiao mais acima (ultima desenhada) no ponto local, ou null. */
    HoloZone zoneAt(float lx, float ly) {
        for (int i = zoneCount - 1; i >= 0; i--) {
            HoloZone zone = zones.get(i);
            if (zone.contains(lx, ly) && (zone.click != null || zone.drag != null)) return zone;
        }
        return null;
    }

    void close() {
        if (surface != null) surface.close();
        zoneCount = 0;
        zones.clear();
    }
}
