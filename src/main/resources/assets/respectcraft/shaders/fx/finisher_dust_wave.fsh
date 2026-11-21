// RespectCraft (Etapa 4.10) — queda: onda de poeira que corre pelo chao onde o corpo bate.
// A RimuLib antepoe #version 150 e common.glsl. Tudo relativo a camera.
uniform vec3 DustCenter;   // onde o corpo bate (relativo a camera)
uniform vec4 DustState;    // x: ticks desde a queda, y: velocidade (blocos/tick), z: forca, w: alcance (blocos)
uniform vec4 DustColor;    // rgb: cor da poeira, a: semente 0..1

#include "calamidade_fx.glsl"

void main() {
    vec3 color = texture(DiffuseSampler, texCoord).rgb;
    float strength = DustState.z;
    if (strength > 0.001 && DustState.x >= 0.0 && !skyAt(texCoord)) {
        vec3 d = scenePosAt(texCoord) - DustCenter;
        float ground = 1.0 - smoothstep(0.3, 0.9, abs(d.y + 0.05));
        if (ground > 0.0) {
            float r = length(d.xz);
            float band = caDistanceWave(r, DustState.x, DustState.y, 0.45, DustState.w);
            float inner = caDistanceWave(r, DustState.x - 2.0, DustState.y * 0.7, 0.8, DustState.w) * 0.5;
            float breakup = smoothstep(0.35, 0.75, fbm2(d.xz * 3.1 + DustColor.a * 53.0));
            float dust = (band + inner) * breakup * ground;
            // Poeira cobre a cena (nao soma luz): mistura com a cor do po, e um toque quente na borda.
            color = mix(color, DustColor.rgb, clamp(dust * strength * 0.6, 0.0, 0.85));
            color += vec3(0.25, 0.16, 0.08) * band * ground * strength * 0.25;
        }
    }
    fragColor = vec4(color, 1.0);
}
