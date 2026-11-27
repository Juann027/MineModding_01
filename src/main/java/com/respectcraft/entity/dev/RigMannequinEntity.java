package com.respectcraft.entity.dev;

import com.respectcraft.rig.CustomRigRegistry;
import com.respectcraft.rig.RigBinding;
import com.respectcraft.rig.RigDefinition;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * FIXTURE TECNICA (Etapa 4.12), nao e conteudo do RespectCraft: manequim parado associado a uma familia de rig pelo
 * manifesto {@code rigs/entities/<tipo>.json}. Dois tipos com geometrias diferentes ({@code dev_rig_mannequin_a} alto
 * e fino, {@code dev_rig_mannequin_b} baixo e largo, com casca/nucleo e folha extra) provam o compartilhamento de
 * animacoes da familia. Sem spawn natural, sem ovo, sem drop, sem XP; so {@code /summon} ou {@code /respectcraft rig}.
 *
 * <p>Modelo generico para qualquer mob de rig: UM controlador (o {@code controller} do contrato) com o laco
 * {@code idle} da familia e, como gatilhos, todas as animacoes que o registro diz que esta entidade pode tocar numa
 * cena ({@link CustomRigRegistry#triggerables}). O servidor dispara pelo {@code FinisherTestMechanic}.
 */
public class RigMannequinEntity extends PathfinderMob implements GeoEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

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

    public Optional<RigBinding> binding() {
        return CustomRigRegistry.GLOBAL.binding(BuiltInRegistries.ENTITY_TYPE.getKey(getType()));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        Optional<RigBinding> binding = binding();
        Optional<RigDefinition> rig = binding.flatMap(b -> CustomRigRegistry.GLOBAL.rig(b.rig()));
        if (binding.isEmpty() || rig.isEmpty()) return;
        RawAnimation idle = RawAnimation.begin().thenLoop(rig.get().animationName("idle"));
        boolean hasIdle = rig.get().animations().containsKey("idle");
        AnimationController<RigMannequinEntity> controller = new AnimationController<>(this, rig.get().controller(), 3,
                state -> hasIdle ? state.setAndContinue(idle) : PlayState.STOP);
        for (String name : CustomRigRegistry.GLOBAL.triggerables(binding.get())) {
            controller.triggerableAnim(name, RawAnimation.begin().thenPlay(name));
        }
        controllers.add(controller);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
