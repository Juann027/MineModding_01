package com.respectcraft.entity.dev;

import com.respectcraft.entity.rig.CustomRigMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * FIXTURE TECNICA (Etapa 4.12), nao e conteudo do RespectCraft: manequim parado associado a uma familia de rig pelo
 * manifesto {@code rigs/entities/<tipo>.json}. Dois tipos com geometrias diferentes ({@code dev_rig_mannequin_a} alto
 * e fino, {@code dev_rig_mannequin_b} baixo e largo, com casca/nucleo e folha extra) provam o compartilhamento de
 * animacoes da familia. Sem spawn natural, sem ovo, sem drop, sem XP; so {@code /summon} ou {@code /respectcraft rig}.
 * Controladores e animacoes vem de {@link CustomRigMob}.
 */
public class RigMannequinEntity extends CustomRigMob {
    public RigMannequinEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.MOVEMENT_SPEED, 0.2);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(2, new RandomLookAroundGoal(this));
    }

    @Override
    public boolean removeWhenFarAway(double distance) { return false; }

    @Override
    protected boolean shouldDropLoot() { return false; }
}
