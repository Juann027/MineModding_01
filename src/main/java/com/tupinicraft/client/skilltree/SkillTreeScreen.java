package com.tupinicraft.client.skilltree;

import com.tupinicraft.TupiniCraft;
import com.tupinicraft.client.character.CharacterHubScreen;
import com.tupinicraft.client.ui.TupiButton;
import com.tupinicraft.client.ui.TupiScreen;
import com.tupinicraft.client.ui.Ui;
import com.tupinicraft.network.GameplayNetworking;
import com.tupinicraft.network.ProfileClientCache;
import com.tupinicraft.profile.CharacterProfile;
import com.tupinicraft.skill.SkillBranch;
import com.tupinicraft.skill.SkillDef;
import com.tupinicraft.skill.SkillEffect;
import com.tupinicraft.skill.Skills;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Arvore de habilidades: uma aba por ramo, cada uma com fundo, cor e icone proprios; nos com moldura propria
 * (bloqueado, disponivel, comprado), linhas com pulso animado ate os nos disponiveis e VFX na compra.
 * A compra e pedida ao servidor; a tela so reage a ficha sincronizada.
 */
public final class SkillTreeScreen extends TupiScreen {
    private static final ResourceLocation NODE = TupiniCraft.id("textures/gui/skilltree/node.png");
    private static final String KEY = "gui.tupinicraft.skills.";
    private static final int GRID_X = 64;
    private static final int GRID_Y = 44;
    private static final int NODE_SIZE = 26;
    private static SkillBranch lastBranch = SkillBranch.COMBATE;

    private SkillBranch branch = lastBranch;
    private int canvasX, canvasY, canvasW, canvasH;
    private float panX, panY;
    private boolean dragging;
    private double dragDistance;
    private Set<String> knownOwned = new HashSet<>();
    private long knownRevision = -1;
    private ResourceLocation pending;
    private final float[] hoverScale = new float[64];

    public SkillTreeScreen() {
        super(Component.translatable(KEY + "title"));
        knownOwned = ProfileClientCache.profile().skillSet();
        knownRevision = ProfileClientCache.revision();
    }

    @Override
    protected void init() {
        layoutPanel(440, 270);
        canvasX = left + 8;
        canvasY = top + 50;
        canvasW = panelWidth - 16;
        canvasH = panelHeight - 74;
        int tabW = Math.min(110, (panelWidth - 24) / SkillBranch.values().length);
        int i = 0;
        for (SkillBranch b : SkillBranch.values()) {
            addRenderableWidget(new TupiButton(left + 8 + i * (tabW + 4), top + 26, tabW, 20,
                    Component.translatable("skillbranch.tupinicraft." + b.id()), button -> select(b))
                    .icon(b.icon()).accent(b.accent()).selected(b == branch));
            i++;
        }
        addRenderableWidget(new TupiButton(left + panelWidth - 78, top + panelHeight - 22, 70, 16,
                Component.translatable(KEY + "back"), b -> minecraft.setScreen(new CharacterHubScreen())));
        centerTree();
    }

    private void select(SkillBranch b) {
        if (b == branch) return;
        branch = b;
        lastBranch = b;
        rebuildWidgets();
        vfx.flash(canvasX + canvasW / 2F, canvasY + canvasH / 2F, canvasW / 2F, Ui.alpha(b.accent(), 0.5F), 12);
    }

    /** Centraliza a arvore da aba no quadro. */
    private void centerTree() {
        List<SkillDef> defs = Skills.of(branch);
        int minX = defs.stream().mapToInt(SkillDef::x).min().orElse(0);
        int maxX = defs.stream().mapToInt(SkillDef::x).max().orElse(0);
        panX = -((minX + maxX) / 2F) * GRID_X;
        panY = 0;
    }

    private float nodeCenterX(SkillDef def) { return canvasX + canvasW / 2F + panX + def.x() * GRID_X; }
    private float nodeCenterY(SkillDef def) { return canvasY + canvasH / 2F + panY + def.y() * GRID_Y; }

    private enum State { LOCKED, AVAILABLE, OWNED }

