package com.respectcraft.client.holo;

import com.respectcraft.RespectCraft;
import com.respectcraft.client.ui.Ui;
import com.respectcraft.needs.Need;
import com.respectcraft.network.GameplayNetworking;
import com.respectcraft.network.ProfileClientCache;
import com.respectcraft.profile.CharacterProfile;
import com.respectcraft.skill.SkillBranch;
import com.respectcraft.skill.SkillDef;
import com.respectcraft.skill.SkillEffect;
import com.respectcraft.skill.Skills;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Secao 05 "Skills": a arvore de habilidades que ja existia (3 ramos, 18 habilidades, estados bloqueado/disponivel/
 * comprado, custo, pre-requisitos) dentro do painel projetado. Arrastar move a arvore; clicar sem arrastar pede a
 * compra ao servidor ({@code SkillPurchaseRequest}), que valida tudo; a tela so reage a ficha sincronizada e
 * comemora quando a habilidade aparece nela.
 */
final class SkillsView extends HoloView {
    private static final ResourceLocation NODE = RespectCraft.id("textures/gui/skilltree/node.png");
    private static final String KEY = "gui.respectcraft.skills.";
    private static final float NODE_SIZE = 26F;
    private static SkillBranch lastBranch = SkillBranch.COMBATE;

    private SkillBranch branch = lastBranch;
    private float panX, panY, pressPanX, pressPanY, pressX, pressY;
    private boolean panning;
    private ResourceLocation pending;
    private Set<String> knownOwned;
    private long knownRevision = -1;
    // quadro do ultimo desenho (para comemorar no lugar certo e para a dica)
    private float canvasX, canvasY, canvasW, canvasH, gridX, gridY, originY;
    private float branchFlash;
    private boolean centerPending = true;

    SkillsView(HoloCharacterScreen screen) { super(screen, HoloCharacterScreen.Section.SKILLS); }

    @Override
    int accent() { return branch.accent(); }

    @Override
    boolean fitsContent() { return false; }

    @Override
    void onEnter() {
        super.onEnter();
        centerPending = true;
    }

    @Override
    void arrow(int dir) {
        SkillBranch[] all = SkillBranch.values();
        select(all[Math.floorMod(branch.ordinal() + dir, all.length)]);
    }

    private void select(SkillBranch b) {
        if (b == branch) return;
        branch = b;
        lastBranch = b;
        centerPending = true;
        branchFlash = 1F;
        Ui.playClick(1.05F);
    }

    private void centerTree() {
        List<SkillDef> defs = Skills.of(branch);
        int minX = defs.stream().mapToInt(SkillDef::x).min().orElse(0);
        int maxX = defs.stream().mapToInt(SkillDef::x).max().orElse(0);
        panX = -((minX + maxX) / 2F) * gridX;
        panY = 0F;
    }

    /** Ficha nova do servidor: comemora as habilidades que apareceram nela (a compra foi confirmada). */
    @Override
    void tick() {
        CharacterProfile p = ProfileClientCache.profile();
        if (knownOwned == null) {
            knownOwned = p.skillSet();
            knownRevision = ProfileClientCache.revision();
            return;
        }
        if (ProfileClientCache.revision() != knownRevision) {
            knownRevision = ProfileClientCache.revision();
            for (String id : p.skills()) if (!knownOwned.contains(id)) celebrate(Skills.get(id));
            knownOwned = p.skillSet();
            pending = null;
        }
    }

    private void celebrate(SkillDef def) {
        if (def == null || screen.section() != HoloCharacterScreen.Section.SKILLS) return;
        if (def.branch() != branch) select(def.branch());
        float x = nodeX(def), y = nodeY(def) + originY;
        screen.burstOnMain(x, y, def.branch().accent(), 34, 90F);
        screen.burstOnMain(x, y, 0xFFFFFFFF, 10, 50F);
    }

    private float nodeX(SkillDef def) { return canvasX + canvasW / 2F + panX + def.x() * gridX; }
    private float nodeY(SkillDef def) { return canvasY + canvasH / 2F + panY + def.y() * gridY; }

    private enum State { LOCKED, AVAILABLE, OWNED }

    private static State state(SkillDef def, CharacterProfile p) {
        if (p.hasSkill(def.id().toString())) return State.OWNED;
        for (ResourceLocation parent : def.parents()) if (!p.hasSkill(parent.toString())) return State.LOCKED;
        return State.AVAILABLE;
    }

    @Override
    float headerRight(HoloCanvas c, float w) {
        CharacterProfile p = ProfileClientCache.profile();
        String pts = tr(KEY + "points", p.skillPoints());
        float pw = c.font.width(pts) + 8F;
        c.pill(pts, w - 10F - pw, 7F, p.skillPoints() > 0 ? HoloStyle.GOLD : 0xFF3A4654);
        return w - 14F - pw;
    }

