package com.tupinicraft.client.skilltree;

import com.mojang.blaze3d.platform.NativeImage;
import com.tupinicraft.TupiniCraft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

import java.io.IOException;
import java.io.InputStream;

/**
 * Correção de compatibilidade do UI Lib 9.0.0 no Minecraft 1.21.1: os componentes de árvore dele desenham as
 * molduras dos nós e as caixas de texto a partir de {@code minecraft:textures/gui/advancements/widgets.png},
 * arquivo que o Minecraft removeu na 1.20.2 (virou sprites separados). Sem ele, os nós aparecem roxos.
 * <p>
 * Em vez de copiar arte da Mojang para o mod (ou classes do UI Lib), montamos esse arquivo em tempo de execução
 * com os sprites da própria instalação do jogador ({@code textures/gui/sprites/advancements/*.png}), no mesmo
 * layout antigo. Se algum pacote de recursos já fornecer o arquivo, ele é respeitado e nada é feito.
 */
public final class LegacyAdvancementWidgets implements ResourceManagerReloadListener {
    private static final ResourceLocation LEGACY = ResourceLocation.withDefaultNamespace("textures/gui/advancements/widgets.png");
    private static final int SIZE = 256;

    private LegacyAdvancementWidgets() {
    }

    public static void register(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new LegacyAdvancementWidgets());
    }

    @Override
    public void onResourceManagerReload(ResourceManager resources) {
        if (resources.getResource(LEGACY).isPresent()) {
            return;
        }
        NativeImage atlas = new NativeImage(SIZE, SIZE, true);
        try {
            // Layout do widgets.png antigo (até a 1.20.1), lido pelo UI Lib.
            paste(resources, atlas, "box_obtained", 0, 0);
            paste(resources, atlas, "box_unobtained", 0, 26);
            paste(resources, atlas, "title_box", 0, 52);
            paste(resources, atlas, "task_frame_obtained", 0, 128);
            paste(resources, atlas, "challenge_frame_obtained", 26, 128);
            paste(resources, atlas, "goal_frame_obtained", 52, 128);
            paste(resources, atlas, "task_frame_unobtained", 0, 154);
            paste(resources, atlas, "challenge_frame_unobtained", 26, 154);
            paste(resources, atlas, "goal_frame_unobtained", 52, 154);
        } catch (IOException | RuntimeException e) {
            atlas.close();
            TupiniCraft.LOGGER.warn("Não foi possível montar as molduras da árvore de habilidades: {}", e.toString());
            return;
        }
        Minecraft.getInstance().getTextureManager().register(LEGACY, new DynamicTexture(atlas));
    }

    private static void paste(ResourceManager resources, NativeImage atlas, String sprite, int x, int y) throws IOException {
        ResourceLocation location = ResourceLocation.withDefaultNamespace("textures/gui/sprites/advancements/" + sprite + ".png");
        try (InputStream stream = resources.getResourceOrThrow(location).open(); NativeImage image = NativeImage.read(stream)) {
            for (int dy = 0; dy < image.getHeight() && y + dy < SIZE; dy++) {
                for (int dx = 0; dx < image.getWidth() && x + dx < SIZE; dx++) {
                    atlas.setPixelRGBA(x + dx, y + dy, image.getPixelRGBA(dx, dy));
                }
            }
        }
    }
}
