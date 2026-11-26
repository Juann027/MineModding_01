package com.respectcraft.registry;

import com.respectcraft.RespectCraft;
import com.respectcraft.entity.AguaVivaEntity;
import com.respectcraft.entity.dev.RigMannequinEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Tipos de entidade (código comum). Renderers ficam em client.render. */
public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, RespectCraft.MOD_ID);

    /** Etapa 4.9: agua-viva gigante do Inverted Realm (passiva, flutua; sem ataque ainda). */
    public static final DeferredHolder<EntityType<?>, EntityType<AguaVivaEntity>> AGUA_VIVA = ENTITY_TYPES.register(
            "agua_viva",
            () -> EntityType.Builder.<AguaVivaEntity>of(AguaVivaEntity::new, MobCategory.AMBIENT)
                    .sized(4.0F, 8.5F)
                    .clientTrackingRange(10)
                    .build("agua_viva"));

    /** Etapa 4.9: filhote da agua-viva (passivo, em grupos). */
    public static final DeferredHolder<EntityType<?>, EntityType<AguaVivaEntity>> AGUA_VIVA_FILHOTE = ENTITY_TYPES.register(
            "agua_viva_filhote",
            () -> EntityType.Builder.<AguaVivaEntity>of(AguaVivaEntity::new, MobCategory.AMBIENT)
                    .sized(0.8F, 0.9F)
                    .clientTrackingRange(8)
                    .build("agua_viva_filhote"));

    /**
     * Etapa 4.12: FIXTURES TECNICAS do Custom Rig Framework (manequins da familia {@code respectcraft:fruit_humanoid_v1}).
     * MISC (nao some longe do jogador), sem spawn natural, sem ovo e fora da aba criativa: so {@code /summon}.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<RigMannequinEntity>> DEV_RIG_MANNEQUIN_A = ENTITY_TYPES.register(
            "dev_rig_mannequin_a",
            () -> EntityType.Builder.<RigMannequinEntity>of(RigMannequinEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F).clientTrackingRange(8).build("dev_rig_mannequin_a"));

    public static final DeferredHolder<EntityType<?>, EntityType<RigMannequinEntity>> DEV_RIG_MANNEQUIN_B = ENTITY_TYPES.register(
            "dev_rig_mannequin_b",
            () -> EntityType.Builder.<RigMannequinEntity>of(RigMannequinEntity::new, MobCategory.MISC)
                    .sized(0.9F, 1.6F).clientTrackingRange(8).build("dev_rig_mannequin_b"));

    private ModEntities() {
    }

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(AGUA_VIVA.get(), AguaVivaEntity.createAttributes().build());
        event.put(AGUA_VIVA_FILHOTE.get(), AguaVivaEntity.createHatchlingAttributes().build());
        event.put(DEV_RIG_MANNEQUIN_A.get(), RigMannequinEntity.createAttributes().build());
        event.put(DEV_RIG_MANNEQUIN_B.get(), RigMannequinEntity.createAttributes().build());
    }

    /** Agua-vivas surgem no ar livre do bioma twisted_wilds (lista "ambient" do bioma). */
    public static void registerSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        for (var type : java.util.List.of(AGUA_VIVA.get(), AGUA_VIVA_FILHOTE.get())) {
            event.register(type, SpawnPlacementTypes.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    AguaVivaEntity::checkSpawn, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        }
    }
}
