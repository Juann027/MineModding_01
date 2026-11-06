package com.tupinicraft.config;

import com.tupinicraft.politics.PoliticalOffice;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Tabela única de todas as configurações de servidor do mod (eleições, mandatos, NPCs, progressão).
 * <p>
 * Esta tabela gera o arquivo TOML ({@code BrasilServerConfig}) e valida os comandos
 * {@code /brasil admin config}. Valores efetivos são lidos sempre por {@code ServerSettings}.
 * O caminho da chave é persistido nos overrides do mundo: não renomeie chaves existentes.
 */
public final class ConfigKeys {
    private static final int MAX_MINUTES = 7 * 24 * 60;
    private static final int MAX_STAT = 1_000_000;

    private static final Map<String, ConfigKey> BY_PATH = new LinkedHashMap<>();
    /** Pares (mínimo → máximo) que precisam respeitar mínimo ≤ máximo. */
    private static final Map<ConfigKey, ConfigKey> MIN_TO_MAX = new LinkedHashMap<>();

    // ------------------------------------------------------------------ eleições
    public static final ConfigKey AUTO_ELECTIONS = add(ConfigKey.bool("eleicao.automaticas", true,
            "Inicia eleições automaticamente pelos calendários geral e municipal (o tempo só corre com jogadores online)."));
    public static final ConfigKey GENERAL_INTERVAL = add(ConfigKey.integer("eleicao.intervalo_gerais", 30, 1, MAX_MINUTES,
            "Intervalo entre eleições gerais (Presidente, Governador, Senador, Deputados), em minutos."));
    public static final ConfigKey MUNICIPAL_INTERVAL = add(ConfigKey.integer("eleicao.intervalo_municipais", 30, 1, MAX_MINUTES,
            "Intervalo entre eleições municipais (Prefeito, Vereador), em minutos."));
    public static final ConfigKey CALENDAR_OFFSET = add(ConfigKey.integer("eleicao.deslocamento_inicial", 15, 0, MAX_MINUTES,
            "Atraso inicial do calendário municipal em relação ao geral, em minutos."));
    public static final ConfigKey REGISTRATION_SECONDS = add(ConfigKey.integer("eleicao.inscricao_segundos", 30, 5, 3600,
            "Duração da fase de inscrição, em segundos."));
    public static final ConfigKey VOTING_SECONDS = add(ConfigKey.integer("eleicao.votacao_segundos", 60, 5, 3600,
            "Duração da fase de votação, em segundos."));
    public static final ConfigKey COUNTING_SECONDS = add(ConfigKey.integer("eleicao.apuracao_segundos", 15, 3, 600,
            "Duração da apuração gradual, em segundos."));
    public static final ConfigKey VILLAGER_CANDIDATES = add(ConfigKey.bool("eleicao.candidatos_villagers", true,
            "Completa disputas com candidatos aldeões (NPCs) quando faltam candidatos."));
    public static final ConfigKey MIN_CANDIDATES = add(ConfigKey.integer("eleicao.min_candidatos", 2, 1, 10,
            "Número mínimo de candidatos por disputa. Com aldeões ativos, NPCs completam a vaga; sem eles, a disputa é cancelada."));
    public static final ConfigKey MAX_NPC_CANDIDATES = add(ConfigKey.integer("eleicao.max_npcs_por_disputa", 2, 0, 10,
            "Número máximo de candidatos NPCs por disputa."));
    public static final ConfigKey NPC_SUPPORT_MIN = add(ConfigKey.integer("eleicao.apoio_npc_min", 0, 0, 1000,
            "Apoio eleitoral simulado mínimo de um NPC (equivale a peso de votos favoráveis)."));
    public static final ConfigKey NPC_SUPPORT_MAX = add(ConfigKey.integer("eleicao.apoio_npc_max", 3, 0, 1000,
            "Apoio eleitoral simulado máximo de um NPC."));
    public static final ConfigKey VOTE_MULTIPLIER = add(ConfigKey.integer("eleicao.multiplicador_voto", 20, 0, 10_000,
            "Pontos por unidade de peso de voto: pontuação = popularidade + multiplicador × (peso dos votos + apoio)."));
    public static final ConfigKey POPULARITY_DIVISOR = add(ConfigKey.integer("eleicao.divisor_popularidade", 50, 1, MAX_STAT,
            "Peso do voto = 1 + popularidade do eleitor ÷ divisor (limitado pelo peso máximo)."));
    public static final ConfigKey MAX_VOTE_WEIGHT = add(ConfigKey.integer("eleicao.peso_maximo", 5, 1, 1000,
            "Peso máximo permitido por voto."));
    public static final ConfigKey WIN_MARGIN = add(ConfigKey.integer("eleicao.margem_vitoria", 30, 0, MAX_STAT,
            "Pontuação mínima de vitória = popularidade de referência do cargo + margem."));