    private State state(SkillDef def, CharacterProfile p) {
        if (p.hasSkill(def.id().toString())) return State.OWNED;
        for (ResourceLocation parent : def.parents()) if (!p.hasSkill(parent.toString())) return State.LOCKED;
        return State.AVAILABLE;
    }

    @Override
    public void tick() {
        super.tick();
        CharacterProfile p = ProfileClientCache.profile();
        if (ProfileClientCache.revision() != knownRevision) {
            knownRevision = ProfileClientCache.revision();
            for (String id : p.skills()) {
                if (!knownOwned.contains(id)) celebrate(Skills.get(id));
            }
            knownOwned = p.skillSet();
            pending = null;
        }
        // Brasas no fundo, na cor da aba.
        if (ticks % 4 == 0) vfx.ember(canvasX + (float) Math.random() * canvasW, canvasY + canvasH - 2, Ui.alpha(branch.accent(), 0.6F));
    }

    private void celebrate(SkillDef def) {
        if (def == null) return;
        if (def.branch() != branch) select(def.branch());
        float x = nodeCenterX(def), y = nodeCenterY(def);
        vfx.flash(x, y, 46, def.branch().accent(), 14);
        vfx.ring(x, y, 44, def.branch().accent(), 18);
        vfx.ring(x, y, 28, 0xFFFFFFFF, 12);
        vfx.burst(x, y, 40, def.branch().accent(), 3.5F);
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        CharacterProfile p = ProfileClientCache.profile();
        // Quadro com o fundo proprio da aba, deslocado junto com a arvore (paralaxe leve).
        g.fill(canvasX - 1, canvasY - 1, canvasX + canvasW + 1, canvasY + canvasH + 1, 0xFF06080A);
        Ui.tile(g, branch.background(), canvasX, canvasY, canvasW, canvasH, 64, Math.round(-panX * 0.5F), Math.round(-panY * 0.5F));
        g.fillGradient(canvasX, canvasY, canvasX + canvasW, canvasY + 24, 0xA0000000, 0x00000000);
        g.fillGradient(canvasX, canvasY + canvasH - 24, canvasX + canvasW, canvasY + canvasH, 0x00000000, 0xA0000000);

        g.enableScissor(canvasX, canvasY, canvasX + canvasW, canvasY + canvasH);
        List<SkillDef> defs = Skills.of(branch);
        float time = ticks + partialTick;
        // Ligacoes (cotovelo: horizontal ate o meio, vertical, horizontal)
        for (SkillDef def : defs) {
            State s = state(def, p);
            for (ResourceLocation parentId : def.parents()) {
                SkillDef parent = Skills.get(parentId);
                if (parent == null) continue;
                boolean parentOwned = p.hasSkill(parentId.toString());
                int color = s == State.OWNED ? branch.accent() : parentOwned ? 0xFFC9D6E2 : 0xFF3B4048;
                float x1 = nodeCenterX(parent), y1 = nodeCenterY(parent), x2 = nodeCenterX(def), y2 = nodeCenterY(def);
                float mx = (x1 + x2) / 2F;
                Ui.line(g, x1, y1, mx, y1, 5, 0xFF06080A);
                Ui.line(g, mx, y1, mx, y2, 5, 0xFF06080A);
                Ui.line(g, mx, y2, x2, y2, 5, 0xFF06080A);
                Ui.line(g, x1, y1, mx + 1, y1, 3, color);
                Ui.line(g, mx, y1 - 1, mx, y2 + 1, 3, color);
                Ui.line(g, mx - 1, y2, x2, y2, 3, color);
                if (parentOwned && s != State.OWNED) {
                    // Pulso caminhando ate o no disponivel
                    float total = Math.abs(mx - x1) + Math.abs(y2 - y1) + Math.abs(x2 - mx);
                    float d = (time * 1.6F) % Math.max(1, total);
                    float px, py;
                    if (d < Math.abs(mx - x1)) { px = x1 + Math.signum(mx - x1) * d; py = y1; }
                    else if (d < Math.abs(mx - x1) + Math.abs(y2 - y1)) { px = mx; py = y1 + Math.signum(y2 - y1) * (d - Math.abs(mx - x1)); }
                    else { px = mx + Math.signum(x2 - mx) * (d - Math.abs(mx - x1) - Math.abs(y2 - y1)); py = y2; }
                    Ui.glow(g, px, py, 7, Ui.alpha(branch.accent(), 0.9F));
                }
            }
        }
        // Nos
        SkillDef hovered = hovered(mouseX, mouseY);
        for (int i = 0; i < defs.size(); i++) {
            SkillDef def = defs.get(i);
            State s = state(def, p);
            boolean hot = def == hovered;
            int idx = Math.min(i, hoverScale.length - 1);
            hoverScale[idx] = Mth.lerp(0.35F, hoverScale[idx], hot ? 1F : 0F);
            float scale = 1F + 0.12F * hoverScale[idx];
            float x = nodeCenterX(def), y = nodeCenterY(def);
            if (s == State.AVAILABLE) {
                boolean affordable = p.skillPoints() >= def.cost() && p.born();
                float pulse = 0.5F + 0.5F * Mth.sin(time * 0.15F);
                Ui.glow(g, x, y, 22 + pulse * 6, Ui.alpha(affordable ? branch.accent() : 0xFFC9D6E2, 0.35F + 0.3F * pulse));
            } else if (s == State.OWNED) {
                Ui.glow(g, x, y, 24, Ui.alpha(branch.accent(), 0.45F));
            }
            if (def.id().equals(pending)) Ui.glow(g, x, y, 30, Ui.alpha(0xFFFFFFFF, 0.4F + 0.3F * Mth.sin(time * 0.6F)));
            g.pose().pushPose();
            g.pose().translate(x, y, 0);
            g.pose().scale(scale, scale, 1F);
            int frame = s == State.OWNED ? 2 : s == State.AVAILABLE ? 1 : 0;
            g.blit(NODE, -NODE_SIZE / 2, -NODE_SIZE / 2, frame * 26, 0, 26, 26, 78, 26);
            if (s == State.LOCKED) g.setColor(0.35F, 0.35F, 0.4F, 1F);
            Ui.icon(g, def.icon(), -8, -8, 16);
            g.setColor(1F, 1F, 1F, 1F);
            g.pose().popPose();
            // custo
            String cost = String.valueOf(def.cost());
            int cx = Math.round(x + 9), cy = Math.round(y + 7);
            if (s != State.OWNED) {
                g.fill(cx - 1, cy - 1, cx + font.width(cost) + 3, cy + 9, 0xFF06080A);
                g.drawString(font, cost, cx + 1, cy, p.skillPoints() >= def.cost() ? Ui.GOLD : Ui.RED, false);
            }
        }
        g.disableScissor();

        // Cabecalho: pontos
        String pts = Component.translatable(KEY + "points", p.skillPoints()).getString();
        int w = font.width(pts) + 12;
        int bx = left + panelWidth - w - 10;
        g.fill(bx, top + 6, bx + w, top + 19, 0xFF06080A);
        g.fill(bx + 1, top + 7, bx + w - 1, top + 18, p.skillPoints() > 0 ? 0xFF6B5216 : 0xFF262D36);
        g.drawString(font, pts, bx + 6, top + 9, p.skillPoints() > 0 ? Ui.GOLD : Ui.MUTED, false);
        g.drawString(font, Component.translatable(p.born() ? KEY + "hint" : KEY + "unborn"), left + 10,
                top + panelHeight - 18, p.born() ? Ui.MUTED : Ui.RED, false);
    }

