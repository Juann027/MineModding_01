package com.respectcraft.entity.fruit;

import com.respectcraft.registry.ModSounds;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/**
 * Morango antropomorfico: primeiro mob de gameplay da familia {@code respectcraft:fruit_humanoid_v1} (assets externos
 * da Etapa 4.13.1; modelo, textura, partes e sockets pelo binding {@code rigs/entities/strawberry.json}).
 *
 * <p>Etapa 4.13.3: NPC neutro de gameplay ({@link FruitMob}: revida, musica propria), spawn natural so em
 * {@code minecraft:plains} e {@code minecraft:savanna} (biome modifier), 3-5 de XP, sem drops. Vitima das cinco
 * finalizacoes exclusivas ({@code finisher.FruitFinishers}); nunca executor.
 */
public class StrawberryEntity extends FruitMob {
    public StrawberryEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 12.0)
                .add(Attributes.ATTACK_DAMAGE, 2.0)
                .add(Attributes.MOVEMENT_SPEED, 0.23)
                .add(Attributes.FOLLOW_RANGE, 16.0);
    }

    @Override
    public SoundEvent musicSound() { return ModSounds.STRAWBERRY_MUSIC.get(); }

    private static final Juice JUICE = new Juice(0xC8102E, net.minecraft.world.item.Items.PUMPKIN_SEEDS,
            net.minecraft.world.item.Items.SWEET_BERRIES);

    @Override
    public Juice juice() { return JUICE; }

    /** 3 a 5 de XP (morte por jogador, inclusive pela execucao de uma finalizacao: regra vanilla). */
    @Override
    protected int getBaseExperienceReward() { return 3 + random.nextInt(3); }
}
