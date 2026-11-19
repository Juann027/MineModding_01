package com.respectcraft.entity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/** First integration: neutral mob, basic retaliation, no automatic special attacks.
 * Special attacks are visual hooks only; use /leaodofim for validation.
 */
public class LeaoDoFimEntity extends PathfinderMob implements GeoEntity {
    private static final EntityDataAccessor<Boolean> AURA_CHARGING = SynchedEntityData.defineId(
            LeaoDoFimEntity.class, EntityDataSerializers.BOOLEAN);
    private static final String BODY = "body";
    private static final RawAnimation IDLE = loop("idle");
    private static final RawAnimation WALK = loop("walk");
    private static final RawAnimation SIGIL = layer("sigil");
    private static final RawAnimation AURA_IDLE = layer("aura_idle");
    private static final RawAnimation AURA_CHARGE = layer("aura_charge");
    private static final RawAnimation EYES_IDLE = layer("eye_aura_idle");
    private static final RawAnimation EYES_CHARGE = layer("eye_aura_charge");
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    // Server-only action timing. Held charge ends on release/reset or a successful hit.
    private int actionTicks;

    public LeaoDoFimEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 100.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(AURA_CHARGING, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.1, true) {
            @Override public boolean canUse() { return actionTicks == 0 && super.canUse(); }
            @Override public boolean canContinueToUse() { return actionTicks == 0 && super.canContinueToUse(); }
        });
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8) {
            @Override public boolean canUse() { return actionTicks == 0 && super.canUse(); }
            @Override public boolean canContinueToUse() { return actionTicks == 0 && super.canContinueToUse(); }
        });
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && actionTicks > 0 && actionTicks != Integer.MAX_VALUE) {
            if (--actionTicks == 0) setAuraCharging(false);
        }
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        if (!level().isClientSide && actionTicks == 0) playAction("bite");
        // Basic vanilla melee timing. A future attack goal can delay damage to the bite keyframe.
        return super.doHurtTarget(target);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean damaged = super.hurt(source, amount);
        if (damaged && isAlive() && !level().isClientSide) playAction("hit");
        return damaged;
    }

    public boolean isAuraCharging() { return entityData.get(AURA_CHARGING); }

    public void setAuraCharging(boolean charging) {
        if (!level().isClientSide) entityData.set(AURA_CHARGING, charging);
    }

    /** Server-side trigger hooks, synchronized by GeckoLib. Returns false for unknown actions. */
    public boolean playAction(String action) {
        if (level().isClientSide || !isAlive()) return false;
        int duration = switch (action) {
            // Do not stop/restart the melee goal for ordinary attacks: preserve its cooldown.
            case "bite" -> 0;
            case "roar" -> 40;
            case "hit" -> 0;
            case "charge_attack" -> Integer.MAX_VALUE;
            case "release_attack" -> 24;
            case "sigil_charge" -> 60;
            case "sigil_spurt" -> 80;
            default -> -1;
        };
        if (duration < 0) return false;
        actionTicks = duration;
        if (duration > 0) getNavigation().stop();
        setAuraCharging(action.equals("charge_attack") || action.equals("release_attack")
                || action.equals("sigil_charge") || action.equals("sigil_spurt"));
        stopTriggeredAnim(BODY, null);
        triggerAnim(BODY, action);
        return true;
    }

    public void resetAnimationState() {
        if (level().isClientSide) return;
        actionTicks = 0;
        setAuraCharging(false);
        stopTriggeredAnim(BODY, null);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // Overlays first; body/actions last so roar/hit/sigil attacks win on shared bones.
        // The exported layer clips contain only their own bones, not global hide-effect keys.
        controllers.add(new AnimationController<>(this, "sigil", 0, s -> s.setAndContinue(SIGIL)));
        controllers.add(new AnimationController<>(this, "aura", 3,
                s -> s.setAndContinue(isAuraCharging() ? AURA_CHARGE : AURA_IDLE)));
        controllers.add(new AnimationController<>(this, "eyes", 3,
                s -> s.setAndContinue(isAuraCharging() ? EYES_CHARGE : EYES_IDLE)));
        controllers.add(new AnimationController<>(this, BODY, 0,
                s -> s.setAndContinue(s.isMoving() ? WALK : IDLE))
                .triggerableAnim("bite", play("bite"))
                .triggerableAnim("roar", play("roar"))
                .triggerableAnim("hit", play("hit"))
                .triggerableAnim("charge_attack", RawAnimation.begin().thenPlayAndHold("animation.lionxx.charge_attack"))
                .triggerableAnim("release_attack", play("release_attack"))
                .triggerableAnim("sigil_charge", play("sigil_charge"))
                .triggerableAnim("sigil_spurt", play("sigil_spurt")));
    }

    private static RawAnimation loop(String name) { return RawAnimation.begin().thenLoop("animation.lionxx." + name); }
    private static RawAnimation play(String name) { return RawAnimation.begin().thenPlay("animation.lionxx." + name); }
    private static RawAnimation layer(String name) { return RawAnimation.begin().thenLoop("animation.leao_do_fim.layer_" + name); }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return animationCache; }
}
