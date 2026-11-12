package com.tupinicraft.registry;

import com.tupinicraft.TupiniCraft;
import com.tupinicraft.entity.CaboEleitoralEntity;
import com.tupinicraft.entity.LeaoDoFimEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Tipos de entidade (código comum). Renderers ficam em client.render. */
public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, TupiniCraft.MOD_ID);

    /** Mob de teste da integração GeckoLib + SmartBrainLib. */
    public static final DeferredHolder<EntityType<?>, EntityType<CaboEleitoralEntity>> CABO_ELEITORAL = ENTITY_TYPES.register(
            "cabo_eleitoral",
            () -> EntityType.Builder.of(CaboEleitoralEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(8)
                    .build("cabo_eleitoral"));

    /** LeãoDoFim: neutral validation mob; no natural spawning yet. */
    public static final DeferredHolder<EntityType<?>, EntityType<LeaoDoFimEntity>> LEAO_DO_FIM = ENTITY_TYPES.register(
            "leao_do_fim",
            () -> EntityType.Builder.of(LeaoDoFimEntity::new, MobCategory.CREATURE)
                    .sized(1.4F, 1.7F)
                    .clientTrackingRange(10)
                    .build("leao_do_fim"));

    private ModEntities() {
    }

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(CABO_ELEITORAL.get(), CaboEleitoralEntity.createAttributes().build());
        event.put(LEAO_DO_FIM.get(), LeaoDoFimEntity.createAttributes().build());
    }
}
