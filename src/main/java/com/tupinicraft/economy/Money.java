package com.tupinicraft.economy;

import java.util.Locale;

/** Formatação de valores em reais fictícios (inteiros), ex.: {@code R$ 4.500}. Classe pura. */
public final class Money {
    private Money() {
    }

    public static String format(long reais) {
        String digits = String.format(Locale.ROOT, "%,d", Math.abs(reais)).replace(',', '.');
        return (reais < 0 ? "-R$ " : "R$ ") + digits;
    }
}