    @Override
    protected void renderOverlay(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        SkillDef def = hovered(mouseX, mouseY);
        if (def == null || dragging) return;
        CharacterProfile p = ProfileClientCache.profile();
        List<FormattedCharSequence> lines = new ArrayList<>();
        lines.add(Component.translatable(def.translationKey()).withStyle(s -> s.withColor(def.branch().accent() & 0xFFFFFF).withBold(true)).getVisualOrderText());
        lines.addAll(font.split(Component.translatable(def.translationKey() + ".desc").withStyle(ChatFormatting.GRAY), 200));
        for (SkillEffect effect : def.effects()) lines.add(describe(effect).getVisualOrderText());
        State s = state(def, p);
        String blocker = Skills.purchaseBlocker(def, p.skillSet(), p.skillPoints(), p.born());
        Component status = s == State.OWNED ? Component.translatable(KEY + "owned").withStyle(ChatFormatting.GREEN)
                : blocker == null ? Component.translatable(KEY + "buy", def.cost()).withStyle(ChatFormatting.YELLOW)
                : Component.translatable("msg.tupinicraft.skill_blocked." + blocker).withStyle(ChatFormatting.RED);
        lines.add(status.getVisualOrderText());
        g.renderTooltip(font, lines, mouseX, mouseY);
    }

