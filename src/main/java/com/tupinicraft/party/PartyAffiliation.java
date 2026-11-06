package com.tupinicraft.party;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/**
 * Filiação partidária do jogador (Data Attachment, copiada na morte, mantida ao perder mandatos).
 * O sistema de partidos ainda NÃO existe: hoje a filiação só é definida por comando administrativo
 * de teste. Sair do partido não apaga as skills compradas daquele partido (ficam no SkillLib);
 * elas só ficam inativas até o jogador voltar.
 */
public record PartyAffiliation(Optional<String> partyId) {
    public static final PartyAffiliation NONE = new PartyAffiliation(Optional.empty());

    public static final Codec<PartyAffiliation> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("party").forGetter(PartyAffiliation::partyId)
    ).apply(instance, PartyAffiliation::new));

    public static boolean isValidId(String id) {
        return id != null && id.matches("[a-z0-9_]{2,32}");
    }
}
