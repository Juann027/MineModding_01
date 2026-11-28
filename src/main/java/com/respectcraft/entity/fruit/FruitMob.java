package com.respectcraft.entity.fruit;

import com.respectcraft.entity.rig.CustomRigMob;
import com.respectcraft.testgame.FinisherTestMechanic;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * Base comum das frutas humanoides de gameplay (Etapa 4.13.3): morango hoje; banana, melancia e abacaxi no futuro so
 * estendem esta classe (atributos, som e binding proprios). Junta o que toda fruta tem:
 *
 * <ul>
 *   <li><b>IA neutra</b>: passeia, olha para jogadores, nunca procura briga; revida quem a fere
 *       ({@link HurtByTargetGoal} + {@link MeleeAttackGoal}) e larga o alvo quando o perde (alcance/visao do vanilla).
 *       Durante uma finalizacao os golpes e a perseguicao ficam desligados (a sessao segura posicao e navegacao).</li>
 *   <li><b>Musica propria</b> ({@link #musicSound()}): tocada SO no cliente, em loop, presa a entidade
 *       ({@code client.fruit.FruitMusicClient}). O servidor so decide o corte seco no impacto decisivo de uma
 *       finalizacao ({@link #setMusicSilenced}); o flag e dado sincronizado da entidade (vai a quem rastreia, nunca e
 *       gravado no save).</li>
 *   <li><b>Spawn/despawn</b>: nascidas pelo mundo (spawn natural, geracao de chunk) somem longe dos jogadores como
 *       monstros (sem acumulo); ovo, {@code /summon} e afins ficam persistentes (testes). Nome/guia seguem o vanilla.</li>
 * </ul>
 */
public abstract class FruitMob extends CustomRigMob {
    private static final EntityDataAccessor<Boolean> MUSIC_SILENCED =
            SynchedEntityData.defineId(FruitMob.class, EntityDataSerializers.BOOLEAN);

    protected FruitMob(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    /** Som em loop desta fruta (null = sem musica). */
    public abstract SoundEvent musicSound();

    /**
     * Suco, sementes e lascas da fruta nos golpes/quedas de uma finalizacao (desenhados por {@code fx.FinisherFx} com
     * particulas vanilla; o futuro framework de fluidos fica no Calamidade).
     *
     * @param rgb   cor do suco
     * @param seeds item cujas lascas fazem as sementes
     * @param rind  item cujas lascas fazem os pedacos de casca/polpa (estouro)
     */
    public record Juice(int rgb, net.minecraft.world.item.Item seeds, net.minecraft.world.item.Item rind) {}

    public abstract Juice juice();

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(MUSIC_SILENCED, false);
    }

    public boolean musicSilenced() { return entityData.get(MUSIC_SILENCED); }

    /** Servidor: corta (true) ou devolve (false) a musica para todos que veem a fruta. */
    public void setMusicSilenced(boolean silenced) {
        if (!level().isClientSide()) entityData.set(MUSIC_SILENCED, silenced);
    }

    /** Numa cena de finalizacao agora (servidor). */
    protected boolean inFinisher() { return !level().isClientSide() && FinisherTestMechanic.inSession(this); }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2, false) {
            @Override public boolean canUse() { return !inFinisher() && super.canUse(); }
            @Override public boolean canContinueToUse() { return !inFinisher() && super.canContinueToUse(); }
        });
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8) {
            @Override public boolean canUse() { return !inFinisher() && super.canUse(); }
        });
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        return !inFinisher() && super.doHurtTarget(target);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType,
                                        SpawnGroupData groupData) {
        if (!naturalSpawn(spawnType)) setPersistenceRequired();
        return super.finalizeSpawn(level, difficulty, spawnType, groupData);
    }

    /** Nascida pelo proprio mundo (pode sumir longe dos jogadores). */
    public static boolean naturalSpawn(MobSpawnType type) {
        return type == MobSpawnType.NATURAL || type == MobSpawnType.CHUNK_GENERATION;
    }

    @Override
    public boolean removeWhenFarAway(double distance) { return !inFinisher(); }

    @Override
    protected boolean shouldDropLoot() { return false; }

    /** Regra de spawn natural: chao de grama (como animais) com luz. */
    public static boolean checkFruitSpawnRules(EntityType<? extends FruitMob> type, LevelAccessor level, MobSpawnType spawnType,
                                               BlockPos pos, RandomSource random) {
        return level.getBlockState(pos.below()).is(BlockTags.ANIMALS_SPAWNABLE_ON) && level.getRawBrightness(pos, 0) > 8;
    }
}
