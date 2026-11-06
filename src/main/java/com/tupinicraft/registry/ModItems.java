package com.tupinicraft.registry;

import com.tupinicraft.TupiniCraft;
import com.tupinicraft.election.UrnaEletronicaItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(TupiniCraft.MOD_ID);

    public static final DeferredItem<UrnaEletronicaItem> URNA_ELETRONICA = ITEMS.register(
            "urna_eletronica", () -> new UrnaEletronicaItem(new Item.Properties().stacksTo(1)));

    private ModItems() {
    }

    public static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(URNA_ELETRONICA);
        }
    }
}
