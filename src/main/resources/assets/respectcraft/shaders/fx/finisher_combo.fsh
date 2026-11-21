// RespectCraft (Etapa 4.10) — COMBO: cupula de pressao em volta dos dois, que pulsa a cada golpe; ondas no chao
// nos golpes e nas quedas. O mais intenso. A RimuLib antepoe #version 150 e common.glsl. Tudo relativo a camera.
uniform vec3 DomeCenter;   // ponto da cena no chao (relativo a camera)
uniform vec4 DomeState;    // x: forca, y: impulso 0..1, z: raio (blocos), w: idade em ticks
uniform vec4 DomeHit;      // x: ticks desde o ultimo golpe/queda (<0 = nenhum), y: velocidade (blocos/tick), z: forca da onda
uniform vec3 DomeColor;

#include "calamidade_fx.glsl"

float shell(vec3 rd, float t, float sceneT) {
    if (t <= 0.0 || t >= sceneT) return 0.0;
    vec3 p = rd * t - DomeCenter;
    if (p.y < 0.0) return 0.0;                              // so a metade de cima (cupula)
    vec3 n = normalize(p);
    float rim = pow(1.0 - abs(dot(n, rd)), 3.0);
    float grid = caWorldGrid(p, n, 0.45, 0.035, gPix * t);
    float top = smoothstep(0.0, DomeState.z, p.y);          // mais forte embaixo
    return rim * (0.35 + 0.9 * DomeState.y) + grid * (0.08 + 0.7 * DomeState.y) * (1.0 - 0.6 * top);
}

void main() {
    vec3 color = texture(DiffuseSampler, texCoord).rgb;
    float strength = DomeState.x;
    if (strength > 0.001) {
        initPixel(texCoord);
        vec3 rd = viewDir(texCoord);
        float sceneT = length(scenePosAt(texCoord));
        vec2 hit = caRaySphere(vec3(0.0), rd, DomeCenter, DomeState.z);
        float glow = 0.0;
        if (hit.x >= 0.0 || hit.y > 0.0) {
            glow += shell(rd, hit.x, sceneT);
            glow += 0.6 * shell(rd, hit.y, sceneT);         // face de tras, mais fraca
        }
        if (DomeHit.x >= 0.0 && !skyAt(texCoord)) {
            vec3 d = scenePosAt(texCoord) - DomeCenter;
            float ground = 1.0 - smoothstep(0.5, 1.2, abs(d.y));
            float r = length(d.xz);
            float wave = caDistanceWave(r, DomeHit.x, DomeHit.y, 0.3, DomeState.z * 1.8);
            glow += wave * DomeHit.z * ground * 1.4;
        }
        color += DomeColor * glow * strength;
    }
    fragColor = vec4(color, 1.0);
}