    private static Component describe(SkillEffect e) {
        return switch (e.kind()) {
            case ATTRIBUTE_ADD -> Component.translatable("skilleffect.tupinicraft.add", fmt(e.amount()),
                    Component.translatable("attribute.name." + attributePath(e.target()))).withStyle(ChatFormatting.BLUE);
            case ATTRIBUTE_MULTIPLY -> Component.translatable("skilleffect.tupinicraft.multiply", Math.round(e.amount() * 100),
                    Component.translatable("attribute.name." + attributePath(e.target()))).withStyle(ChatFormatting.BLUE);
            case NEED_RATE -> Component.translatable("skilleffect.tupinicraft.need", Math.round((1 - e.amount()) * 100),
                    Component.translatable("need.tupinicraft." + e.target())).withStyle(ChatFormatting.AQUA);
            case MOOD_BONUS -> Component.translatable("skilleffect.tupinicraft.mood", Math.round(e.amount() / 100)).withStyle(ChatFormatting.AQUA);
            case FINISHER -> Component.translatable("skilleffect.tupinicraft.finisher",
                    Component.translatable("finisher." + e.target().replace(':', '.'))).withStyle(ChatFormatting.LIGHT_PURPLE);
        };
    }

    private static String attributePath(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null ? id : rl.getPath();
    }

    private static String fmt(double value) {
        return value == Math.rint(value) ? String.valueOf((int) value) : String.valueOf(value);
    }

    private SkillDef hovered(double mouseX, double mouseY) {
        if (mouseX < canvasX || mouseX >= canvasX + canvasW || mouseY < canvasY || mouseY >= canvasY + canvasH) return null;
        for (SkillDef def : Skills.of(branch)) {
            if (Math.abs(mouseX - nodeCenterX(def)) <= NODE_SIZE / 2F && Math.abs(mouseY - nodeCenterY(def)) <= NODE_SIZE / 2F) return def;
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (mouseX >= canvasX && mouseX < canvasX + canvasW && mouseY >= canvasY && mouseY < canvasY + canvasH) {
            dragging = true;
            dragDistance = 0;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging) {
            panX += (float) dragX;
            panY += (float) dragY;
            panX = Mth.clamp(panX, -GRID_X * 6F, GRID_X * 6F);
            panY = Mth.clamp(panY, -GRID_Y * 4F, GRID_Y * 4F);
            dragDistance += Math.abs(dragX) + Math.abs(dragY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging) {
            dragging = false;
            if (dragDistance < 4) tryPurchase(hovered(mouseX, mouseY));
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void tryPurchase(SkillDef def) {
        if (def == null) return;
        CharacterProfile p = ProfileClientCache.profile();
        String blocker = Skills.purchaseBlocker(def, p.skillSet(), p.skillPoints(), p.born());
        if (blocker != null) {
            Ui.playClick(0.6F);
            vfx.burst(nodeCenterX(def), nodeCenterY(def), 6, Ui.RED, 1.5F);
            return;
        }
        Ui.playClick(1.2F);
        pending = def.id();
        PacketDistributor.sendToServer(new GameplayNetworking.SkillPurchaseRequest(def.id()));
    }
}
