package com.respectcraft.registry;

import com.respectcraft.RespectCraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Aba do mod: ovo do Leão do Fim e consumíveis. */
public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, RespectCraft.MOD_ID);
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> RESPECTCRAFT = TABS.register(
            "respectcraft", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.respectcraft"))
                    .icon(() -> ModItems.LEAO_DO_FIM_SPAWN_EGG.get().getDefaultInstance())
                    .withTabsBefore(CreativeModeTabs.TOOLS_AND_UTILITIES)
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.LEAO_DO_FIM_SPAWN_EGG.get());
                        for (var item : ModItems.consumables()) output.accept(item.get());
                    }).build());
    private ModCreativeTabs() {}
}
