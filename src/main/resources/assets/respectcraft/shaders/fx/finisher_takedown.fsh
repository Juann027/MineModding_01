// RespectCraft (Etapa 4.10) — TAKEDOWN: linhas de foco de anime convergindo no ponto da cena. Discreto.
// A RimuLib antepoe #version 150 e common.glsl. Espaco: relativo a camera (o de scenePosAt).
uniform vec3 FocusCenter;  // peito entre os dois, relativo a camera (post.center + post.facing)
uniform vec4 FocusState;   // x: forca (ja com a acessibilidade), y: idade em ticks, z: semente 0..1, w: 1 = linhas se mexem
uniform vec3 FocusColor;   // cor das linhas

#include "calamidade_fx.glsl"

void main() {
    vec3 color = texture(DiffuseSampler, texCoord).rgb;
    float strength = FocusState.x;
    vec3 p = project(FocusCenter);
    if (strength > 0.001 && p.z > 0.0) {
        float aspect = OutSize.x / max(OutSize.y, 1.0);
        vec2 d = texCoord - p.xy;
        d.x *= aspect;
        float r = length(d);
        float a = atan(d.y, d.x) / TAU + 0.5;
        // Sem efeitos de movimento, as linhas ficam paradas; com eles, trocam a cada 2 ticks (traço de anime).
        float frame = FocusState.w > 0.5 ? floor(FocusState.y * 0.5) : 0.0;
        float cells = 140.0;
        float cell = floor(a * cells);
        float h = hash12(vec2(cell, frame + FocusState.z * 31.0));
        float keep = step(0.52, h);
        float f = fract(a * cells);
        float thickness = mix(0.12, 0.38, hash12(vec2(cell * 1.37, frame + 7.0)));
        float lineMask = 1.0 - smoothstep(0.0, thickness, abs(f - 0.5) * 2.0);
        float inner = mix(0.62, 0.34, h);
        float radial = smoothstep(inner, inner + 0.42, r);
        float lines = lineMask * keep * radial;
        // Bordas um pouco mais escuras: o olho vai para o centro da cena.
        color *= 1.0 - 0.18 * strength * smoothstep(0.35, 1.0, r);
        color = mix(color, FocusColor, clamp(lines * strength * 0.55, 0.0, 1.0));
    }
    fragColor = vec4(color, 1.0);
}
