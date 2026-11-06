package com.tupinicraft.player;

/**
 * Progresso de habilidades de mandato em um cargo: pontos de habilidade e segundos online acumulados
 * para o próximo ponto. Fica guardado por cargo e é mantido quando o mandato termina, para ser
 * reaproveitado se o jogador voltar ao mesmo cargo.
 */
public record OfficeProgress(int skillPoints, int onlineSeconds) {
    public static final OfficeProgress EMPTY = new OfficeProgress(0, 0);

    public OfficeProgress {
        skillPoints = Math.max(0, skillPoints);
        onlineSeconds = Math.max(0, onlineSeconds);
    }
}
