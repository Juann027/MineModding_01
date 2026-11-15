package com.tupinicraft.character;

/**
 * Aparência cosmética do personagem. Não contém atributos ou dados de gameplay.
 * Todos os campos têm um catálogo fixo, validado no servidor antes de persistir.
 */
public record CharacterAppearance(boolean created, int bodyType, int hairStyle, int hairColor,
                                  int skinTone, int face) {
    public static final String[] BODY_IDS = {"masculino", "feminino"};
    public static final String[] HAIR_IDS = {"careca", "curto", "franja", "cacheado", "longo", "rabo_de_cavalo", "moicano"};
    public static final String[] COLOR_IDS = {"preto", "castanho", "loiro", "ruivo", "branco", "azul"};
    public static final int[] COLOR_ARGB = {0xFF242329, 0xFF67402B, 0xFFE0B85C, 0xFFB45A34, 0xFFE5DCCC, 0xFF527ABF};
    public static final String[] SKIN_IDS = {"clara", "media", "escura"};
    public static final int[] SKIN_ARGB = {0xFFF3CBA8, 0xFFC28D64, 0xFF71452D};
    public static final String[] FACE_IDS = {"neutro", "feliz", "serio"};

    // Cada opção ocupa 4 bits. O bit 20 identifica a criação concluída.
    private static final int KNOWN_BITS = (1 << 21) - 1;

    public static CharacterAppearance defaults() {
        return new CharacterAppearance(false, 0, 1, 1, 1, 0);
    }

    public boolean valid() {
        return in(bodyType, BODY_IDS.length) && in(hairStyle, HAIR_IDS.length)
                && in(hairColor, COLOR_IDS.length) && in(skinTone, SKIN_IDS.length)
                && in(face, FACE_IDS.length);
    }

    private static boolean in(int index, int size) {
        return index >= 0 && index < size;
    }

    public int pack() {
        if (!valid()) throw new IllegalArgumentException("Aparência inválida");
        return (created ? 1 << 20 : 0) | (bodyType << 16) | (hairStyle << 12)
                | (hairColor << 8) | (skinTone << 4) | face;
    }

    /** Null significa pacote inválido; não aceitar valores arbitrários enviados pelo cliente. */
    public static CharacterAppearance fromPacked(int packed) {
        if (packed < 0 || (packed & ~KNOWN_BITS) != 0) return null;
        CharacterAppearance result = new CharacterAppearance((packed & (1 << 20)) != 0,
                (packed >>> 16) & 15, (packed >>> 12) & 15, (packed >>> 8) & 15,
                (packed >>> 4) & 15, packed & 15);
        return result.valid() ? result : null;
    }
}
