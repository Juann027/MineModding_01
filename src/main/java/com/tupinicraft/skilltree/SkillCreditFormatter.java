package com.tupinicraft.skilltree;

import java.util.Locale;

/** Formatação de valores em creditos fictícios (inteiros), ex.: {@code cr 4.500}. Classe pura. */
public final class SkillCreditFormatter {
    private SkillCreditFormatter() {
    }

    public static String format(long creditos) {
        String digits = String.format(Locale.ROOT, "%,d", Math.abs(creditos)).replace(',', '.');
        return (creditos < 0 ? "-cr " : "cr ") + digits;
    }
}
