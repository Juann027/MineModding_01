package com.respectcraft.profile;

/** Atributos sorteados no nascimento. Os ids sao persistidos (nao renomear). Puro, sem classes do jogo. */
public enum CharacterAttribute {
    FORCA("forca", 0xFFE0533D),
    AGILIDADE("agilidade", 0xFF5FD3E0),
    VIGOR("vigor", 0xFF6BCB5E),
    INTELIGENCIA("inteligencia", 0xFF7C8CF0),
    CARISMA("carisma", 0xFFF0A3D0),
    SORTE("sorte", 0xFFF2CF4A);

    public static final int MIN = 1;
    public static final int MAX = 10;
    /** Valor de partida antes da distribuicao aleatoria; tambem e o "neutro" dos bonus (sem efeito). */
    public static final int BASE = 3;

    private final String id;
    private final int color;

    CharacterAttribute(String id, int color) {
        this.id = id;
        this.color = color;
    }

    public String id() { return id; }
    public int color() { return color; }

    public static int clamp(int value) {
        return Math.max(MIN, Math.min(MAX, value));
    }
}
