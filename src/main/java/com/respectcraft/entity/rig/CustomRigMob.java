package com.respectcraft.entity.rig;

import com.respectcraft.rig.CustomRigRegistry;
import com.respectcraft.rig.RigBinding;
import com.respectcraft.rig.RigDefinition;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Base comum dos mobs do Custom Rig Framework (Etapa 4.12; extraida na 4.13.1): a entidade associada a uma familia
 * pelo manifesto {@code rigs/entities/<tipo>.json}. UM controlador (o {@code controller} do contrato) com o laco
 * {@code idle} da familia e, como gatilhos, todas as animacoes que o registro diz que esta entidade pode tocar numa
 * cena ({@link CustomRigRegistry#triggerables}). O servidor dispara pelo {@code FinisherTestMechanic}; IA, dano e alvo
 * ficam na subclasse (servidor), o GeckoLib so anima.
 */
public abstract class CustomRigMob extends PathfinderMob implements GeoEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    protected CustomRigMob(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

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
        AnimationController<CustomRigMob> controller = new AnimationController<>(this, rig.get().controller(), 3,
                state -> hasIdle ? state.setAndContinue(idle) : PlayState.STOP);
        for (String name : CustomRigRegistry.GLOBAL.triggerables(binding.get())) {
            controller.triggerableAnim(name, RawAnimation.begin().thenPlay(name));
        }
        controllers.add(controller);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