    @Override
    float body(HoloCanvas c, float w, float viewportHeight, float since) {
        CharacterProfile p = ProfileClientCache.profile();
        float x = 8F, iw = w - 16F;
        originY = c.panelY(0F);
        branchFlash = Math.max(0F, branchFlash - c.dt * 3F);

        // ---- abas dos ramos
        c.stage(since, 1);
        SkillBranch[] branches = SkillBranch.values();
        float tabW = (iw - (branches.length - 1) * 4F) / branches.length;
        for (int i = 0; i < branches.length; i++) {
            SkillBranch b = branches[i];
            c.button("branch." + b.id(), tr("skillbranch.respectcraft." + b.id()), x + i * (tabW + 4F), 1F, tabW, 15F,
                    b.icon(), b.accent(), true, b == branch, "", () -> select(b));
        }

        // ---- quadro da arvore
        c.stage(since, 2);
        canvasX = x;
        canvasY = 20F;
        canvasW = iw;
        canvasH = viewportHeight - canvasY - 14F;
        gridX = Mth.clamp((canvasW - 44F) / 3F, 46F, 76F);
        gridY = Mth.clamp((canvasH - 34F) / 2F, 30F, 50F);
        if (centerPending) {
            centerTree();
            centerPending = false;
        }
        c.fill(canvasX, canvasY, canvasW, canvasH, 0x90020810);
        c.surface.scissor(c.panelX(canvasX), c.panelY(canvasY), canvasW, canvasH);
        tile(c, branch.background(), canvasX, canvasY, canvasW, canvasH, 64, -panX * 0.5F, -panY * 0.5F);
        c.gradient(canvasX, canvasY, canvasW, 18F, 0xB0020810, 0x00020810);
        c.gradient(canvasX, canvasY + canvasH - 18F, canvasW, 18F, 0x00020810, 0xB0020810);
        if (branchFlash > 0F) c.glow(canvasX + canvasW / 2F, canvasY + canvasH / 2F, canvasW * 0.45F, Ui.alpha(branch.accent(), 0.35F * branchFlash));

        List<SkillDef> defs = Skills.of(branch);
        float time = c.time;
        // ligacoes em cotovelo, com pulso caminhando ate os nos disponiveis
        for (SkillDef def : defs) {
            State s = state(def, p);
            for (ResourceLocation parentId : def.parents()) {
                SkillDef parent = Skills.get(parentId);
                if (parent == null) continue;
                boolean parentOwned = p.hasSkill(parentId.toString());
                int color = s == State.OWNED ? branch.accent() : parentOwned ? 0xFFC9D6E2 : 0xFF3B4048;
                float x1 = nodeX(parent), y1 = nodeY(parent), x2 = nodeX(def), y2 = nodeY(def);
                float mx = (x1 + x2) / 2F;
                line(c, x1, y1, mx, y1, 4, 0xFF06080A);
                line(c, mx, y1, mx, y2, 4, 0xFF06080A);
                line(c, mx, y2, x2, y2, 4, 0xFF06080A);
                line(c, x1, y1, mx + 1, y1, 2, color);
                line(c, mx, y1 - 1, mx, y2 + 1, 2, color);
                line(c, mx - 1, y2, x2, y2, 2, color);
                if (parentOwned && s != State.OWNED) {
                    float total = Math.abs(mx - x1) + Math.abs(y2 - y1) + Math.abs(x2 - mx);
                    float d = (time * 32F) % Math.max(1F, total);
                    float px, py;
                    if (d < Math.abs(mx - x1)) { px = x1 + Math.signum(mx - x1) * d; py = y1; }
                    else if (d < Math.abs(mx - x1) + Math.abs(y2 - y1)) { px = mx; py = y1 + Math.signum(y2 - y1) * (d - Math.abs(mx - x1)); }
                    else { px = mx + Math.signum(x2 - mx) * (d - Math.abs(mx - x1) - Math.abs(y2 - y1)); py = y2; }
                    c.glow(px, py, 6F, Ui.alpha(branch.accent(), 0.9F));
                }
            }
        }
        // nos
        SkillDef hovered = panning ? null : hovered(c, defs);
        for (SkillDef def : defs) {
            State s = state(def, p);
            float k = c.hoverAnim("node." + def.id(), def == hovered);
            float scale = 1F + 0.14F * k;
            float nx = nodeX(def), ny = nodeY(def);
            if (s == State.AVAILABLE) {
                boolean affordable = p.skillPoints() >= def.cost() && p.born();
                float pulse = 0.5F + 0.5F * Mth.sin(time * 3F);
                c.glow(nx, ny, 20F + pulse * 6F, Ui.alpha(affordable ? branch.accent() : 0xFFC9D6E2, 0.3F + 0.3F * pulse));
            } else if (s == State.OWNED) {
                c.glow(nx, ny, 22F, Ui.alpha(branch.accent(), 0.45F));
            }
            if (def.id().equals(pending)) c.glow(nx, ny, 28F, Ui.alpha(0xFFFFFFFF, 0.4F + 0.3F * Mth.sin(time * 12F)));
            c.g.pose().pushPose();
            c.g.pose().translate(nx, ny, 0F);
            c.g.pose().scale(scale, scale, 1F);
            int frame = s == State.OWNED ? 2 : s == State.AVAILABLE ? 1 : 0;
            HoloSurface.accumulate();
            c.g.setColor(1F, 1F, 1F, c.alpha);
            c.g.blit(NODE, -13, -13, frame * 26, 0, 26, 26, 78, 26);
            c.g.setColor(1F, 1F, 1F, 1F);
            c.icon(def.icon(), -8F, -8F, 16F, s == State.LOCKED ? 0.38F : 1F);
            c.g.pose().popPose();
            if (s != State.OWNED) {
                String cost = String.valueOf(def.cost());
                float cx = nx + 9F, cy = ny + 7F;
                c.fill(cx - 1F, cy - 1F, c.font.width(cost) + 4F, 10F, 0xFF06080A);
                c.text(cost, cx + 1F, cy, p.skillPoints() >= def.cost() ? Ui.GOLD : Ui.RED, false);
            }
        }
        c.surface.unscissor();
        c.surface.scissor(1F, HEADER + 3F, w - 2F, viewportHeight);
        c.frame(canvasX, canvasY, canvasW, canvasH, Ui.alpha(branch.accent(), 0.35F));
        c.brackets(canvasX, canvasY, canvasW, canvasH, 8F, branch.accent());

        // regiao do quadro: arrastar move, clicar compra
        HoloZone zone = c.zone(canvasX, canvasY, canvasW, canvasH);
        float zoneX = c.panelX(0F), zoneY = c.panelY(0F);
        zone.drag = new HoloZone.Drag() {
            @Override public void press(float px, float py) {
                panning = false;
                pressX = px;
                pressY = py;
                pressPanX = panX;
                pressPanY = panY;
            }
            @Override public void move(float px, float py) {
                if (Math.abs(px - pressX) + Math.abs(py - pressY) > 3F) panning = true;
                if (panning) {
                    panX = Mth.clamp(pressPanX + (px - pressX), -gridX * 5F, gridX * 5F);
                    panY = Mth.clamp(pressPanY + (py - pressY), -gridY * 4F, gridY * 4F);
                }
            }
            @Override public void release(float px, float py, boolean click) {
                boolean wasPanning = panning;
                panning = false;
                if (click && !wasPanning) tryPurchase(nodeAt(px - zoneX, py - zoneY));
            }
        };

        if (hovered != null) c.tooltip(tooltip(hovered, p));

        // dica
        c.stage(since, 3);
        String hint = tr(p.born() ? KEY + "hint" : KEY + "unborn");
        c.text(Ui.fit(c.font, hint, (int) iw), x + 2F, canvasY + canvasH + 4F, p.born() ? c.muted() : HoloStyle.RED, false);
        return viewportHeight;
    }

