package com.tupinicraft.registry;

import com.tupinicraft.TupiniCraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Aba enxuta com os mobs disponíveis; os assets inativos ficam preservados. */
public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, TupiniCraft.MOD_ID);
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TUPINICRAFT = TABS.register(
            "tupinicraft", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.tupinicraft"))
                    .icon(() -> ModItems.LEAO_DO_FIM_SPAWN_EGG.get().getDefaultInstance())
                    .withTabsBefore(CreativeModeTabs.TOOLS_AND_UTILITIES)
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.CABO_ELEITORAL_SPAWN_EGG.get());
                        output.accept(ModItems.LEAO_DO_FIM_SPAWN_EGG.get());
                        for (var item : ModItems.consumables()) output.accept(item.get());
                    }).build());
    private ModCreativeTabs() {}
}
