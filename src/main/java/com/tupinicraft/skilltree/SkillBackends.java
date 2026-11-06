package com.tupinicraft.skilltree;

import com.tupinicraft.TupiniCraft;
import com.tupinicraft.compat.skilllib.SkillLibBackend;
import net.neoforged.fml.ModList;

/**
 * Escolhe o backend na inicialização. A classe {@link SkillLibBackend} só é carregada dentro da lambda,
 * quando o SkillLib está instalado; sem ele, nenhuma classe {@code cn.skilllib.*} é tocada.
 */
public final class SkillBackends {
    private static SkillBackend current = SkillBackend.NONE;

    private SkillBackends() {
    }

    public static void init() {
        if (ModList.get().isLoaded("skilllib")) {
            java.util.function.Supplier<SkillBackend> factory = () -> new SkillLibBackend();
            current = factory.get();
            TupiniCraft.LOGGER.info("TupiniCraft: SkillLib detectado; árvores de habilidades ativadas.");
        } else {
            TupiniCraft.LOGGER.info("TupiniCraft: SkillLib não instalado; árvores de habilidades desativadas.");
        }
    }

    public static SkillBackend get() {
        return current;
    }
}
