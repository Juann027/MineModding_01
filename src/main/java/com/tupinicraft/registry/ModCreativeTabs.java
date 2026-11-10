package com.tupinicraft.registry;

import com.tupinicraft.TupiniCraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Aba própria com todos os itens registrados pelo mod, inclusive os ícones das árvores. */
public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, TupiniCraft.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TUPINICRAFT = TABS.register(
            "tupinicraft", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.tupinicraft"))
                    .icon(() -> ModItems.URNA_ELETRONICA.get().getDefaultInstance())
                    .withTabsBefore(CreativeModeTabs.TOOLS_AND_UTILITIES)
                    .displayItems((parameters, output) ->
                            ModItems.ITEMS.getEntries().forEach(entry -> output.accept(entry.get())))
                    .build());

    private ModCreativeTabs() {
    }
}
