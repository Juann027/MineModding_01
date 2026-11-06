package com.tupinicraft.client;

import com.tupinicraft.TupiniCraft;
import com.tupinicraft.election.ElectionManager;
import com.tupinicraft.election.ElectionPhase;
import com.tupinicraft.network.ElectionSyncPayload;
import com.tupinicraft.player.PoliticalData;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import java.util.ArrayList;
import java.util.List;

/**
 * HUD político (somente cliente), desenhado com camadas nativas do NeoForge:
 * <ul>
 *   <li>barra de XP político logo acima da barra de XP vanilla (só quando ela existe, isto é,
 *       fora do Criativo e do Espectador);</li>
 *   <li>painel discreto no canto superior direito (cargo, popularidade, XP) e, se houver,
 *       o painel da eleição logo abaixo.</li>
 * </ul>
 * Lê apenas os caches do cliente; não envia pacotes.
 */
public final class PoliticalHud {
    private static final int PANEL_BACKGROUND = 0x90000000;
    private static final int PANEL_PADDING = 4;
    private static final int LINE_HEIGHT = 10;
    private static final int SCREEN_MARGIN = 2;

    private static final int XP_BAR_WIDTH = 182;
    private static final int XP_BAR_HEIGHT = 3;
    /** Espaço que a barra ocupa acima da barra vanilla; corações/fome sobem esse tanto. */
    private static final int XP_BAR_RESERVED_HEIGHT = 4;
    private static final int XP_BAR_BACKGROUND = 0xFF10203A;
    private static final int XP_BAR_FILL = 0xFF009C3B;
    private static final int XP_BAR_FULL = 0xFFFFDF00;

    private PoliticalHud() {
    }

