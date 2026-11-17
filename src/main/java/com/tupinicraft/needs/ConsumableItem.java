package com.tupinicraft.needs;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * Comida/bebida do mod: alem da fome vanilla (se tiver {@code food}), altera as necessidades no servidor.
 * Bebidas usam a animacao de beber e podem ser tomadas de barriga cheia.
 */
public class ConsumableItem extends Item {
    private final NeedsDelta delta;
    private final boolean drink;

    public ConsumableItem(Properties properties, NeedsDelta delta, boolean drink) {
        super(properties);
        this.delta = delta;
        this.drink = drink;
    }

    public NeedsDelta delta() { return delta; }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return drink ? UseAnim.DRINK : super.getUseAnimation(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return drink ? 32 : super.getUseDuration(stack, entity);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (drink) return ItemUtils.startUsingInstantly(level, player, hand);
        return super.use(level, player, hand);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof ServerPlayer player) NeedsService.apply(player, delta);
        if (drink) {
            stack.consume(1, entity);
            return stack;
        }
        return super.finishUsingItem(stack, level, entity);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, java.util.List<Component> tooltip, TooltipFlag flag) {
        line(tooltip, "hidratacao", delta.hydration());
        line(tooltip, "energia", delta.energy());
        line(tooltip, "humor", delta.mood());
        line(tooltip, "bexiga", delta.bladder());
        line(tooltip, "intestino", delta.bowel());
    }

    private static void line(java.util.List<Component> tooltip, String need, int value) {
        if (value == 0) return;
        int percent = Math.round(value / 100F);
        tooltip.add(Component.translatable("tooltip.tupinicraft.need_delta",
                Component.translatable("need.tupinicraft." + need), (percent > 0 ? "+" : "") + percent)
                .withStyle(percent > 0 ? net.minecraft.ChatFormatting.BLUE : net.minecraft.ChatFormatting.RED));
    }
}
