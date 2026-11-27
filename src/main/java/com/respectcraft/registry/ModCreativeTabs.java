package com.respectcraft.registry;

import com.respectcraft.RespectCraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Aba do mod: ovos, consumíveis e a Madeira Retorcida (Etapa 4.9). */
public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, RespectCraft.MOD_ID);
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> RESPECTCRAFT = TABS.register(
            "respectcraft", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.respectcraft"))
                    .icon(() -> ModItems.AGUA_VIVA_SPAWN_EGG.get().getDefaultInstance())
                    .withTabsBefore(CreativeModeTabs.TOOLS_AND_UTILITIES)
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.AGUA_VIVA_SPAWN_EGG.get());
                        output.accept(ModItems.AGUA_VIVA_FILHOTE_SPAWN_EGG.get());
                        output.accept(ModItems.STRAWBERRY_SPAWN_EGG.get());
                        for (var item : ModItems.consumables()) output.accept(item.get());
                        for (var item : com.respectcraft.block.wood.TwistedWoodBlocks.CREATIVE_ORDER) output.accept(item.get());
                    }).build());
    private ModCreativeTabs() {}
}
