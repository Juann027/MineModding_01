// RespectCraft (Etapa 4.10) — impact frame dos golpes de forca 3: a tela vira duas cores por limiar de luminancia,
// com um holofote claro na vitima. Inverte so quando clarões sao permitidos (a forca ja vem com a acessibilidade).
// A RimuLib antepoe #version 150 e common.glsl.
uniform vec3 ImpactCenter; // peito da vitima, relativo a camera
uniform vec3 ImpactLight;  // cor das areas claras
uniform vec3 ImpactDark;   // cor das areas escuras
uniform vec4 ImpactState;  // x: forca 0..1, y: inverter (0/1), z: limiar de luminancia

#include "calamidade_fx.glsl"

void main() {
    vec3 color = texture(DiffuseSampler, texCoord).rgb;
    float strength = ImpactState.x;
    if (strength > 0.001) {
        float luma = caLuma(color);
        vec3 p = project(ImpactCenter);
        float spot = 0.0;
        if (p.z > 0.0) {
            vec2 d = texCoord - p.xy;
            d.x *= OutSize.x / max(OutSize.y, 1.0);
            spot = 1.0 - smoothstep(0.05, 0.32, length(d));
        }
        float threshold = ImpactState.z - 0.18 * spot;        // perto da vitima, mais area clara
        float light = smoothstep(threshold - 0.035, threshold + 0.035, luma);
        if (ImpactState.y > 0.5) light = 1.0 - light;
        vec3 tone = mix(ImpactDark, ImpactLight, light);
        color = mix(color, tone, strength);
    }
    fragColor = vec4(color, 1.0);
}
