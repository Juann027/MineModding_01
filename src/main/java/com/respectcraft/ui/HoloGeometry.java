package com.respectcraft.ui;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Geometria do menu holografico (Etapa 4.8.1): projecao em perspectiva, posicao dos paineis e o caminho inverso
 * (cursor -> coordenada local do painel). Puro (so JOML, sem classes do cliente) para o {@code SelfTest} conferir
 * no servidor dedicado que o desenho e o clique usam exatamente a mesma matriz.
 *
 * <p>Espacos usados:
 * <ul>
 *   <li><b>Layout</b>: unidades de GUI (x para a direita, y para baixo, z para o jogador), origem no canto superior
 *   esquerdo da tela, como um {@code Screen} comum. Os retangulos dos paineis vivem aqui.</li>
 *   <li><b>Menu</b>: o mesmo espaco com y para cima e origem no centro da tela, sobre o plano de referencia.</li>
 *   <li><b>Olho</b>: camera virtual na origem olhando para -z. O plano de referencia fica a {@link #referenceDistance}
 *   e, com a projecao {@link #offAxis}, mostra exatamente {@code w x h} unidades de GUI: um painel parado nesse plano
 *   cai pixel a pixel na tela (texto nitido), e o que esta mais perto/longe ganha paralaxe real.</li>
 * </ul>
 */
public final class HoloGeometry {
    private HoloGeometry() {}

    /** Distancia do plano de referencia para que ele ocupe {@code screenH} com o campo de visao vertical dado. */
    public static float referenceDistance(float screenH, float fovDegrees) {
        return screenH / (2F * (float) Math.tan(Math.toRadians(fovDegrees) / 2.0));
    }

    /**
     * Projecao fora do eixo ("janela"): o olho pode se deslocar ({@code eyeX}, {@code eyeY}) sem mexer no plano de
     * referencia, que continua cobrindo a tela inteira. Assim o cursor cria paralaxe sem borrar o painel principal.
     */
    public static Matrix4f offAxis(Matrix4f dest, float screenW, float screenH, float distance, float eyeX, float eyeY,
                                   float near, float far) {
        float k = near / distance;
        return dest.setFrustum((-screenW / 2F - eyeX) * k, (screenW / 2F - eyeX) * k,
                (-screenH / 2F - eyeY) * k, (screenH / 2F - eyeY) * k, near, far);
    }

    /** Matriz de visao: olho deslocado, plano de referencia a {@code distance} e giro leve da cena (graus). */
    public static Matrix4f view(Matrix4f dest, float distance, float eyeX, float eyeY, float yawDegrees, float pitchDegrees) {
        return dest.translation(-eyeX, -eyeY, -distance)
                .rotateX((float) Math.toRadians(pitchDegrees))
                .rotateY((float) Math.toRadians(yawDegrees));
    }

    /**
     * Layout (GUI, y para baixo) -> menu (y para cima, centro da tela). Com {@code uiScale != 1} o layout tem
     * {@code screenW / uiScale} unidades de largura e continua cobrindo a tela inteira.
     */
    public static Matrix4f layoutToMenu(Matrix4f dest, float screenW, float screenH, float uiScale) {
        return dest.translation(-screenW / 2F, screenH / 2F, 0F).scale(uiScale, -uiScale, uiScale);
    }

    /**
     * Painel em coordenadas de layout: retangulo {@code x, y, w, h}, pivo relativo ({@code 0..1}), afastamento
     * {@code dz} (positivo = mais perto do jogador), giro em torno do pivo e escala. O espaco local do painel e o
     * mesmo do conteudo desenhado nele (origem no canto superior esquerdo, y para baixo).
     */
    public static Matrix4f panel(Matrix4f dest, float x, float y, float w, float h, float pivotX, float pivotY, float dz,
                                 float yawDegrees, float pitchDegrees, float scaleX, float scaleY) {
        float px = pivotX * w, py = pivotY * h;
        return dest.translation(x + px, y + py, dz)
                .rotateY((float) Math.toRadians(yawDegrees))
                .rotateX((float) Math.toRadians(pitchDegrees))
                .scale(scaleX, scaleY, 1F)
                .translate(-px, -py, 0F);
    }

    /**
     * Raio do cursor (NDC) no plano local z = 0 de um painel, a partir da inversa de {@code projecao * visao * modelo}.
     * {@code out} recebe (x local, y local, t), em que {@code t} (0..1 do plano proximo ao distante) ordena paineis
     * sobrepostos: menor = mais perto. Falso se o raio for paralelo ao painel ou o cruzar fora do volume visivel.
     */
    public static boolean intersect(Matrix4f inversePvm, float ndcX, float ndcY, Vector3f out) {
        Vector4f a = new Vector4f(ndcX, ndcY, -1F, 1F);
        Vector4f b = new Vector4f(ndcX, ndcY, 1F, 1F);
        inversePvm.transform(a);
        inversePvm.transform(b);
        if (Math.abs(a.w) < 1e-9F || Math.abs(b.w) < 1e-9F) return false;
        float ax = a.x / a.w, ay = a.y / a.w, az = a.z / a.w;
        float bx = b.x / b.w, by = b.y / b.w, bz = b.z / b.w;
        float dz = bz - az;
        if (Math.abs(dz) < 1e-7F) return false;
        float t = -az / dz;
        if (t < 0F || t > 1F) return false;
        out.set(ax + (bx - ax) * t, ay + (by - ay) * t, t);
        return true;
    }

    /** Como {@link #intersect}, mas so aceita o ponto dentro do retangulo {@code 0..w x 0..h}. */
    public static boolean hit(Matrix4f inversePvm, float ndcX, float ndcY, float w, float h, Vector3f out) {
        if (!intersect(inversePvm, ndcX, ndcY, out)) return false;
        float eps = 1e-3F;
        return out.x >= -eps && out.y >= -eps && out.x <= w + eps && out.y <= h + eps;
    }

    /** Ponto local de um painel -> NDC (x, y, profundidade). Falso se o ponto estiver atras do olho. */
    public static boolean project(Matrix4f pvm, float x, float y, float z, Vector3f outNdc) {
        Vector4f v = new Vector4f(x, y, z, 1F);
        pvm.transform(v);
        if (v.w <= 1e-6F) return false;
        outNdc.set(v.x / v.w, v.y / v.w, v.z / v.w);
        return true;
    }

    /** NDC -> coordenada de GUI (pixels logicos da tela, y para baixo). */
    public static float ndcToGuiX(float ndcX, float screenW) { return (ndcX + 1F) * 0.5F * screenW; }
    public static float ndcToGuiY(float ndcY, float screenH) { return (1F - ndcY) * 0.5F * screenH; }
    public static float guiToNdcX(float guiX, float screenW) { return guiX / screenW * 2F - 1F; }
    public static float guiToNdcY(float guiY, float screenH) { return 1F - guiY / screenH * 2F; }
}