    // ------------------------------------------------------------------ mandatos
    public static final Map<PoliticalOffice, ConfigKey> TERM_MINUTES = perOffice("mandato.%s",
            office -> office.defaultTermMinutes(), 1, MAX_MINUTES, "Duração do mandato de %s, em minutos.");
    public static final ConfigKey CONSECUTIVE_REELECTION = add(ConfigKey.bool("mandato.reeleicao", true,
            "Permite que o último ocupante de uma cadeira dispute a mesma cadeira na eleição seguinte."));
    public static final ConfigKey TERM_EXPIRATION = add(ConfigKey.bool("mandato.expiracao_automatica", true,
            "Encerra mandatos automaticamente ao fim do prazo. Desativado, os mandatos não expiram."));

    // ------------------------------------------------------------------ pontos de habilidade
    public static final ConfigKey SKILL_POINTS_ENABLED = add(ConfigKey.bool("skills.ganho_por_mandato", true,
            "Concede pontos de habilidade pelo tempo online exercendo um mandato."));
    public static final ConfigKey MINUTES_PER_SKILL_POINT = add(ConfigKey.integer("skills.minutos_por_ponto", 10, 1, MAX_MINUTES,
            "Minutos online exercendo mandato para ganhar 1 ponto de habilidade."));

    // ------------------------------------------------------------------ progressão
    public static final Map<PoliticalOffice, ConfigKey> MIN_POLITICAL_XP = perOffice("progressao.xp_min.%s",
            PoliticalOffice::defaultMinPoliticalXp, 0, MAX_STAT,
            "XP político mínimo para se candidatar a %s (único requisito de candidatura; não exige cargo anterior).");
    public static final Map<PoliticalOffice, ConfigKey> REFERENCE_POPULARITY = perOffice("eleicao.popularidade_referencia.%s",
            PoliticalOffice::defaultReferencePopularity, 0, MAX_STAT,
            "Popularidade de referência de %s: pontuação mínima de vitória = referência + margem. Não é requisito de candidatura.");

    // ------------------------------------------------------------------ NPCs (por cargo)
    public static final Map<PoliticalOffice, ConfigKey> NPC_POPULARITY_MIN = perOffice("npc.popularidade_min.%s",
            PoliticalOffice::defaultReferencePopularity, 0, MAX_STAT, "Popularidade mínima gerada para candidatos NPCs a %s.");
    public static final Map<PoliticalOffice, ConfigKey> NPC_POPULARITY_MAX = perOffice("npc.popularidade_max.%s",
            office -> office.defaultReferencePopularity() * 2 + 20, 0, MAX_STAT, "Popularidade máxima gerada para candidatos NPCs a %s.");

    static {
        pair(NPC_SUPPORT_MIN, NPC_SUPPORT_MAX);
        for (PoliticalOffice office : NPC_POPULARITY_MIN.keySet()) {
            pair(NPC_POPULARITY_MIN.get(office), NPC_POPULARITY_MAX.get(office));
        }
    }

    private ConfigKeys() {
    }

    public static Collection<ConfigKey> all() {
        return Collections.unmodifiableCollection(BY_PATH.values());
    }

    public static Optional<ConfigKey> byPath(String path) {
        return Optional.ofNullable(path == null ? null : BY_PATH.get(path.trim()));
    }

    /** Se a chave é o mínimo de um par, devolve a chave do máximo correspondente. */
    public static Optional<ConfigKey> pairedMax(ConfigKey key) {
        return Optional.ofNullable(MIN_TO_MAX.get(key));
    }

    /** Se a chave é o máximo de um par, devolve a chave do mínimo correspondente. */
    public static Optional<ConfigKey> pairedMin(ConfigKey key) {
        for (Map.Entry<ConfigKey, ConfigKey> entry : MIN_TO_MAX.entrySet()) {
            if (entry.getValue().equals(key)) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }

    private static ConfigKey add(ConfigKey key) {
        if (BY_PATH.putIfAbsent(key.path(), key) != null) {
            throw new IllegalStateException("Chave de configuração duplicada: " + key.path());
        }
        return key;
    }

    private static void pair(ConfigKey min, ConfigKey max) {
        MIN_TO_MAX.put(min, max);
    }

    private interface OfficeDefault {
        int value(PoliticalOffice office);
    }

    /** Cria uma chave por cargo eletivo (Vereador … Presidente). */
    private static Map<PoliticalOffice, ConfigKey> perOffice(String pathFormat, OfficeDefault defaults,
                                                             int min, int max, String commentFormat) {
        Map<PoliticalOffice, ConfigKey> map = new EnumMap<>(PoliticalOffice.class);
        for (PoliticalOffice office : PoliticalOffice.values()) {
            if (office.isElective()) {
                int defaultValue = Math.max(min, Math.min(max, defaults.value(office)));
                map.put(office, add(ConfigKey.integer(String.format(pathFormat, office.id()), defaultValue, min, max,
                        String.format(commentFormat, office.displayName()))));
            }
        }
        return Collections.unmodifiableMap(map);
    }
}
