package com.tupinicraft.skilltree;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Fronteira entre o TupiniCraft e a biblioteca de árvores de habilidades (SkillLib).
 * O SkillLib é dono de: definição das skills/categorias, interface, desbloqueio, ativação, nível e a
 * sincronização desses dados. O TupiniCraft é dono de: dinheiro, filiação, mandatos, pontos de cargo e
 * da decisão (no servidor) de quem pode comprar e manter cada efeito ativo.
 */
public interface SkillBackend {
    boolean available();

    boolean skillExists(ResourceLocation skillId);

    /** Nome de exibição definido no JSON da skill (ou o caminho do id, se não houver). */
    default String displayName(ResourceLocation skillId) {
        return skillId.getPath();
    }

    List<ResourceLocation> prerequisites(ResourceLocation skillId);

    boolean isUnlocked(ServerPlayer player, ResourceLocation skillId);

    boolean isActive(ServerPlayer player, ResourceLocation skillId);

    /** Desbloqueia no nível 1 e sincroniza com o cliente. */
    boolean unlock(ServerPlayer player, ResourceLocation skillId);

    /** Ativa/desativa (removendo os efeitos ao desativar) e sincroniza com o cliente. */
    void setActive(ServerPlayer player, ResourceLocation skillId, boolean active);

    /** Reenvia ao cliente os dados de skill do jogador (o painel do SkillLib fica desatualizado após renascer). */
    default void resync(ServerPlayer player) {
    }

    /** Ainda cabe mais uma skill ativa (limite do SkillLib)? */
    boolean canActivateMore(ServerPlayer player);

    /** Sem SkillLib instalado: nada é desbloqueável. */
    SkillBackend NONE = new SkillBackend() {
        @Override
        public boolean available() {
            return false;
        }

        @Override
        public boolean skillExists(ResourceLocation skillId) {
            return false;
        }

        @Override
        public List<ResourceLocation> prerequisites(ResourceLocation skillId) {
            return List.of();
        }

        @Override
        public boolean isUnlocked(ServerPlayer player, ResourceLocation skillId) {
            return false;
        }

        @Override
        public boolean isActive(ServerPlayer player, ResourceLocation skillId) {
            return false;
        }

        @Override
        public boolean unlock(ServerPlayer player, ResourceLocation skillId) {
            return false;
        }

        @Override
        public void setActive(ServerPlayer player, ResourceLocation skillId, boolean active) {
        }

        @Override
        public boolean canActivateMore(ServerPlayer player) {
            return false;
        }
    };
}
