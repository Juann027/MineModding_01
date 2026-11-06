package com.tupinicraft.territory;

import com.tupinicraft.origin.RegionalOrigin;
import com.tupinicraft.politics.PoliticalOffice;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Catálogo imutável de territórios: Brasil, as 27 UFs e um município por UF (a capital).
 * <p>
 * Os IDs são estáveis e persistidos (mandatos, eleições): <b>nunca renomeie um ID</b>; para mudar o
 * nome exibido, altere apenas o nome. Para cadastrar novos municípios, adicione entradas em
 * {@link #CAPITALS} (ou numa lista própria) com IDs novos.
 */
public final class Territories {
    public static final String NAMESPACE = "tupinicraft";
    public static final String BRASIL_ID = NAMESPACE + ":brasil";

    /** Capitais (município cadastrado de cada UF): UF → (caminho do ID, nome exibido). */
    private static final Map<RegionalOrigin, String[]> CAPITALS = new EnumMap<>(RegionalOrigin.class);

    static {
        capital(RegionalOrigin.AC, "rio_branco", "Rio Branco");
        capital(RegionalOrigin.AL, "maceio", "Maceió");
        capital(RegionalOrigin.AP, "macapa", "Macapá");
        capital(RegionalOrigin.AM, "manaus", "Manaus");
        capital(RegionalOrigin.BA, "salvador", "Salvador");
        capital(RegionalOrigin.CE, "fortaleza", "Fortaleza");
        // O DF não tem municípios; Brasília entra como município por simplicidade de jogo.
        capital(RegionalOrigin.DF, "brasilia", "Brasília");
        capital(RegionalOrigin.ES, "vitoria", "Vitória");
        capital(RegionalOrigin.GO, "goiania", "Goiânia");
        capital(RegionalOrigin.MA, "sao_luis", "São Luís");
        capital(RegionalOrigin.MT, "cuiaba", "Cuiabá");
        capital(RegionalOrigin.MS, "campo_grande", "Campo Grande");
        capital(RegionalOrigin.MG, "belo_horizonte", "Belo Horizonte");
        capital(RegionalOrigin.PA, "belem", "Belém");
        capital(RegionalOrigin.PB, "joao_pessoa", "João Pessoa");
        capital(RegionalOrigin.PR, "curitiba", "Curitiba");
        capital(RegionalOrigin.PE, "recife", "Recife");
        capital(RegionalOrigin.PI, "teresina", "Teresina");
        capital(RegionalOrigin.RJ, "rio_de_janeiro", "Rio de Janeiro");
        capital(RegionalOrigin.RN, "natal", "Natal");
        capital(RegionalOrigin.RS, "porto_alegre", "Porto Alegre");
        capital(RegionalOrigin.RO, "porto_velho", "Porto Velho");
        capital(RegionalOrigin.RR, "boa_vista", "Boa Vista");
        capital(RegionalOrigin.SC, "florianopolis", "Florianópolis");
        capital(RegionalOrigin.SP, "sao_paulo", "São Paulo");
        capital(RegionalOrigin.SE, "aracaju", "Aracaju");
        capital(RegionalOrigin.TO, "palmas", "Palmas");
    }

    private static final Map<String, Territory> BY_ID;

    static {
        Map<String, Territory> map = new LinkedHashMap<>();
        add(map, new Territory(BRASIL_ID, "Brasil", TerritoryType.COUNTRY, Optional.empty(), Optional.empty()));
        for (RegionalOrigin uf : RegionalOrigin.values()) {
            add(map, new Territory(stateId(uf), uf.stateName(), TerritoryType.STATE, Optional.of(BRASIL_ID), Optional.of(uf)));
        }
        for (RegionalOrigin uf : RegionalOrigin.values()) {
            String[] capital = CAPITALS.get(uf);
            add(map, new Territory(NAMESPACE + ":" + capital[0], capital[1], TerritoryType.MUNICIPALITY,
                    Optional.of(stateId(uf)), Optional.of(uf)));
        }
        BY_ID = Collections.unmodifiableMap(map);
    }

    private Territories() {
    }

    private static void capital(RegionalOrigin uf, String path, String name) {
        CAPITALS.put(uf, new String[]{path, name});
    }

    private static void add(Map<String, Territory> map, Territory territory) {
        if (map.putIfAbsent(territory.id(), territory) != null) {
            throw new IllegalStateException("ID de território duplicado: " + territory.id());
        }
    }

    public static String stateId(RegionalOrigin uf) {
        return NAMESPACE + ":" + uf.code().toLowerCase(Locale.ROOT);
    }

    public static String capitalId(RegionalOrigin uf) {
        return NAMESPACE + ":" + CAPITALS.get(uf)[0];
    }

    /** Aceita o ID completo ({@code tupinicraft:es}) ou só o caminho ({@code es}). */
    public static Optional<Territory> get(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        if (!normalized.contains(":")) {
            normalized = NAMESPACE + ":" + normalized;
        }
        return Optional.ofNullable(BY_ID.get(normalized));
    }

    public static Territory brasil() {
        return BY_ID.get(BRASIL_ID);
    }

    public static Collection<Territory> all() {
        return BY_ID.values();
    }

    public static List<Territory> ofType(TerritoryType type) {
        List<Territory> result = new ArrayList<>();
        for (Territory territory : BY_ID.values()) {
            if (territory.type() == type) {
                result.add(territory);
            }
        }
        return result;
    }

    public static List<Territory> children(String parentId) {
        List<Territory> result = new ArrayList<>();
        for (Territory territory : BY_ID.values()) {
            if (territory.parent().map(parentId::equals).orElse(false)) {
                result.add(territory);
            }
        }
        return result;
    }

    /**
     * Domicílio eleitoral: em qual território um jogador de determinada origem disputa um cargo.
     * Presidente → Brasil; cargos estaduais → estado de origem; municipais → capital do estado de origem.
     * (A origem do jogador NÃO é o território que ele governa; isso fica nas cadeiras de mandato.)
     */
    public static Territory domicile(PoliticalOffice office, RegionalOrigin origin) {
        return switch (office.jurisdiction()) {
            case COUNTRY -> brasil();
            case STATE -> BY_ID.get(stateId(origin));
            case MUNICIPALITY -> BY_ID.get(capitalId(origin));
        };
    }
}
