package com.tupinicraft;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.block.model.ItemModelGenerator;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceMetadata;

import java.nio.file.Files;
import java.nio.file.Path;

/** Lê os PNGs com o próprio Minecraft e gera a geometria dos sprites sem exigir janela/OpenGL. */
public final class IconTextureSelfTest {
    public static void run() {
        Path textures = Path.of("src/main/resources/assets/tupinicraft/textures/item");
        int count = 0;
        ItemModelGenerator generator = new ItemModelGenerator();
        try (var files = Files.walk(textures)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".png")).sorted().toList()) {
                String relative = textures.relativize(file).toString().replace('\\', '/').replace(".png", "");
                try (var stream = Files.newInputStream(file)) {
                    NativeImage image = NativeImage.read(stream);
                    try (SpriteContents sprite = new SpriteContents(ResourceLocation.fromNamespaceAndPath("tupinicraft", "item/" + relative),
                            new FrameSize(image.getWidth(), image.getHeight()), image, ResourceMetadata.EMPTY)) {
                        if (generator.processFrames(0, "layer0", sprite).size() <= 1) {
                            throw new AssertionError("Textura não gera bordas visíveis: " + relative);
                        }
                        count++;
                    }
                }
            }
        } catch (java.io.IOException error) {
            throw new AssertionError("Não foi possível ler os sprites dos itens", error);
        }
        if (count != 34) throw new AssertionError("Esperadas 34 texturas próprias de itens; obtidas " + count);
        System.out.println("PASS Minecraft: 34 PNGs lidos e convertidos em geometria de item visível");
    }
}
