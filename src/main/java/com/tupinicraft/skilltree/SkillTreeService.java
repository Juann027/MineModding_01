package com.tupinicraft.skilltree;

import com.tupinicraft.TupiniCraft;
import com.tupinicraft.config.ServerSettings;
import com.tupinicraft.economy.Money;
import com.tupinicraft.economy.WalletService;
import com.tupinicraft.mandate.MandateService;
import com.tupinicraft.network.SkillNoticePayload;
import com.tupinicraft.player.OfficeProgress;
import com.tupinicraft.player.PoliticalData;
import com.tupinicraft.player.PoliticalDataService;
import com.tupinicraft.player.SkillProgression;
import com.tupinicraft.politics.PoliticalOffice;
import com.tupinicraft.registry.ModAttachments;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Regras do TupiniCraft sobre as skills (servidor autoritativo): compra com dinheiro ou pontos de cargo e
 * suspensão/restauração automática dos efeitos conforme os requisitos (região, partido, mandato).
 */
public final class SkillTreeService {
    /** Frequência da checagem de requisitos (4x por segundo): um efeito sem requisito some em até 0,25 s. */
    private static final int ENFORCE_INTERVAL_TICKS = ServerSettings.TICKS_PER_SECOND / 4;

    private SkillTreeService() {
    }

    /** Resultado de uma ação, com mensagem pronta. */
    public record Outcome(boolean success, Component message) {
        static Outcome ok(String text) {
            return new Outcome(true, Component.literal(text).withStyle(ChatFormatting.GREEN));
        }

        static Outcome fail(String text) {
            return new Outcome(false, Component.literal(text).withStyle(ChatFormatting.RED));
        }
    }

    public static SkillPlayerContext context(ServerPlayer player) {
        PoliticalData data = PoliticalDataService.get(player);
        Map<PoliticalOffice, Integer> points = new EnumMap<>(PoliticalOffice.class);
        data.officeProgress().forEach((office, progress) -> points.put(office, progress.skillPoints()));
        return new SkillPlayerContext(
                data.origin().map(origin -> origin.macroregion()),
                player.getData(ModAttachments.PARTY).partyId(),
                MandateService.activeOffice(player.server, player.getUUID()),
                data.politicalXp(),
                WalletService.balance(player),
                points);
    }

    /** Compra uma skill: valida tudo no servidor, cobra e desbloqueia no SkillLib. */
    public static Outcome purchase(ServerPlayer player, String skillId) {
        SkillBackend backend = SkillBackends.get();
        if (!backend.available()) {
            return Outcome.fail("As árvores de habilidades estão desativadas: o SkillLib não está instalado neste servidor.");
        }
        Optional<SkillRule> found = SkillRules.get(skillId);
        if (found.isEmpty()) {
            return Outcome.fail("Habilidade desconhecida: " + skillId + ". Veja a lista com /brasil skills.");
        }
        SkillRule rule = found.get();
        ResourceLocation id = ResourceLocation.parse(skillId);
        if (!backend.skillExists(id)) {
            return Outcome.fail("A habilidade " + skillId + " não foi carregada pelo SkillLib (verifique o datapack).");
        }
        if (backend.isUnlocked(player, id)) {
            return Outcome.fail("Você já desbloqueou " + skillId + ".");
        }
        for (ResourceLocation prerequisite : backend.prerequisites(id)) {
            if (!backend.isUnlocked(player, prerequisite)) {
                return Outcome.fail("Desbloqueie antes o pré-requisito " + prerequisite + ".");
            }
        }
        SkillPlayerContext context = context(player);
        Optional<SkillGate.Problem> problem = SkillGate.purchaseProblem(rule, context);
        if (problem.isPresent()) {
            return Outcome.fail(describe(problem.get(), rule, context));
        }

        // Cobra (XP político nunca é consumido) e desbloqueia; devolve se o SkillLib recusar.
        if (rule.kind() == SkillTreeKind.OFFICE) {
            PoliticalOffice office = rule.office().orElseThrow();
            if (!changeOfficePoints(player, office, -rule.officePointCost())) {
                return Outcome.fail("Pontos de " + office.displayName() + " insuficientes.");
            }
            if (!backend.unlock(player, id)) {
                changeOfficePoints(player, office, rule.officePointCost());
                return Outcome.fail("O SkillLib recusou o desbloqueio de " + skillId + ".");
            }
        } else {
            if (!WalletService.debit(player, rule.moneyCost())) {
                return Outcome.fail("Saldo insuficiente.");
            }
            if (!backend.unlock(player, id)) {
                WalletService.credit(player, rule.moneyCost());
                return Outcome.fail("O SkillLib recusou o desbloqueio de " + skillId + ".");
            }
        }
        TupiniCraft.LOGGER.info("{} comprou a skill {}", player.getGameProfile().getName(), skillId);
        return Outcome.ok("Habilidade desbloqueada: " + skillId + " (" + cost(rule) + "). Ligue e desligue no painel do SkillLib (tecla K).");
    }