    private SkillDef hovered(HoloCanvas c, List<SkillDef> defs) {
        if (!c.over(canvasX, canvasY, canvasW, canvasH)) return null;
        for (SkillDef def : defs) {
            if (c.over(nodeX(def) - NODE_SIZE / 2F, nodeY(def) - NODE_SIZE / 2F, NODE_SIZE, NODE_SIZE)) return def;
        }
        return null;
    }

    private SkillDef nodeAt(float x, float y) {
        if (x < canvasX || y < canvasY || x >= canvasX + canvasW || y >= canvasY + canvasH) return null;
        for (SkillDef def : Skills.of(branch)) {
            if (Math.abs(x - nodeX(def)) <= NODE_SIZE / 2F && Math.abs(y - nodeY(def)) <= NODE_SIZE / 2F) return def;
        }
        return null;
    }

    /** Mesmo fluxo da arvore antiga: valida localmente so para o retorno visual; o servidor decide. */
    private void tryPurchase(SkillDef def) {
        if (def == null) return;
        CharacterProfile p = ProfileClientCache.profile();
        String blocker = Skills.purchaseBlocker(def, p.skillSet(), p.skillPoints(), p.born());
        if (blocker != null) {
            Ui.playClick(0.6F);
            screen.burstOnMain(nodeX(def), nodeY(def) + originY, HoloStyle.RED, 8, 40F);
            return;
        }
        Ui.playClick(1.2F);
        pending = def.id();
        screen.burstOnMain(nodeX(def), nodeY(def) + originY, branch.accent(), 10, 45F);
        PacketDistributor.sendToServer(new GameplayNetworking.SkillPurchaseRequest(def.id()));
    }

