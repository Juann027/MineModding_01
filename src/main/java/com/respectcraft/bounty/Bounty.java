package com.respectcraft.bounty;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.item.ItemStack;

/**
 * Um contrato de caca a recompensa contra um jogador (Etapa 4.11): alvo, quem pagou, a recompensa (itens retirados
 * da mao de quem pagou e guardados ate o fim), quando foi aberto/expira (tempo de jogo do Overworld) e os cacadores
 * que aceitaram. Gravado no {@link BountyBoard}; nomes de campo do Codec sao estaveis.
 */
public record Bounty(UUID id, UUID target, String targetName, UUID poster, String posterName, ItemStack reward,
                     long created, long expires, List<UUID> hunters) {
    public Bounty {
        hunters = List.copyOf(hunters);
        reward = reward.copy();
    }

    public static final Codec<Bounty> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(Bounty::id),
            UUIDUtil.CODEC.fieldOf("target").forGetter(Bounty::target),
            Codec.STRING.fieldOf("target_name").forGetter(Bounty::targetName),
            UUIDUtil.CODEC.fieldOf("poster").forGetter(Bounty::poster),
            Codec.STRING.fieldOf("poster_name").forGetter(Bounty::posterName),
            ItemStack.OPTIONAL_CODEC.fieldOf("reward").forGetter(Bounty::reward),
            Codec.LONG.fieldOf("created").forGetter(Bounty::created),
            Codec.LONG.fieldOf("expires").forGetter(Bounty::expires),
            UUIDUtil.CODEC.listOf().optionalFieldOf("hunters", List.of()).forGetter(Bounty::hunters)
    ).apply(i, Bounty::new));

    public boolean hunting(UUID player) { return hunters.contains(player); }

    public Bounty withHunter(UUID hunter) {
        List<UUID> next = new ArrayList<>(hunters);
        if (!next.contains(hunter)) next.add(hunter);
        return new Bounty(id, target, targetName, poster, posterName, reward, created, expires, next);
    }

    public Bounty withoutHunter(UUID hunter) {
        List<UUID> next = new ArrayList<>(hunters);
        next.remove(hunter);
        return new Bounty(id, target, targetName, poster, posterName, reward, created, expires, next);
    }

    /** Id curto (8 primeiros caracteres) usado nos comandos e nas mensagens. */
    public String shortId() { return id.toString().substring(0, 8); }
}
