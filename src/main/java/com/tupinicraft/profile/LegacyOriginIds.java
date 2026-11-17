package com.tupinicraft.profile;

import java.util.Map;

/**
 * Compatibilidade de saves: ids de origem que deixaram de existir e a origem que os substitui.
 * So o Codec da ficha usa isto (na leitura); a ficha volta a ser gravada com o id novo.
 * Os ids antigos ficam aqui apenas como alias de migracao: nunca entram no catalogo, nas traducoes
 * nem no sorteio. Puro: sem classes do Minecraft.
 */
public final class LegacyOriginIds {
    /** v0.5.7 e anteriores: a origem politica antiga virou Familia Artistica (mesmo peso e bonus). */
    private static final Map<String, BirthOrigin> ALIASES = Map.of("familia_politica", BirthOrigin.FAMILIA_ARTISTICA);

    /** Id atual para um id lido do save (ids atuais, vazios e desconhecidos voltam sem mudanca). */
    public static String migrate(String storedId) {
        BirthOrigin replacement = ALIASES.get(storedId);
        return replacement == null ? storedId : replacement.id();
    }

    private LegacyOriginIds() {}
}
