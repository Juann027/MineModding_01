package com.tupinicraft.config;

import com.tupinicraft.election.ElectionRules;
import com.tupinicraft.politics.PoliticalOffice;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Ponto único de leitura dos valores efetivos de configuração (lado servidor).
 * <p>
 * Prioridade: <b>1)</b> override administrativo persistente do mundo; <b>2)</b> valor do TOML SERVER.
 * {@code reset} remove o override e o valor volta a ser o do TOML.
 */
public final class ServerSettings {
    public static final int TICKS_PER_SECOND = 20;
    public static final int TICKS_PER_MINUTE = 60 * TICKS_PER_SECOND;

    private ServerSettings() {
    }

    /** Valor efetivo bruto (booleanos como 0/1). */
    public static int raw(MinecraftServer server, ConfigKey key) {
        return ConfigOverridesSavedData.get(server).get(key).orElseGet(() -> BrasilServerConfig.tomlValue(key));
    }

    public static int getInt(MinecraftServer server, ConfigKey key) {
        return raw(server, key);
    }

    public static boolean getBool(MinecraftServer server, ConfigKey key) {
        return raw(server, key) != 0;
    }

    public static int tomlValue(ConfigKey key) {
        return BrasilServerConfig.tomlValue(key);
    }

    public static Optional<Integer> override(MinecraftServer server, ConfigKey key) {
        return ConfigOverridesSavedData.get(server).get(key);
    }

    /**
     * Grava um override do mundo. Rejeita valores fora dos limites e pares mínimo/máximo invertidos.
     *
     * @return mensagem de erro, ou vazio se aplicado
     */
    public static Optional<String> set(MinecraftServer server, ConfigKey key, String rawValue) {
        var parsed = key.parse(rawValue);
        if (parsed.isEmpty()) {
            return Optional.of("Valor inválido para " + key.path() + ": \"" + rawValue + "\". Esperado: " + key.describeRange() + ".");
        }
        int value = parsed.getAsInt();
        Optional<ConfigKey> max = ConfigKeys.pairedMax(key);
        if (max.isPresent() && value > raw(server, max.get())) {
            return Optional.of(key.path() + " não pode ser maior que " + max.get().path() + " (" + raw(server, max.get()) + ").");
        }
        Optional<ConfigKey> min = ConfigKeys.pairedMin(key);
        if (min.isPresent() && value < raw(server, min.get())) {
            return Optional.of(key.path() + " não pode ser menor que " + min.get().path() + " (" + raw(server, min.get()) + ").");
        }
        ConfigOverridesSavedData.get(server).set(key, value);
        return Optional.empty();
    }

    /** Remove o override; retorna false se não havia override. */
    public static boolean reset(MinecraftServer server, ConfigKey key) {
        return ConfigOverridesSavedData.get(server).remove(key);
    }

    // ------------------------------------------------------------------ atalhos tipados

    public static int minPoliticalXp(MinecraftServer server, PoliticalOffice office) {
        return office.isElective() ? getInt(server, ConfigKeys.MIN_POLITICAL_XP.get(office)) : 0;
    }

    /** XP político suficiente para concorrer ao cargo (único requisito pessoal de candidatura). */
    public static boolean hasRequiredXp(MinecraftServer server, PoliticalOffice office, int politicalXp) {
        return office.isElective() && politicalXp >= minPoliticalXp(server, office);
    }

    /** Popularidade de referência do cargo (só entra na pontuação mínima de vitória). */
    public static int referencePopularity(MinecraftServer server, PoliticalOffice office) {
        return office.isElective() ? getInt(server, ConfigKeys.REFERENCE_POPULARITY.get(office)) : 0;
    }

    /** Pontuação mínima de vitória de uma disputa criada agora. */
    public static int minimumWinningScore(MinecraftServer server, PoliticalOffice office) {
        return referencePopularity(server, office) + getInt(server, ConfigKeys.WIN_MARGIN);
    }

    /** Cargos que o XP atual já permite disputar. */
    public static List<PoliticalOffice> unlockedOffices(MinecraftServer server, int politicalXp) {
        List<PoliticalOffice> unlocked = new ArrayList<>();
        for (PoliticalOffice office : PoliticalOffice.values()) {
            if (hasRequiredXp(server, office, politicalXp)) {
                unlocked.add(office);
            }
        }
        return unlocked;
    }

    /** Próximo cargo a ser desbloqueado por XP (menor XP mínimo acima do XP atual). */
    public static Optional<PoliticalOffice> nextUnlock(MinecraftServer server, int politicalXp) {
        PoliticalOffice best = null;
        for (PoliticalOffice office : PoliticalOffice.values()) {
            if (office.isElective() && minPoliticalXp(server, office) > politicalXp
                    && (best == null || minPoliticalXp(server, office) < minPoliticalXp(server, best))) {
                best = office;
            }
        }
        return Optional.ofNullable(best);
    }

    /** Maior XP mínimo já alcançado (base da barra de XP político). */
    public static int xpFloor(MinecraftServer server, int politicalXp) {
        int floor = 0;
        for (PoliticalOffice office : unlockedOffices(server, politicalXp)) {
            floor = Math.max(floor, minPoliticalXp(server, office));
        }
        return floor;
    }

    public static long termTicks(MinecraftServer server, PoliticalOffice office) {
        return (long) getInt(server, ConfigKeys.TERM_MINUTES.get(office)) * TICKS_PER_MINUTE;
    }

    /** Faixa [mín, máx] de popularidade de NPCs para o cargo (normalizada se o TOML estiver invertido). */
    public static int[] npcPopularityRange(MinecraftServer server, PoliticalOffice office) {
        int min = getInt(server, ConfigKeys.NPC_POPULARITY_MIN.get(office));
        int max = getInt(server, ConfigKeys.NPC_POPULARITY_MAX.get(office));
        return new int[]{min, Math.max(min, max)};
    }

    public static int registrationTicks(MinecraftServer server) {
        return getInt(server, ConfigKeys.REGISTRATION_SECONDS) * TICKS_PER_SECOND;
    }

    public static int intervalTicks(MinecraftServer server, ConfigKey intervalKey) {
        return getInt(server, intervalKey) * TICKS_PER_MINUTE;
    }

    /** Copia as regras eleitorais efetivas para congelá-las numa nova eleição. */
    public static ElectionRules snapshotElectionRules(MinecraftServer server) {
        return new ElectionRules(
                getInt(server, ConfigKeys.VOTE_MULTIPLIER),
                getInt(server, ConfigKeys.POPULARITY_DIVISOR),
                getInt(server, ConfigKeys.MAX_VOTE_WEIGHT),
                getBool(server, ConfigKeys.VILLAGER_CANDIDATES),
                getInt(server, ConfigKeys.MIN_CANDIDATES),
                getInt(server, ConfigKeys.MAX_NPC_CANDIDATES),
                getInt(server, ConfigKeys.NPC_SUPPORT_MIN),
                getInt(server, ConfigKeys.NPC_SUPPORT_MAX),
                getInt(server, ConfigKeys.VOTING_SECONDS) * TICKS_PER_SECOND,
                getInt(server, ConfigKeys.COUNTING_SECONDS) * TICKS_PER_SECOND);
    }
}
