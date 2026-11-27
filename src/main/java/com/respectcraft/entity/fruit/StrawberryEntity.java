package com.respectcraft.entity.fruit;

import com.respectcraft.entity.rig.CustomRigMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Morango antropomorfico (Etapa 4.13.1): primeiro mob de gameplay da familia {@code respectcraft:fruit_humanoid_v1},
 * integrado a partir de assets externos (Blockbench) sem infraestrutura propria: modelo, textura, partes e sockets vem
 * do binding {@code rigs/entities/strawberry.json}, animacoes da familia, renderer generico {@code RigGeoRenderer}.
 *
 * <p>Papel: VITIMA de finalizacoes do jogador (nao executa finalizacoes). Comportamento basico e PROVISORIO para
 * testes: passeia, olha para jogadores e foge quando ferido. Sem ataque, sem spawn natural, sem drop e sem XP.
 */
public class StrawberryEntity extends CustomRigMob {
    public StrawberryEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.xpReward = 0;
    }

    /** Atributos provisorios de teste (ajustar quando o papel do morango no jogo for definido). */
    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 12.0)
                .add(Attributes.MOVEMENT_SPEED, 0.23)
                .add(Attributes.FOLLOW_RANGE, 16.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new PanicGoal(this, 1.4));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    @Override
    public boolean removeWhenFarAway(double distance) { return false; }

    @Override
    protected boolean shouldDropLoot() { return false; }
}
