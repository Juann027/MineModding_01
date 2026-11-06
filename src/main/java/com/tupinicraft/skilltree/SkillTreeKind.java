package com.tupinicraft.skilltree;

/** As quatro categorias independentes de árvores de habilidades. */
public enum SkillTreeKind {
    /** Para todos os jogadores; custa dinheiro. */
    PERSONAL("pessoal", "Pessoais"),
    /** Depende da macrorregião de origem; custa dinheiro. */
    REGIONAL("regional", "Regionais"),
    /** Depende do partido de filiação; custa dinheiro; progresso fica salvo ao sair do partido. */
    PARTY("partidaria", "Partidárias"),
    /** Depende do mandato ativo; custa pontos daquele cargo; XP político nunca é consumido. */
    OFFICE("cargo", "Cargos políticos");

    private final String id;
    private final String displayName;

    SkillTreeKind(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public static SkillTreeKind byId(String id) {
        for (SkillTreeKind kind : values()) {
            if (kind.id.equals(id)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("Tipo de árvore desconhecido: " + id + " (use pessoal, regional, partidaria ou cargo)");
    }
}
