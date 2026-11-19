package com.respectcraft.skill;

import com.respectcraft.RespectCraft;
import net.minecraft.resources.ResourceLocation;

/** Ramos (abas) da arvore. Cada um tem fundo, cor e icone proprios. */
public enum SkillBranch {
    COMBATE("combate", 0xFFE0533D, "caveira"),
    SOBREVIVENCIA("sobrevivencia", 0xFF5BC26A, "amazonia"),
    MALANDRAGEM("malandragem", 0xFFB57BE8, "pix");

    private final String id;
    private final int accent;
    private final String icon;

    SkillBranch(String id, int accent, String icon) {
        this.id = id;
        this.accent = accent;
        this.icon = icon;
    }

    public String id() { return id; }
    public int accent() { return accent; }
    public ResourceLocation icon() { return SkillDef.iconTexture(icon); }
    public ResourceLocation background() { return RespectCraft.id("textures/gui/skilltree/bg_" + id + ".png"); }
}
