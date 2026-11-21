// RespectCraft (Etapa 4.10) — RASTEIRA: arco luminoso rente ao chao que varre em volta das pernas da vitima.
// A RimuLib antepoe #version 150 e common.glsl. Tudo relativo a camera.
uniform vec3 SweepCenter;  // pes da vitima (relativo a camera)
uniform vec3 SweepFwd;     // "frente" da cena (executor -> vitima), eixos do mundo
uniform vec3 SweepRight;   // "direita" da cena
uniform vec4 SweepState;   // x: forca, y: progresso da varredura 0..1, z: idade em ticks, w: raio do arco (blocos)
uniform vec3 SweepColor;

#include "calamidade_fx.glsl"

void main() {
    vec3 color = texture(DiffuseSampler, texCoord).rgb;
    float strength = SweepState.x;
    if (strength > 0.001 && !skyAt(texCoord)) {
        vec3 d = scenePosAt(texCoord) - SweepCenter;
        float ground = 1.0 - smoothstep(0.25, 0.8, abs(d.y));
        if (ground > 0.0) {
            float x = dot(d, SweepRight);
            float z = dot(d, SweepFwd);
            float r = length(vec2(x, z));
            // Angulo a partir do lado do executor (-frente), de -90 (esquerda) a +90 graus (direita).
            float ang = atan(x, -z);
            float head = mix(-1.66, 1.66, SweepState.y);
            float behind = head - ang;                       // quanto o ponto ja foi varrido (radianos)
            float trail = (behind >= 0.0 && ang >= -1.66) ? exp(-behind * 1.6) : 0.0;
            float headGlow = exp(-behind * behind * 40.0);
            float band = 1.0 - smoothstep(0.0, 0.16, abs(r - SweepState.w));
            float halo = exp(-abs(r - SweepState.w) * 5.0) * 0.35;
            float sparks = step(0.93, hash12(floor(vec2(ang * 40.0, SweepState.z * 0.5)))) * band;
            float glow = (band * (trail + headGlow) + halo * trail + sparks * trail) * ground;
            color += SweepColor * glow * strength * 1.7;
        }
    }
    fragColor = vec4(color, 1.0);
}
