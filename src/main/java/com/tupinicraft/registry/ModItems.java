package com.tupinicraft.registry;

import com.tupinicraft.TupiniCraft;
import com.tupinicraft.election.UrnaEletronicaItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(TupiniCraft.MOD_ID);

    public static final DeferredItem<UrnaEletronicaItem> URNA_ELETRONICA = ITEMS.register(
            "urna_eletronica", () -> new UrnaEletronicaItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<net.minecraft.world.item.BlockItem> TIGRINHO_MACHINE = ITEMS.register(
            "tigrinho_machine", () -> new net.minecraft.world.item.BlockItem(ModBlocks.TIGRINHO_MACHINE.get(), new Item.Properties()));

    /** Ovo do mob de teste (só no modo criativo). */
    public static final DeferredItem<DeferredSpawnEggItem> CABO_ELEITORAL_SPAWN_EGG = ITEMS.register(
            "cabo_eleitoral_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.CABO_ELEITORAL, 0x009C3B, 0xFFDF00, new Item.Properties()));

    public static final DeferredItem<DeferredSpawnEggItem> LEAO_DO_FIM_SPAWN_EGG = ITEMS.register(
            "leao_do_fim_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.LEAO_DO_FIM, 0xB87521, 0x6236D9, new Item.Properties()));

    /** Ícones usados pelo UI Lib nas árvores e nos nós e disponíveis na aba TupiniCraft. */
    static {
        for (String icon : java.util.List.of(
                "cristo_redentor",
                "mao_em_l",
                "rejeicao",
                "policia_federal",
                "vacina",
                "moeda_real",
                "receita_federal",
                "chimarrao",
                "bandeira_brasil",
                "caveira",
                "favela",
                "estadio",
                "amazonia",
                "tigrinho",
                "pix",
                "vira_lata",
                "trevo",
                "comprimido",
                "mito",
                "identidade_dinheiro",
                "niobio",
                "picanha",
                "jumento",
                "cus",
                "bolsa",
                "constituicao",
                "carteira_trabalho",
                "montanha",
                "cedulas",
                "mst",
                "intelectual",
                "painho",
                "sertao")) {
            ITEMS.register("icone_" + icon, () -> icon.equals("tigrinho") ? new com.tupinicraft.tigrinho.TigrinhoItem(new Item.Properties()) : new Item(new Item.Properties()));
        }
    }

    private ModItems() {
    }

    public static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(URNA_ELETRONICA);
        }
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(CABO_ELEITORAL_SPAWN_EGG);
            event.accept(LEAO_DO_FIM_SPAWN_EGG);
        }
    }
}
