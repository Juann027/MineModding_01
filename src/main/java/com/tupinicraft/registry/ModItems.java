package com.tupinicraft.registry;

import com.tupinicraft.TupiniCraft;
import com.tupinicraft.needs.ChamberPotItem;
import com.tupinicraft.needs.ConsumableItem;
import com.tupinicraft.needs.NeedsDelta;
import java.util.List;
import net.minecraft.world.food.FoodProperties;
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


    // Consumiveis das necessidades (unidades: 100 = 1%).
    public static final DeferredItem<ConsumableItem> AGUA_DE_COCO = ITEMS.register("agua_de_coco",
            () -> new ConsumableItem(new Item.Properties().stacksTo(16), new NeedsDelta(4_000, 0, 1_000, 0, 300), true));
    public static final DeferredItem<ConsumableItem> CAFEZINHO = ITEMS.register("cafezinho",
            () -> new ConsumableItem(new Item.Properties().stacksTo(16), new NeedsDelta(500, 3_500, 1_200, 0, 500), true));
    public static final DeferredItem<ConsumableItem> GUARANA = ITEMS.register("guarana",
            () -> new ConsumableItem(new Item.Properties().stacksTo(16), new NeedsDelta(2_500, 2_000, 1_000, 0, 600), true));
    public static final DeferredItem<ConsumableItem> PAO_DE_QUEIJO = ITEMS.register("pao_de_queijo",
            () -> new ConsumableItem(new Item.Properties().food(new FoodProperties.Builder().nutrition(4)
                    .saturationModifier(0.5F).build()), new NeedsDelta(0, 300, 0, 800, 400), false));
    public static final DeferredItem<ConsumableItem> COXINHA = ITEMS.register("coxinha",
            () -> new ConsumableItem(new Item.Properties().food(new FoodProperties.Builder().nutrition(6)
                    .saturationModifier(0.6F).build()), new NeedsDelta(0, 0, 0, 1_500, 600), false));
    public static final DeferredItem<ConsumableItem> BRIGADEIRO = ITEMS.register("brigadeiro",
            () -> new ConsumableItem(new Item.Properties().food(new FoodProperties.Builder().nutrition(2)
                    .saturationModifier(0.2F).alwaysEdible().fast().build()), new NeedsDelta(0, 500, 0, 300, 1_500), false));
    public static final DeferredItem<ChamberPotItem> PENICO = ITEMS.register("penico",
            () -> new ChamberPotItem(new Item.Properties().durability(64)));

    public static List<DeferredItem<? extends Item>> consumables() {
        return List.of(AGUA_DE_COCO, CAFEZINHO, GUARANA, PAO_DE_QUEIJO, COXINHA, BRIGADEIRO, PENICO);
    }

    private ModItems() {}

    public static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(CABO_ELEITORAL_SPAWN_EGG);
            event.accept(LEAO_DO_FIM_SPAWN_EGG);
        }
        if (event.getTabKey() == CreativeModeTabs.FOOD_AND_DRINKS) {
            for (DeferredItem<? extends Item> item : consumables()) event.accept(item);
        }
    }
}