    private List<Component> tooltip(SkillDef def, CharacterProfile p) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(def.translationKey()).withStyle(s -> s.withColor(def.branch().accent() & 0xFFFFFF).withBold(true)));
        lines.add(Component.translatable(def.translationKey() + ".desc").withStyle(ChatFormatting.GRAY));
        for (SkillEffect effect : def.effects()) lines.add(describe(effect));
        State s = state(def, p);
        String blocker = Skills.purchaseBlocker(def, p.skillSet(), p.skillPoints(), p.born());
        lines.add(s == State.OWNED ? Component.translatable(KEY + "owned").withStyle(ChatFormatting.GREEN)
                : blocker == null ? Component.translatable(KEY + "buy", def.cost()).withStyle(ChatFormatting.YELLOW)
                : Component.translatable("msg.respectcraft.skill_blocked." + blocker).withStyle(ChatFormatting.RED));
        return lines;
    }

    private static Component describe(SkillEffect e) {
        return switch (e.kind()) {
            case ATTRIBUTE_ADD -> Component.translatable("skilleffect.respectcraft.add", fmt(e.amount()),
                    Component.translatable("attribute.name." + attributePath(e.target()))).withStyle(ChatFormatting.BLUE);
            case ATTRIBUTE_MULTIPLY -> Component.translatable("skilleffect.respectcraft.multiply", Math.round(e.amount() * 100),
                    Component.translatable("attribute.name." + attributePath(e.target()))).withStyle(ChatFormatting.BLUE);
            case NEED_RATE -> legacyNeed(e.target())
                    // Hidratacao/energia sao legadas (Etapa 4.5): o efeito fica guardado, mas nao faz nada.
                    ? Component.translatable("skilleffect.respectcraft.need_legacy",
                            Component.translatable("need.respectcraft." + e.target())).withStyle(ChatFormatting.DARK_GRAY)
                    : Component.translatable("skilleffect.respectcraft.need", Math.round((1 - e.amount()) * 100),
                            Component.translatable("need.respectcraft." + e.target())).withStyle(ChatFormatting.AQUA);
            case MOOD_BONUS -> Component.translatable("skilleffect.respectcraft.mood", Math.round(e.amount() / 100)).withStyle(ChatFormatting.AQUA);
            case FINISHER -> Component.translatable("skilleffect.respectcraft.finisher",
                    Component.translatable("finisher." + e.target().replace(':', '.'))).withStyle(ChatFormatting.LIGHT_PURPLE);
        };
    }

    private static boolean legacyNeed(String id) {
        Need need = Need.byId(id);
        return need != null && !need.active();
    }

    private static String attributePath(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null ? id : rl.getPath();
    }

    private static String fmt(double value) {
        return value == Math.rint(value) ? String.valueOf((int) value) : String.valueOf(value);
    }

    /** Linha grossa (retangulo girado) com a opacidade da revelacao. */
    private static void line(HoloCanvas c, float x1, float y1, float x2, float y2, float thickness, int argb) {
        float dx = x2 - x1, dy = y2 - y1;
        float len = Mth.sqrt(dx * dx + dy * dy);
        if (len < 0.01F) return;
        c.g.pose().pushPose();
        c.g.pose().translate(x1, y1, 0F);
        c.g.pose().mulPose(com.mojang.math.Axis.ZP.rotation((float) Math.atan2(dy, dx)));
        c.fill(0F, -thickness / 2F, len, thickness, argb);
        c.g.pose().popPose();
    }

    /** Fundo do ramo repetido (o recorte ja foi feito na superficie). */
    private static void tile(HoloCanvas c, ResourceLocation tex, float x, float y, float w, float h, int size, float offX, float offY) {
        HoloSurface.accumulate();
        c.g.setColor(0.55F, 0.7F, 0.8F, 0.55F * c.alpha);
        float startX = x - Math.floorMod(Math.round(offX), size);
        float startY = y - Math.floorMod(Math.round(offY), size);
        for (float ty = startY; ty < y + h; ty += size) {
            for (float tx = startX; tx < x + w; tx += size) {
                c.g.blit(tex, Math.round(tx), Math.round(ty), 0, 0, size, size, size, size);
            }
        }
        c.g.setColor(1F, 1F, 1F, 1F);
    }
}
