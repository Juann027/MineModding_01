package com.respectcraft.entity;

import com.respectcraft.registry.ModEntities;
import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Agua-viva do Inverted Realm (Etapa 4.9, primeira ligacao): criatura passiva que flutua no ar, em dois tamanhos com
 * o mesmo codigo ({@code agua_viva}, gigante, e {@code agua_viva_filhote}). Sem ataque: os clips {@code attack_beam} e
 * {@code grab} do adulto existem nos assets mas ainda nao tem regra de combate (nao invente). IA so no servidor;
 * GeckoLib so anima (idle parado, swim em movimento, hurt e death).
 */
public class AguaVivaEntity extends FlyingMob implements GeoEntity {
    private static final String BODY = "body";
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private final boolean hatchling;
    private final String clipPrefix;

    public AguaVivaEntity(EntityType<? extends FlyingMob> type, Level level) {
        super(type, level);
        this.hatchling = type == ModEntities.AGUA_VIVA_FILHOTE.get();
        this.clipPrefix = hatchling ? "animation.agua_viva_filhote." : "animation.agua_viva.";
        this.moveControl = new FloatMoveControl(this);
        this.xpReward = hatchling ? 1 : 5;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return FlyingMob.createMobAttributes().add(Attributes.MAX_HEALTH, 40.0).add(Attributes.FOLLOW_RANGE, 32.0);
    }

    public static AttributeSupplier.Builder createHatchlingAttributes() {
        return FlyingMob.createMobAttributes().add(Attributes.MAX_HEALTH, 8.0).add(Attributes.FOLLOW_RANGE, 16.0);
    }

    public boolean isHatchling() {
        return hatchling;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(5, new DriftGoal(this));
    }

    /**
     * Regra de surgimento natural (bioma {@code twisted_wilds}, categoria AMBIENT): so no ar livre, em cima do terreno
     * (nunca em cavernas). A IA sobe a criatura depois.
     */
    public static boolean checkSpawn(EntityType<AguaVivaEntity> type, LevelAccessor level, MobSpawnType spawnType,
                                     BlockPos pos, RandomSource random) {
        return level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()
                && pos.getY() >= level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ());
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType,
                                        @Nullable SpawnGroupData groupData) {
        // Ovo e /summon ficam no mundo; os naturais (AMBIENT) somem longe do jogador, como morcegos.
        if (spawnType == MobSpawnType.SPAWN_EGG || spawnType == MobSpawnType.COMMAND) setPersistenceRequired();
        return super.finalizeSpawn(level, difficulty, spawnType, groupData);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean damaged = super.hurt(source, amount);
        if (damaged && isAlive() && !level().isClientSide) triggerAnim(BODY, "hurt");
        return damaged;
    }

    /** O clip de morte (2 s filhote, 3 s adulto) termina antes de a entidade sumir. */
    @Override
    protected void tickDeath() {
        deathTime++;
        if (deathTime >= (hatchling ? 40 : 60) && !level().isClientSide() && !isRemoved()) {
            level().broadcastEntityEvent(this, (byte) 60);
            remove(Entity.RemovalReason.KILLED);
        }
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(hatchling ? 0.5 : 1.5);
    }

    @Override
    public int getAmbientSoundInterval() {
        return 160;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.AMETHYST_BLOCK_CHIME;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.GLOW_SQUID_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.GLOW_SQUID_DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return hatchling ? 0.4F : 1.0F;
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * (hatchling ? 1.4F : 0.7F);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        RawAnimation idle = RawAnimation.begin().thenLoop(clipPrefix + "idle");
        RawAnimation swim = RawAnimation.begin().thenLoop(clipPrefix + "swim");
        RawAnimation death = RawAnimation.begin().thenPlayAndHold(clipPrefix + "death");
        controllers.add(new AnimationController<>(this, BODY, 5, state -> {
            if (isDeadOrDying()) return state.setAndContinue(death);
            // No cliente a posicao ja chega interpolada: o deslocamento do tick diz se esta nadando.
            double moved = position().subtract(xo, yo, zo).lengthSqr();
            return state.setAndContinue(moved > 0.0001 ? swim : idle);
        }).triggerableAnim("hurt", RawAnimation.begin().thenPlay(clipPrefix + "hurt")));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }

    /** Impulsos curtos na direcao do alvo (como o Ghast, mas lento): combina com o pulso do clip swim. */
    static final class FloatMoveControl extends MoveControl {
        private final AguaVivaEntity jelly;
        private int pulseCooldown;

        FloatMoveControl(AguaVivaEntity jelly) {
            super(jelly);
            this.jelly = jelly;
        }

        @Override
        public void tick() {
            if (operation != Operation.MOVE_TO || pulseCooldown-- > 0) return;
            pulseCooldown = 10 + jelly.getRandom().nextInt(10);
            Vec3 delta = new Vec3(wantedX - jelly.getX(), wantedY - jelly.getY(), wantedZ - jelly.getZ());
            double distance = delta.length();
            if (distance < 0.5) {
                operation = Operation.WAIT;
                return;
            }
            Vec3 dir = delta.normalize();
            if (canReach(dir, Mth.ceil(distance))) {
                jelly.setDeltaMovement(jelly.getDeltaMovement().add(dir.scale(jelly.isHatchling() ? 0.06 : 0.04)));
            } else {
                operation = Operation.WAIT;
            }
        }

        private boolean canReach(Vec3 dir, int steps) {
            AABB box = jelly.getBoundingBox();
            for (int i = 1; i < Math.min(steps, 24); i++) {
                box = box.move(dir);
                if (!jelly.level().noCollision(jelly, box)) return false;
            }
            return true;
        }
    }

    /** Escolhe um ponto no ar a ate 12 blocos, numa faixa de altura acima do chao (mais alta para o adulto). */
    static final class DriftGoal extends Goal {
        private final AguaVivaEntity jelly;

        DriftGoal(AguaVivaEntity jelly) {
            this.jelly = jelly;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            MoveControl move = jelly.getMoveControl();
            if (!move.hasWanted()) return true;
            double dx = move.getWantedX() - jelly.getX(), dy = move.getWantedY() - jelly.getY(), dz = move.getWantedZ() - jelly.getZ();
            double d = dx * dx + dy * dy + dz * dz;
            return d < 1.0 || d > 1600.0 || jelly.getRandom().nextInt(200) == 0;
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }

        @Override
        public void start() {
            RandomSource random = jelly.getRandom();
            double x = jelly.getX() + (random.nextFloat() * 2.0F - 1.0F) * 12.0F;
            double z = jelly.getZ() + (random.nextFloat() * 2.0F - 1.0F) * 12.0F;
            int ground = jelly.level().getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
            double low = ground + (jelly.isHatchling() ? 3 : 10);
            double high = ground + (jelly.isHatchling() ? 16 : 28);
            double y = Mth.clamp(jelly.getY() + (random.nextFloat() * 2.0F - 1.0F) * 6.0F, low, high);
            y = Math.min(y, jelly.level().getMaxBuildHeight() - 12);
            jelly.getMoveControl().setWantedPosition(x, y, z, 1.0);
        }
    }
}
