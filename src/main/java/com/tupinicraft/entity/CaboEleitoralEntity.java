package com.tupinicraft.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.tslat.smartbrainlib.api.SmartBrainOwner;
import net.tslat.smartbrainlib.api.core.BrainActivityGroup;
import net.tslat.smartbrainlib.api.core.SmartBrainProvider;
import net.tslat.smartbrainlib.api.core.behaviour.FirstApplicableBehaviour;
import net.tslat.smartbrainlib.api.core.behaviour.OneRandomBehaviour;
import net.tslat.smartbrainlib.api.core.behaviour.custom.attack.AnimatableMeleeAttack;
import net.tslat.smartbrainlib.api.core.behaviour.custom.look.LookAtTarget;
import net.tslat.smartbrainlib.api.core.behaviour.custom.misc.Idle;
import net.tslat.smartbrainlib.api.core.behaviour.custom.move.MoveToWalkTarget;
import net.tslat.smartbrainlib.api.core.behaviour.custom.path.SetRandomWalkTarget;
import net.tslat.smartbrainlib.api.core.behaviour.custom.path.SetWalkTargetToAttackTarget;
import net.tslat.smartbrainlib.api.core.behaviour.custom.target.InvalidateAttackTarget;
import net.tslat.smartbrainlib.api.core.behaviour.custom.target.SetPlayerLookTarget;
import net.tslat.smartbrainlib.api.core.behaviour.custom.target.SetRandomLookTarget;
import net.tslat.smartbrainlib.api.core.behaviour.custom.target.TargetOrRetaliate;
import net.tslat.smartbrainlib.api.core.sensor.ExtendedSensor;
import net.tslat.smartbrainlib.api.core.sensor.vanilla.HurtBySensor;
import net.tslat.smartbrainlib.api.core.sensor.vanilla.NearbyLivingEntitySensor;
import net.tslat.smartbrainlib.api.core.sensor.vanilla.NearbyPlayersSensor;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;

/**
 * Mob de TESTE da integração GeckoLib + SmartBrainLib ("Cabo Eleitoral"). Não nasce naturalmente;
 * use o ovo do modo criativo ou /summon tupinicraft:cabo_eleitoral.
 * <ul>
 *   <li>Servidor: IA pelo SmartBrainLib (patrulha, olha para jogadores, revida e ataca corpo a corpo),
 *       dano e alvo são decididos aqui.</li>
 *   <li>GeckoLib: só animação (idle/andar em loop; ataque disparado pelo servidor e sincronizado).</li>
 *   <li>Cliente: o renderer fica em {@code client.render}, nunca referenciado por esta classe.</li>
 * </ul>
 */
public class CaboEleitoralEntity extends PathfinderMob implements GeoEntity, SmartBrainOwner<CaboEleitoralEntity> {
    public static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.cabo_eleitoral.idle");
    public static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.cabo_eleitoral.walk");
    public static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("animation.cabo_eleitoral.attack");
    private static final String ATTACK_CONTROLLER = "ataque";

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    public CaboEleitoralEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.ATTACK_DAMAGE, 2.0)
                .add(Attributes.FOLLOW_RANGE, 16.0);
    }

    // ------------------------------------------------------------------ SmartBrainLib (servidor)

    @Override
    protected Brain.Provider<?> brainProvider() {
        return new SmartBrainProvider<>(this);
    }

    @Override
    protected void customServerAiStep() {
        tickBrain(this);
    }

    @Override
    public List<? extends ExtendedSensor<? extends CaboEleitoralEntity>> getSensors() {
        return List.of(new NearbyPlayersSensor<>(), new NearbyLivingEntitySensor<>(), new HurtBySensor<>());
    }

    @Override
    public BrainActivityGroup<? extends CaboEleitoralEntity> getCoreTasks() {
        return BrainActivityGroup.coreTasks(new LookAtTarget<>(), new MoveToWalkTarget<>());
    }

    @Override
    public BrainActivityGroup<? extends CaboEleitoralEntity> getIdleTasks() {
        return BrainActivityGroup.idleTasks(
                // Só revida: ataca quem o atacou (não persegue jogadores à toa).
                new FirstApplicableBehaviour<CaboEleitoralEntity>(
                        new TargetOrRetaliate<CaboEleitoralEntity>().attackablePredicate(target -> target == getLastHurtByMob()),
                        new SetPlayerLookTarget<>(),
                        new SetRandomLookTarget<>()),
                new OneRandomBehaviour<CaboEleitoralEntity>(
                        new SetRandomWalkTarget<>(),
                        new Idle<CaboEleitoralEntity>().runForBetween(30, 60)));
    }

    @Override
    public BrainActivityGroup<? extends CaboEleitoralEntity> getFightTasks() {
        return BrainActivityGroup.fightTasks(
                new InvalidateAttackTarget<>(),
                new SetWalkTargetToAttackTarget<>(),
                new AnimatableMeleeAttack<>(6));
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit) {
            triggerAnim(ATTACK_CONTROLLER, "golpe"); // servidor dispara; GeckoLib sincroniza com os clientes
        }
        return hit;
    }

    // ------------------------------------------------------------------ GeckoLib (animação)

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movimento", 5,
                state -> state.setAndContinue(state.isMoving() ? WALK : IDLE)));
        controllers.add(new AnimationController<>(this, ATTACK_CONTROLLER, 0, state -> PlayState.STOP)
                .triggerableAnim("golpe", ATTACK));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }
}
