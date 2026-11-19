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
    private final Vector3f scratch = new Vector3f();
    float depth;

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
        modelView.transformPosition(scratch.set(w / 2F, h / 2F, 0F));
        depth = scratch.z;
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
