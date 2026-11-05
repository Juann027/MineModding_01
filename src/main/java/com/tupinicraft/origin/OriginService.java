package com.tupinicraft.origin;

import com.tupinicraft.TupiniCraft;
import com.tupinicraft.player.PoliticalData;
import com.tupinicraft.player.PoliticalDataService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Sorteio e atribuição de origens regionais (somente servidor). */
public final class OriginService {
    private OriginService() {
    }

    /**
     * Sorteia uma origem (todas com a mesma chance) se o jogador ainda não tiver uma.
     * A mensagem global só é enviada nesta primeira atribuição.
     */
    public static void assignIfMissing(ServerPlayer player) {
        PoliticalData data = PoliticalDataService.get(player);
        if (data.origin().isPresent()) {
            return;
        }
        RegionalOrigin[] all = RegionalOrigin.values();
        RegionalOrigin origin = all[player.getRandom().nextInt(all.length)];
        PoliticalDataService.set(player, data.withOrigin(origin));
        TupiniCraft.LOGGER.info("Origem sorteada para {}: {}", player.getGameProfile().getName(), origin.code());

        Component message = Component.empty()
                .append(Component.literal("[BRASIL] ").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD))
                .append(Component.literal(player.getGameProfile().getName()).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" é ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(origin.demonym() + "!").withStyle(ChatFormatting.YELLOW));
        player.server.getPlayerList().broadcastSystemMessage(message, false);
    }

    /** Alteração administrativa da origem. Não repete o anúncio global. */
    public static void setOrigin(ServerPlayer player, RegionalOrigin origin) {
        PoliticalDataService.set(player, PoliticalDataService.get(player).withOrigin(origin));
        applyOriginEffects(player);
    }

    /**
     * Ponto de extensão para futuros benefícios por origem/macrorregião.
     * Chamado no login, no respawn e após alteração administrativa.
     * Intencionalmente vazio nesta versão: nenhuma origem concede atributos.
     */
    public static void applyOriginEffects(ServerPlayer player) {
    }

    public static Component describe(RegionalOrigin origin) {
        return Component.literal(origin.demonym()).withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(" (" + origin.stateName() + " - " + origin.code() + ", região "
                        + origin.macroregion().displayName() + ")").withStyle(ChatFormatting.GRAY));
    }
}
