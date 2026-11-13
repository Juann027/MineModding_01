package com.tupinicraft.registry;

import com.tupinicraft.TupiniCraft;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(TupiniCraft.MOD_ID);
    public static final DeferredItem<DeferredSpawnEggItem> CABO_ELEITORAL_SPAWN_EGG = ITEMS.register(
            "cabo_eleitoral_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.CABO_ELEITORAL, 0x009C3B, 0xFFDF00, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> LEAO_DO_FIM_SPAWN_EGG = ITEMS.register(
            "leao_do_fim_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.LEAO_DO_FIM, 0xB87521, 0x6236D9, new Item.Properties()));

    private ModItems() {}

    public static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(CABO_ELEITORAL_SPAWN_EGG);
            event.accept(LEAO_DO_FIM_SPAWN_EGG);
        }
    }
}