    static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.EXPERIENCE_BAR, TupiniCraft.id("political_xp_bar"), PoliticalHud::renderXpBar);
        event.registerAbove(VanillaGuiLayers.EFFECTS, TupiniCraft.id("political_panel"), PoliticalHud::renderPanels);
        event.registerAboveAll(TupiniCraft.id("election_announcement"), AnnouncementOverlay::render);
    }

    // ------------------------------------------------------------------ barra de XP político

    private static void renderXpBar(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        PoliticalData data = ClientPoliticalCache.get();
        // Mesmo critério da barra de XP vanilla: só no Sobrevivência/Aventura. No Criativo e no
        // Espectador a barra não é desenhada e nada é reservado em leftHeight/rightHeight.
        if (data == null || minecraft.player == null || minecraft.gameMode == null
                || !minecraft.gameMode.hasExperience()) {
            return;
        }
        int x = graphics.guiWidth() / 2 - XP_BAR_WIDTH / 2;
        // A barra vanilla começa em guiHeight - 29; deixamos 1 px de espaço entre as duas.
        int y = graphics.guiHeight() - 29 - 1 - XP_BAR_HEIGHT;

        float progress = xpProgress(data);
        int filled = Math.round(XP_BAR_WIDTH * progress);
        graphics.fill(x, y, x + XP_BAR_WIDTH, y + XP_BAR_HEIGHT, XP_BAR_BACKGROUND);
        if (filled > 0) {
            graphics.fill(x, y, x + filled, y + XP_BAR_HEIGHT, progress >= 1.0F ? XP_BAR_FULL : XP_BAR_FILL);
        }

        // Empurra corações, armadura e fome para cima, sem sobrepor a barra.
        minecraft.gui.leftHeight += XP_BAR_RESERVED_HEIGHT;
        minecraft.gui.rightHeight += XP_BAR_RESERVED_HEIGHT;
    }

    /** Progresso do XP político entre o requisito do cargo atual e o do próximo (valores do servidor). */
    static float xpProgress(PoliticalData data) {
        int to = ClientPoliticalCache.nextOfficeMinXp();
        if (to < 0) {
            return 1.0F;
        }
        int from = ClientPoliticalCache.currentOfficeMinXp();
        if (to <= from) {
            return data.politicalXp() >= to ? 1.0F : 0.0F;
        }
        return Math.max(0.0F, Math.min(1.0F, (data.politicalXp() - from) / (float) (to - from)));
    }

    // ------------------------------------------------------------------ painéis do canto superior direito

    private static void renderPanels(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        PoliticalData data = ClientPoliticalCache.get();
        if (data == null || minecraft.player == null || minecraft.getDebugOverlay().showDebugScreen()) {
            return;
        }
        Font font = minecraft.font;
        int y = topOffsetBelowEffects(minecraft);

        List<Component> political = new ArrayList<>();
        political.add(label("Cargo: ", data.office().displayName()));
        political.add(label("Popularidade: ", String.valueOf(data.popularity())));
        // "atual/meta" só quando o próximo cargo exige XP (evita o confuso "0/0" para Vereador).
        int nextMinXp = ClientPoliticalCache.nextOfficeMinXp();
        String xpText = nextMinXp > 0 ? data.politicalXp() + "/" + nextMinXp : String.valueOf(data.politicalXp());
        political.add(label("XP político: ", xpText));
        y = drawPanel(graphics, font, political, y) + SCREEN_MARGIN;

        List<Component> election = electionLines();
        if (!election.isEmpty()) {
            drawPanel(graphics, font, election, y);
        }
    }

    private static List<Component> electionLines() {
        ElectionSyncPayload state = ClientElectionCache.get();
        ElectionPhase phase = state.electionPhase();
        List<Component> lines = new ArrayList<>();
        if (phase == ElectionPhase.INACTIVE) {
            return lines;
        }
        lines.add(Component.literal("ELEIÇÃO: " + phase.displayName() + " (")
                .append(ElectionManager.formatTime(ClientElectionCache.displayTicksRemaining()) + ")")
                .withStyle(ChatFormatting.GOLD));
        for (int index = 0; index < state.races().size(); index++) {
            ElectionSyncPayload.RaceView race = state.races().get(index);
            switch (phase) {
                case REGISTRATION -> lines.add(Component.literal(race.label() + ": "
                        + race.candidates().size() + " candidato(s)").withStyle(ChatFormatting.WHITE));
                case VOTING -> lines.add(Component.literal(race.label() + ": ")
                        .withStyle(ChatFormatting.WHITE)
                        .append(state.hasVotedIn(index)
                                ? Component.literal("voto registrado ✔").withStyle(ChatFormatting.GREEN)
                                : Component.literal("use a Urna!").withStyle(ChatFormatting.YELLOW)));
                case COUNTING -> {
                    lines.add(Component.literal(race.label() + " — apurados " + race.revealedBallots()
                            + "/" + race.totalBallots()).withStyle(ChatFormatting.WHITE));
                    for (ElectionSyncPayload.CandidateView candidate : race.candidates()) {
                        lines.add(label("  " + candidate.name() + (candidate.npc() ? " [aldeão]" : "") + ": ",
                                Math.max(0, candidate.shownScore()) + " pts"));
                    }
                }
                default -> {
                }
            }
        }
        return lines;
    }

    /** Desenha um painel alinhado à direita e devolve o y logo abaixo dele. */
    private static int drawPanel(GuiGraphics graphics, Font font, List<Component> lines, int y) {
        int width = 0;
        for (Component line : lines) {
            width = Math.max(width, font.width(line));
        }
        width += PANEL_PADDING * 2;
        int height = lines.size() * LINE_HEIGHT + PANEL_PADDING * 2 - 2;
        int x = graphics.guiWidth() - width - SCREEN_MARGIN;
        graphics.fill(x, y, x + width, y + height, PANEL_BACKGROUND);
        for (int i = 0; i < lines.size(); i++) {
            graphics.drawString(font, lines.get(i), x + PANEL_PADDING, y + PANEL_PADDING + i * LINE_HEIGHT, 0xFFFFFFFF, false);
        }
        return y + height;
    }

    /** Os ícones de efeitos vanilla ocupam o topo direito; o painel desce para não cobri-los. */
    private static int topOffsetBelowEffects(Minecraft minecraft) {
        boolean beneficial = false;
        boolean harmful = false;
        for (MobEffectInstance effect : minecraft.player.getActiveEffects()) {
            if (!effect.showIcon()) {
                continue;
            }
            if (effect.getEffect().value().isBeneficial()) {
                beneficial = true;
            } else {
                harmful = true;
            }
        }
        if (harmful) {
            return 52;
        }
        return beneficial ? 27 : SCREEN_MARGIN;
    }

    private static Component label(String label, String value) {
        return Component.literal(label).withStyle(ChatFormatting.GRAY)
                .append(Component.literal(value).withStyle(ChatFormatting.WHITE));
    }
}
