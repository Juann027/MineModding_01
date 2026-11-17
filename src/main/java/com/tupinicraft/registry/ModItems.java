package com.tupinicraft.registry;

import com.tupinicraft.TupiniCraft;
import com.tupinicraft.needs.ChamberPotItem;
import com.tupinicraft.needs.ConsumableItem;
import com.tupinicraft.needs.NeedEffectDef;
import com.tupinicraft.needs.NeedEffects;
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


    // Consumiveis das necessidades (efeitos em NeedEffects; unidades: 100 = 1%).
    public static final DeferredItem<ConsumableItem> AGUA_DE_COCO = drink("agua_de_coco", NeedEffects.AGUA_DE_COCO);
    public static final DeferredItem<ConsumableItem> CAFEZINHO = drink("cafezinho", NeedEffects.CAFEZINHO);
    public static final DeferredItem<ConsumableItem> GUARANA = drink("guarana", NeedEffects.GUARANA);
    public static final DeferredItem<ConsumableItem> PAO_DE_QUEIJO = food("pao_de_queijo", NeedEffects.PAO_DE_QUEIJO,
            new FoodProperties.Builder().nutrition(4).saturationModifier(0.5F).build());
    public static final DeferredItem<ConsumableItem> COXINHA = food("coxinha", NeedEffects.COXINHA,
            new FoodProperties.Builder().nutrition(6).saturationModifier(0.6F).build());
    public static final DeferredItem<ConsumableItem> BRIGADEIRO = food("brigadeiro", NeedEffects.BRIGADEIRO,
            new FoodProperties.Builder().nutrition(2).saturationModifier(0.2F).alwaysEdible().fast().build());
    public static final DeferredItem<ChamberPotItem> PENICO = ITEMS.register("penico",
            () -> new ChamberPotItem(new Item.Properties().durability(64)));

    // Etapa 3.1: consumiveis ficticios das necessidades.
    public static final DeferredItem<ConsumableItem> LAXANTE_INDUSTRIAL = pill("laxante_industrial", NeedEffects.LAXANTE_INDUSTRIAL);
    public static final DeferredItem<ConsumableItem> ANTIDIARREICO = pill("antidiarreico", NeedEffects.ANTIDIARREICO);
    public static final DeferredItem<ConsumableItem> CAFE_EXTRAFORTE = drink("cafe_extraforte", NeedEffects.CAFE_EXTRAFORTE);
    public static final DeferredItem<ConsumableItem> CHA_DIURETICO = drink("cha_diuretico", NeedEffects.CHA_DIURETICO);
    public static final DeferredItem<ConsumableItem> CHA_CALMANTE = drink("cha_calmante", NeedEffects.CHA_CALMANTE);
    public static final DeferredItem<ConsumableItem> AFRODISIACO_MISTERIOSO = drink("afrodisiaco_misterioso", NeedEffects.AFRODISIACO_MISTERIOSO);
    public static final DeferredItem<ConsumableItem> PILULA_AZUL = pill("pilula_azul", NeedEffects.PILULA_AZUL);
    public static final DeferredItem<ConsumableItem> ENERGETICO_DO_TIGRINHO = drink("energetico_do_tigrinho", NeedEffects.ENERGETICO_DO_TIGRINHO);

    public static List<DeferredItem<? extends Item>> consumables() {
        return List.of(AGUA_DE_COCO, CAFEZINHO, GUARANA, PAO_DE_QUEIJO, COXINHA, BRIGADEIRO, PENICO,
                LAXANTE_INDUSTRIAL, ANTIDIARREICO, CAFE_EXTRAFORTE, CHA_DIURETICO, CHA_CALMANTE,
                AFRODISIACO_MISTERIOSO, PILULA_AZUL, ENERGETICO_DO_TIGRINHO);
    }

    private static DeferredItem<ConsumableItem> drink(String name, NeedEffectDef effect) {
        return ITEMS.register(name, () -> new ConsumableItem(new Item.Properties().stacksTo(16), effect, ConsumableItem.Kind.DRINK));
    }

    private static DeferredItem<ConsumableItem> pill(String name, NeedEffectDef effect) {
        return ITEMS.register(name, () -> new ConsumableItem(new Item.Properties().stacksTo(16), effect, ConsumableItem.Kind.PILL));
    }

    private static DeferredItem<ConsumableItem> food(String name, NeedEffectDef effect, FoodProperties food) {
        return ITEMS.register(name, () -> new ConsumableItem(new Item.Properties().food(food), effect, ConsumableItem.Kind.FOOD));
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
