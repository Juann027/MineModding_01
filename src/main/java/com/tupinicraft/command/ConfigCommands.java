package com.tupinicraft.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.tupinicraft.config.ConfigKey;
import com.tupinicraft.config.ConfigKeys;
import com.tupinicraft.config.ServerSettings;
import com.tupinicraft.player.PoliticalDataService;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;

import java.util.Optional;

/**
 * /brasil admin config list [prefixo] | get &lt;chave&gt; | set &lt;chave&gt; &lt;valor&gt; | reset &lt;chave&gt;
 * (já protegido pela permissão de operador do nó /brasil admin).
 * <p>
 * {@code set} grava um override persistente no mundo; {@code reset} remove o override e volta ao valor do TOML.
 * Mudanças valem para as próximas eleições e os próximos mandatos; eventos em andamento mantêm seus prazos.
 */
final class ConfigCommands {
    private static final SuggestionProvider<CommandSourceStack> KEY_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(ConfigKeys.all().stream().map(ConfigKey::path), builder);

    private ConfigCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("config")
                .then(Commands.literal("list")
                        .executes(context -> list(context, ""))
                        .then(Commands.argument("prefixo", StringArgumentType.word())
                                .executes(context -> list(context, StringArgumentType.getString(context, "prefixo")))))
                .then(Commands.literal("get")
                        .then(Commands.argument("chave", StringArgumentType.word())
                                .suggests(KEY_SUGGESTIONS)
                                .executes(ConfigCommands::get)))
                .then(Commands.literal("set")
                        .then(Commands.argument("chave", StringArgumentType.word())
                                .suggests(KEY_SUGGESTIONS)
                                .then(Commands.argument("valor", StringArgumentType.word())
                                        .executes(ConfigCommands::set))))
                .then(Commands.literal("reset")
                        .then(Commands.argument("chave", StringArgumentType.word())
                                .suggests(KEY_SUGGESTIONS)
                                .executes(ConfigCommands::reset)));
    }

    private static int list(CommandContext<CommandSourceStack> context, String prefix) {
        MinecraftServer server = context.getSource().getServer();
        int count = 0;
        for (ConfigKey key : ConfigKeys.all()) {
            if (key.path().startsWith(prefix)) {
                context.getSource().sendSuccess(() -> describe(server, key), false);
                count++;
            }
        }
        if (count == 0) {
            context.getSource().sendFailure(Component.literal("Nenhuma chave começa com \"" + prefix + "\"."));
        }
        return count;
    }

    private static int get(CommandContext<CommandSourceStack> context) {
        Optional<ConfigKey> key = resolve(context);
        if (key.isEmpty()) {
            return 0;
        }
        context.getSource().sendSuccess(() -> describe(context.getSource().getServer(), key.get())
                .append(Component.literal("\n  " + key.get().comment() + " (" + key.get().describeRange() + ")")
                        .withStyle(ChatFormatting.GRAY)), false);
        return 1;
    }

    private static int set(CommandContext<CommandSourceStack> context) {
        Optional<ConfigKey> key = resolve(context);
        if (key.isEmpty()) {
            return 0;
        }
        MinecraftServer server = context.getSource().getServer();
        String before = key.get().format(ServerSettings.raw(server, key.get()));
        Optional<String> error = ServerSettings.set(server, key.get(), StringArgumentType.getString(context, "valor"));
        if (error.isPresent()) {
            context.getSource().sendFailure(Component.literal(error.get()));
            return 0;
        }
        String after = key.get().format(ServerSettings.raw(server, key.get()));
        PoliticalDataService.syncAll(server);
        context.getSource().sendSuccess(() -> Component.literal(key.get().path() + ": " + before + " → " + after
                + " (override do mundo; TOML: " + key.get().format(ServerSettings.tomlValue(key.get())) + ")")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> context) {
        Optional<ConfigKey> key = resolve(context);
        if (key.isEmpty()) {
            return 0;
        }
        MinecraftServer server = context.getSource().getServer();
        if (!ServerSettings.reset(server, key.get())) {
            context.getSource().sendFailure(Component.literal(key.get().path() + " não tem override; já usa o valor do TOML ("
                    + key.get().format(ServerSettings.tomlValue(key.get())) + ")."));
            return 0;
        }
        PoliticalDataService.syncAll(server);
        context.getSource().sendSuccess(() -> Component.literal(key.get().path() + " voltou ao valor do TOML: "
                + key.get().format(ServerSettings.tomlValue(key.get()))).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static Optional<ConfigKey> resolve(CommandContext<CommandSourceStack> context) {
        String path = StringArgumentType.getString(context, "chave");
        Optional<ConfigKey> key = ConfigKeys.byPath(path);
        if (key.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Chave desconhecida: " + path
                    + ". Use /brasil admin config list para ver as chaves."));
        }
        return key;
    }

    private static MutableComponent describe(MinecraftServer server, ConfigKey key) {
        boolean overridden = ServerSettings.override(server, key).isPresent();
        return Component.literal(key.path() + " = ").withStyle(ChatFormatting.WHITE)
                .append(Component.literal(key.format(ServerSettings.raw(server, key))).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(overridden
                        ? " [override; TOML: " + key.format(ServerSettings.tomlValue(key)) + "]"
                        : " [TOML]").withStyle(overridden ? ChatFormatting.AQUA : ChatFormatting.GRAY));
    }
}
