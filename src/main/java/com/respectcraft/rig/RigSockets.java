package com.respectcraft.rig;

import java.util.Optional;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Pontos semanticos (sockets) de rigs customizadas (Etapa 4.12).
 *
 * <p><b>Espacos.</b> (1) Local do modelo: pixels do Blockbench (16 px = 1 bloco), origem nos pes, +Y para cima,
 * frente -Z, +X = direita do modelo. O {@code .geo.json} guarda X invertido (convencao Bedrock); o GeckoLib desinverte
 * ao assar, entao o espaco assado e o mesmo do Blockbench. (2) Mundo: o {@code GeoEntityRenderer} gira o modelo por
 * {@code 180 - yawCorpo} e escala por {@code render_scale}; logo a frente -Z vira a direcao do olhar da entidade.
 *
 * <p><b>Servidor (autoritativo, so pose de repouso).</b> O GeckoLib 4.9.3 nao avalia animacoes no servidor (nao ha
 * matrizes de bone la). {@link #restWorld} da a posicao do socket NA POSE DE REPOUSO, girada pelo corpo da entidade:
 * pivo do bone (contrato × proporcao do binding, ou override) + deslocamento. Serve para efeitos/sons e alinhamento
 * aproximado; nunca use como dano ou colisao exata.
 *
 * <p><b>Cliente (pose animada, so visual).</b> {@code client.rig.RigGeoModel} liga {@code setTrackingMatrices} nos
 * bones {@code socket_*} existentes; depois do desenho, {@code GeoBone.getWorldPosition()} da a posicao animada do
 * quadro. E so do cliente e de quem desenhou: nunca mande isso de volta para o servidor como verdade.
 *
 * <p>Locators do Blockbench sao lidos pelo GeckoLib 4.9.3 ({@code Bone.locators}), mas nao vao para o modelo assado;
 * por isso o contrato usa bones vazios {@code socket_<nome>} (opcionais) como marcadores, alem do manifesto.
 */
public final class RigSockets {
    private RigSockets() {}

    /** Posicao local (px, Blockbench) do socket neste modelo, pose de repouso. */
    public static Optional<Vec3> local(CustomRigRegistry registry, RigBinding binding, String socket) {
        Optional<RigDefinition.Socket> s = registry.socket(binding, socket);
        if (s.isEmpty()) return Optional.empty();
        boolean own = binding.sockets().contains(s.get());
        return registry.pivot(binding, s.get().bone()).map(p -> p.add(own ? s.get().offset() : s.get().offset().multiply(binding.proportion())));
    }

    /** Regra pura: local (px) → mundo, dado posicao, giro do corpo e escala de desenho. */
    public static Vec3 toWorld(Vec3 origin, float bodyYaw, float renderScale, Vec3 localPx) {
        double r = Math.toRadians(bodyYaw);
        Vec3 forward = new Vec3(-Math.sin(r), 0, Math.cos(r));
        Vec3 right = new Vec3(-Math.cos(r), 0, -Math.sin(r));
        double k = renderScale / 16.0;
        return origin.add(right.scale(localPx.x * k)).add(0, localPx.y * k, 0).add(forward.scale(-localPx.z * k));
    }

    /** Socket no mundo, pose de repouso (servidor ou cliente). Vazio sem rig ou sem o socket. */
    public static Optional<Vec3> restWorld(LivingEntity entity, String socket) {
        Optional<RigBinding> binding = RigVisuals.binding(entity);
        if (binding.isEmpty()) return Optional.empty();
        return local(CustomRigRegistry.GLOBAL, binding.get(), socket)
                .map(l -> toWorld(entity.position(), entity.yBodyRot, binding.get().renderScale(), l));
    }
}
