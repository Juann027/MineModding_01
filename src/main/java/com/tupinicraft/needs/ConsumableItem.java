package com.tupinicraft.needs;

import net.minecraft.ChatFormatting;
import net.minecraft.locale.Language;
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
 * Comida, bebida ou pilula do mod: alem da fome vanilla (se tiver {@code food}), aplica no servidor o
 * {@link NeedEffectDef} (imediato, gradual, atrasado, ritmos, buffs, particulas, som e recarga).
 * Bebidas e pilulas podem ser consumidas de barriga cheia.
 */
public class ConsumableItem extends Item {
    public enum Kind { FOOD, DRINK, PILL }

    private final NeedEffectDef effect;
    private final Kind kind;

    public ConsumableItem(Properties properties, NeedEffectDef effect, Kind kind) {
        super(properties);
        this.effect = effect;
        this.kind = kind;
    }

    public NeedEffectDef effect() { return effect; }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return switch (kind) {
            case DRINK -> UseAnim.DRINK;
            case PILL -> UseAnim.EAT;
            case FOOD -> super.getUseAnimation(stack);
        };
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return switch (kind) {
            case DRINK -> 32;
            case PILL -> 16;
            case FOOD -> super.getUseDuration(stack, entity);
        };
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (kind != Kind.FOOD) return ItemUtils.startUsingInstantly(level, player, hand);
        return super.use(level, player, hand);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof ServerPlayer player) {
            NeedsService.consume(player, effect);
            if (effect.cooldownTicks() > 0) player.getCooldowns().addCooldown(this, effect.cooldownTicks());
        }
        if (kind != Kind.FOOD) {
            stack.consume(1, entity);
            return stack;
        }
        return super.finishUsingItem(stack, level, entity);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, java.util.List<Component> tooltip, TooltipFlag flag) {
        for (Need need : Need.values()) line(tooltip, need, effect.immediate().get(need));
        String desc = "effect.tupinicraft.need." + effect.id() + ".desc";
        if (Language.getInstance().has(desc)) tooltip.add(Component.translatable(desc).withStyle(ChatFormatting.GRAY));
        if (effect.cooldownTicks() > 0) {
            tooltip.add(Component.translatable("tooltip.tupinicraft.cooldown", effect.cooldownTicks() / 20)
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltip.add(Component.translatable("tooltip.tupinicraft.fictional").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    private static void line(java.util.List<Component> tooltip, Need need, int value) {
        if (value == 0) return;
        int percent = Math.round(value / 100F);
        // Positivo e bom para hidratacao/energia/humor; para bexiga/intestino/coom, subir e ruim.
        boolean good = need.highIsGood() == (percent > 0);
        tooltip.add(Component.translatable("tooltip.tupinicraft.need_delta",
                Component.translatable("need.tupinicraft." + need.id()), (percent > 0 ? "+" : "") + percent)
                .withStyle(good ? ChatFormatting.BLUE : ChatFormatting.RED));
    }
}
