package com.respectcraft.finisher;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/**
 * Clip de uma finalizacao no rig GeckoLib PROPRIO de um tipo de entidade (Etapa 4.8). E so dados: o servidor
 * dispara {@code GeoEntity.triggerAnim(controller, animation)} no mesmo tick em que os jogadores comecam os seus
 * clips, e o GeckoLib sincroniza com quem esta vendo. Nada aqui supoe ossos de jogador: cada entidade usa o seu
 * esqueleto, o seu controlador e o nome de animacao gatilho que ela mesma registra em {@code registerControllers}
 * ({@code AnimationController.triggerableAnim(animation, ...)}).
 *
 * @param entityType    id do tipo de entidade (ex.: {@code respectcraft:agua_viva})
 * @param role          papel em que este clip vale (executor ou vitima)
 * @param controller    nome do controlador GeckoLib da entidade que toca o clip
 * @param animation     nome da animacao gatilho registrada nesse controlador
 * @param durationTicks duracao do clip; precisa ser igual a da cena ({@link FinisherDef#durationTicks()})
 * @param alignment     ajuste de palco especifico do rig (distancia, deslocamento lateral, giro do modelo)
 */
public record EntityFinisherClip(ResourceLocation entityType, FinisherRole role, String controller, String animation,
                                 int durationTicks, Alignment alignment) {
    /**
     * Ajuste de posicionamento do rig. A posicao no mundo continua sendo da sessao; keyframes so com deslocamentos
     * que voltam a zero.
     *
     * @param distance  distancia executor-vitima em blocos; {@code NaN} = automatica pelas caixas de colisao
     * @param lateral   deslocamento da vitima para a direita do executor (blocos; negativo = esquerda)
     * @param yawOffset giro extra aplicado a ESTA entidade (graus), para rigs cuja frente nao e +Z
     */
    public record Alignment(double distance, double lateral, float yawOffset) {
        public static final Alignment AUTO = new Alignment(Double.NaN, 0, 0F);

        public Alignment {
            if (!Double.isNaN(distance) && (distance < 0.3 || distance > 6.0)) {
                throw new IllegalArgumentException("Distancia de palco fora de 0.3..6: " + distance);
            }
            if (Math.abs(lateral) > 3.0) throw new IllegalArgumentException("Deslocamento lateral fora de -3..3: " + lateral);
        }

        public boolean autoDistance() { return Double.isNaN(distance); }
    }

    public EntityFinisherClip {
        Objects.requireNonNull(entityType, "entityType");
        Objects.requireNonNull(role, "role");
        if (controller == null || controller.isBlank()) throw new IllegalArgumentException("Controlador vazio: " + entityType);
        if (animation == null || animation.isBlank()) throw new IllegalArgumentException("Animacao vazia: " + entityType);
        if (durationTicks <= 0) throw new IllegalArgumentException("Duracao deve ser positiva: " + entityType);
        if (alignment == null) alignment = Alignment.AUTO;
    }

    public EntityFinisherClip(ResourceLocation entityType, FinisherRole role, String controller, String animation,
                              int durationTicks) {
        this(entityType, role, controller, animation, durationTicks, Alignment.AUTO);
    }
}
