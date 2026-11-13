package com.tupinicraft.skilltree;

import com.tupinicraft.registry.ModAttachments;
import net.minecraft.server.level.ServerPlayer;

/** Apenas créditos legados usados pelas skill trees, sem sistema de economia nem HUD. */
public final class SkillCreditsService {
    private SkillCreditsService() {}

    public static long balance(ServerPlayer player) {
        return player.getData(ModAttachments.SKILL_CREDITS).balance();
    }

    public static void set(ServerPlayer player, long amount) {
        SkillCredits old = player.getData(ModAttachments.SKILL_CREDITS);
        player.setData(ModAttachments.SKILL_CREDITS, old.withBalance(amount));
    }

    public static boolean debit(ServerPlayer player, long amount) {
        long balance = balance(player);
        if (amount < 0 || balance < amount) return false;
        set(player, balance - amount);
        return true;
    }
}
