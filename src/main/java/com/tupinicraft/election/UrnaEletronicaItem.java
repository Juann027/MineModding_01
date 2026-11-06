package com.tupinicraft.election;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Urna eletrônica: entregue a todos no início da votação. O clique direito pede ao servidor
 * para abrir a urna; o servidor valida a fase e responde com {@code OpenUrnaPayload}.
 * O item em si não concede voto: toda validação fica em {@link ElectionManager#castVote}.
 */
public class UrnaEletronicaItem extends Item {
    public UrnaEletronicaItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            ElectionManager.openBallot(serverPlayer);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Botão direito durante a votação para votar.").withStyle(ChatFormatting.GRAY));
    }
}
