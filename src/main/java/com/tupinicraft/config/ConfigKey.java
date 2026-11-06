package com.tupinicraft.config;

import java.util.Locale;
import java.util.OptionalInt;

/**
 * Definição de uma configuração de servidor: caminho (igual no TOML e no comando), tipo, padrão e limites.
 * Booleanos são representados internamente como 0/1. Classe pura (sem Minecraft) para poder ser testada.
 */
public record ConfigKey(String path, Type type, int defaultValue, int min, int max, String comment) {
    public enum Type {
        INT,
        BOOL
    }

    public ConfigKey {
        if (min > max || defaultValue < min || defaultValue > max) {
            throw new IllegalArgumentException("Limites inválidos para " + path);
        }
    }

    public static ConfigKey integer(String path, int defaultValue, int min, int max, String comment) {
        return new ConfigKey(path, Type.INT, defaultValue, min, max, comment);
    }

    public static ConfigKey bool(String path, boolean defaultValue, String comment) {
        return new ConfigKey(path, Type.BOOL, defaultValue ? 1 : 0, 0, 1, comment);
    }

    public boolean isBool() {
        return type == Type.BOOL;
    }

    public boolean isValid(int value) {
        return value >= min && value <= max;
    }

    /**
     * Converte o texto digitado num comando. Booleanos aceitam true/false, sim/nao, on/off e 1/0.
     * Retorna vazio se o texto não é do tipo certo ou está fora dos limites.
     */
    public OptionalInt parse(String raw) {
        if (raw == null) {
            return OptionalInt.empty();
        }
        String text = raw.trim().toLowerCase(Locale.ROOT);
        int value;
        if (isBool()) {
            switch (text) {
                case "true", "sim", "on", "1" -> value = 1;
                case "false", "nao", "não", "off", "0" -> value = 0;
                default -> {
                    return OptionalInt.empty();
                }
            }
        } else {
            try {
                value = Integer.parseInt(text);
            } catch (NumberFormatException e) {
                return OptionalInt.empty();
            }
        }
        return isValid(value) ? OptionalInt.of(value) : OptionalInt.empty();
    }

    public String format(int value) {
        return isBool() ? String.valueOf(value != 0) : String.valueOf(value);
    }

    public String describeRange() {
        return isBool() ? "true ou false" : "inteiro de " + min + " a " + max;
    }
}
