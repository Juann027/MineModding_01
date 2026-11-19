// RespectCraft (Etapa 4.8.1): fundo do menu holografico. Passada do FxApi do Rimulib sobre a cena do mundo
// (antes da GUI, entao o texto dos paineis nunca passa por aqui). O Rimulib antepoe o common.glsl
// (DiffuseSampler, texCoord, fragColor, OutSize, GTime): sem #version aqui.
uniform float Strength;
uniform vec3 Tint;

void main() {
    vec3 scene = texture(DiffuseSampler, texCoord).rgb;
    float k = clamp(Strength, 0.0, 1.0);
    // dessatura puxando para um cinza levemente azulado
    float luma = dot(scene, vec3(0.2126, 0.7152, 0.0722));
    vec3 graded = mix(scene, luma * vec3(0.80, 0.93, 1.06), 0.65 * k);
    // vinheta na cor do holograma
    vec2 d = (texCoord - 0.5) * vec2(OutSize.x / max(OutSize.y, 1.0), 1.0);
    float vig = smoothstep(0.30, 0.95, length(d));
    graded = mix(graded, Tint * 0.30, vig * 0.65 * k);
    // modulacao de varredura bem fraca (so no mundo)
    graded *= 1.0 - 0.035 * k * (0.5 + 0.5 * sin(texCoord.y * OutSize.y * 1.3 - GTime * 5.0));
    fragColor = vec4(graded, 1.0);
}