    /**
     * Compra pedida pelo painel do SkillLib (clique numa skill bloqueada): compra, já ativa se couber e
     * responde com uma notificação na tela.
     */
    public static void purchaseFromPanel(ServerPlayer player, ResourceLocation skillId) {
        String id = skillId.toString();
        Outcome outcome = purchase(player, id);
        if (!outcome.success()) {
            notify(player, false, "Não foi possível comprar", outcome.message());
            return;
        }
        SkillBackend backend = SkillBackends.get();
        String price = SkillRules.get(id).map(SkillTreeService::cost).orElse("");
        if (backend.canActivateMore(player)) {
            backend.setActive(player, skillId, true);
            notify(player, true, "Habilidade comprada e ativada", Component.literal(skillName(id) + " — " + price));
        } else {
            notify(player, true, "Habilidade comprada", Component.literal(skillName(id)
                    + " — limite de skills ativas atingido; desative outra para ativá-la."));
        }
    }

    private static String skillName(String skillId) {
        return SkillBackends.get().displayName(ResourceLocation.parse(skillId));
    }

    private static void notify(ServerPlayer player, boolean success, String title, Component message) {
        PacketDistributor.sendToPlayer(player, new SkillNoticePayload(success,
                Component.literal(title).withStyle(success ? ChatFormatting.GREEN : ChatFormatting.RED), message));
    }

    private static boolean changeOfficePoints(ServerPlayer player, PoliticalOffice office, int delta) {
        PoliticalData data = PoliticalDataService.get(player);
        Map<PoliticalOffice, OfficeProgress> progress = SkillProgression.copy(data.officeProgress());
        OfficeProgress current = progress.getOrDefault(office, OfficeProgress.EMPTY);
        int newPoints = current.skillPoints() + delta;
        if (newPoints < 0) {
            return false;
        }
        progress.put(office, new OfficeProgress(newPoints, current.onlineSeconds()));
        PoliticalDataService.set(player, data.withOfficeProgress(progress));
        return true;
    }

