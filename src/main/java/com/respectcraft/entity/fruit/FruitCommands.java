package com.respectcraft.entity.fruit;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;

/**
 * Diagnostico das frutas (Etapa 4.13.3; permissao 2): {@code /respectcraft fruit spawns} lista, por bioma de referencia,
 * as entradas de spawn de frutas (biome modifiers ja aplicados) e {@code /respectcraft fruit music <alvo> on|off} liga
 * ou corta a musica de uma fruta como o impacto decisivo de uma finalizacao faria (teste de audio sem cena).
 */
public final class FruitCommands {
    /** Biomas conferidos: os dois com spawn e vizinhos que NAO devem ter (sunflower_plains, savanna_plateau...). */
    static final List<String> BIOMES = List.of("plains", "savanna", "sunflower_plains", "savanna_plateau",
            "windswept_savanna", "forest", "desert", "meadow");

    private FruitCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("fruit").requires(s -> s.hasPermission(2))
                .then(Commands.literal("spawns").executes(FruitCommands::spawns))
                .then(Commands.literal("music").then(Commands.argument("entity", EntityArgument.entity())
                        .then(Commands.argument("on", BoolArgumentType.bool()).executes(FruitCommands::music))));
    }

    private static int spawns(CommandContext<CommandSourceStack> c) {
        var biomes = c.getSource().getServer().registryAccess().registryOrThrow(Registries.BIOME);
        StringBuilder sb = new StringBuilder("Fruit spawns (CREATURE):");
        int found = 0;
        for (String name : BIOMES) {
            Biome biome = biomes.get(ResourceKey.create(Registries.BIOME, ResourceLocation.withDefaultNamespace(name)));
            if (biome == null) continue;
            StringBuilder line = new StringBuilder();
            for (var data : biome.getMobSettings().getMobs(MobCategory.CREATURE).unwrap()) {
                ResourceLocation type = BuiltInRegistries.ENTITY_TYPE.getKey(data.type);
                if (!type.getNamespace().equals(com.respectcraft.RespectCraft.MOD_ID)) continue;
                line.append(' ').append(type).append(" weight=").append(data.getWeight().asInt())
                        .append(" group=").append(data.minCount).append('-').append(data.maxCount);
                found++;
            }
            sb.append("\n minecraft:").append(name).append(':').append(line.length() == 0 ? " none" : line);
        }
        String text = sb.toString();
        c.getSource().sendSuccess(() -> Component.literal(text), false);
        return found;
    }

    private static int music(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        if (!(EntityArgument.getEntity(c, "entity") instanceof FruitMob fruit)) {
            c.getSource().sendFailure(Component.literal("Target is not a fruit."));
            return 0;
        }
        boolean on = BoolArgumentType.getBool(c, "on");
        fruit.setMusicSilenced(!on);
        c.getSource().sendSuccess(() -> Component.literal("Fruit music " + (on ? "on" : "cut") + " for " + fruit.getUUID()), false);
        return 1;
    }
}
