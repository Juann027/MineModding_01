package com.tupinicraft;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Configuração de servidor (arquivo por mundo em serverconfig/tupinicraft-server.toml). */
public final class TupiniConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue AUTO_ELECTIONS;
    public static final ModConfigSpec.IntValue AUTO_ELECTION_INTERVAL_MINUTES;
    public static final ModConfigSpec.IntValue REGISTRATION_SECONDS;
    public static final ModConfigSpec.IntValue VOTING_SECONDS;
    public static final ModConfigSpec.IntValue COUNTING_SECONDS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("eleicao");
        AUTO_ELECTIONS = builder
                .comment("Inicia eleições automaticamente a cada intervalo (só conta tempo com jogadores online).")
                .define("eleicoesAutomaticas", true);
        AUTO_ELECTION_INTERVAL_MINUTES = builder
                .comment("Intervalo entre eleições automáticas, em minutos de tempo real.")
                .defineInRange("intervaloMinutos", 30, 1, 24 * 60);
        REGISTRATION_SECONDS = builder
                .comment("Duração da fase de inscrição, em segundos.")
                .defineInRange("inscricaoSegundos", 30, 5, 3600);
        VOTING_SECONDS = builder
                .comment("Duração da fase de votação, em segundos.")
                .defineInRange("votacaoSegundos", 60, 5, 3600);
        COUNTING_SECONDS = builder
                .comment("Duração da apuração gradual, em segundos.")
                .defineInRange("apuracaoSegundos", 15, 3, 600);
        builder.pop();
        SPEC = builder.build();
    }

    private TupiniConfig() {
    }
}