    /** 4x por segundo: suspende efeitos sem requisito e restaura os suspensos quando o requisito volta. */
    public static void tick(MinecraftServer server) {
        SkillBackend backend = SkillBackends.get();
        if (!backend.available() || server.getTickCount() % ENFORCE_INTERVAL_TICKS != 0 || SkillRules.all().isEmpty()) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            enforce(player, backend);
        }
    }

    static void enforce(ServerPlayer player, SkillBackend backend) {
        SkillPlayerContext context = context(player);
        SkillTreeState state = player.getData(ModAttachments.SKILL_TREE_STATE);
        SkillTreeState updated = state;
        for (SkillRule rule : SkillRules.all()) {
            ResourceLocation id = ResourceLocation.parse(rule.skillId());
            if (!backend.isUnlocked(player, id)) {
                continue;
            }
            boolean allowed = SkillGate.effectAllowed(rule, context);
            boolean active = backend.isActive(player, id);
            boolean suspended = updated.suspended().contains(rule.skillId());
            if (active && !allowed) {
                backend.setActive(player, id, false);
                updated = updated.with(rule.skillId(), true);
                Component reason = Component.literal(skillName(rule.skillId()) + ": " + requirementText(rule)
                        + ". O progresso continua salvo.");
                player.sendSystemMessage(Component.literal("Habilidade suspensa — ").append(reason).withStyle(ChatFormatting.YELLOW));
                notify(player, false, "Habilidade suspensa", reason);
            } else if (suspended && allowed) {
                if (!active && backend.canActivateMore(player)) {
                    backend.setActive(player, id, true);
                    player.sendSystemMessage(Component.literal("Habilidade restaurada: " + rule.skillId() + ".")
                            .withStyle(ChatFormatting.GREEN));
                    notify(player, true, "Habilidade restaurada", Component.literal(skillName(rule.skillId())));
                }
                updated = updated.with(rule.skillId(), false);
            }
        }
        if (updated != state) {
            player.setData(ModAttachments.SKILL_TREE_STATE, updated);
        }
    }

    public static String cost(SkillRule rule) {
        String base = rule.kind() == SkillTreeKind.OFFICE
                ? rule.officePointCost() + " ponto(s) de " + rule.office().orElseThrow().displayName()
                : Money.format(rule.moneyCost());
        return rule.minPoliticalXp() > 0 ? base + ", exige " + rule.minPoliticalXp() + " XP político (não consumido)" : base;
    }

    public static String requirementText(SkillRule rule) {
        return switch (rule.kind()) {
            case PERSONAL -> "disponível para todos";
            case REGIONAL -> "exige origem na região " + rule.region().orElseThrow().displayName();
            case PARTY -> "exige filiação ao partido " + rule.party().orElseThrow();
            case OFFICE -> "exige mandato vigente de " + rule.office().orElseThrow().displayName();
        };
    }

    private static String describe(SkillGate.Problem problem, SkillRule rule, SkillPlayerContext context) {
        return switch (problem) {
            case WRONG_REGION -> "Esta habilidade é da região " + rule.region().orElseThrow().displayName()
                    + "; sua origem é da região " + context.macroregion().map(r -> r.displayName()).orElse("nenhuma") + ".";
            case NOT_IN_PARTY -> "Esta habilidade é do partido " + rule.party().orElseThrow() + "; você "
                    + context.party().map(p -> "é filiado a " + p).orElse("não é filiado a nenhum partido") + ".";
            case INSUFFICIENT_XP -> "XP político insuficiente: você tem " + context.politicalXp() + ", precisa de "
                    + rule.minPoliticalXp() + " (o XP não é gasto).";
            case INSUFFICIENT_MONEY -> "Saldo insuficiente: você tem " + Money.format(context.money()) + ", custa "
                    + Money.format(rule.moneyCost()) + ".";
            case INSUFFICIENT_OFFICE_POINTS -> "Pontos de " + rule.office().orElseThrow().displayName()
                    + " insuficientes: você tem " + context.pointsOf(rule.office().orElseThrow()) + ", custa "
                    + rule.officePointCost() + " (ganhos exercendo o mandato online).";
        };
    }

    /**
     * Ao renascer: o SkillLib 1.4.0 sincroniza durante a clonagem do jogador, antes do cliente trocar de
     * entidade, e o painel passa a mostrar as skills compradas como bloqueadas. Reenviamos depois do respawn.
     */
    public static void onRespawn(ServerPlayer player) {
        SkillBackends.get().resync(player);
    }

    /** Usado no perfil e na lista; true se o SkillLib está ativo neste servidor. */
    public static boolean enabled() {
        return SkillBackends.get().available();
    }
}
