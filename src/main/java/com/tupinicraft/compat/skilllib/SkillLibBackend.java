package com.tupinicraft.compat.skilllib;

import cn.skilllib.Config;
import cn.skilllib.api.SkillLibAPI;
import cn.skilllib.api.skill.Skill;
import cn.skilllib.api.skill.SkillEffect;
import cn.skilllib.data.PlayerSkillData;
import cn.skilllib.data.SkillDataManager;
import cn.skilllib.registry.ModAttachments;
import cn.skilllib.registry.ModNetwork;
import com.tupinicraft.skilltree.SkillBackend;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Implementação sobre a API pública do SkillLib 1.4.0 (SkillLibAPI, PlayerSkillData, SkillDataManager,
 * ModNetwork.syncSkillData). Só carregada quando o mod "skilllib" está presente (ver SkillBackends).
 * Não copia classes do SkillLib; os dados de skill continuam no attachment dele.
 */
public final class SkillLibBackend implements SkillBackend {
    @Override
    public boolean available() {
        return true;
    }

    @Override
    public boolean skillExists(ResourceLocation skillId) {
        return SkillDataManager.getInstance().getSkill(skillId) != null;
    }

    @Override
    public List<ResourceLocation> prerequisites(ResourceLocation skillId) {
        Skill skill = SkillDataManager.getInstance().getSkill(skillId);
        return skill == null ? List.of() : List.copyOf(skill.getPrerequisites());
    }

    @Override
    public boolean isUnlocked(ServerPlayer player, ResourceLocation skillId) {
        return SkillLibAPI.isSkillUnlocked(player, skillId);
    }

    @Override
    public boolean isActive(ServerPlayer player, ResourceLocation skillId) {
        return SkillLibAPI.isSkillActive(player, skillId);
    }

    @Override
    public boolean unlock(ServerPlayer player, ResourceLocation skillId) {
        boolean unlocked = SkillLibAPI.unlockSkill(player, skillId, 1);
        if (unlocked) {
            ModNetwork.syncSkillData(player);
        }
        return unlocked;
    }

    @Override
    public void setActive(ServerPlayer player, ResourceLocation skillId, boolean active) {
        PlayerSkillData data = player.getData(ModAttachments.PLAYER_SKILL_DATA);
        if (data.isActive(skillId) == active) {
            return;
        }
        data.setActive(skillId, active);
        if (!active) {
            Skill skill = SkillDataManager.getInstance().getSkill(skillId);
            if (skill != null) {
                for (SkillEffect effect : skill.getEffects()) {
                    effect.remove(player);
                }
            }
        }
        ModNetwork.syncSkillData(player);
    }

    @Override
    public boolean canActivateMore(ServerPlayer player) {
        int max = Config.maxActiveSkills;
        return max <= 0 || player.getData(ModAttachments.PLAYER_SKILL_DATA).getActiveCount() < max;
    }
}
