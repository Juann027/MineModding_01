package com.tupinicraft.skilltree;

import com.tupinicraft.TupiniCraft;
import com.tupinicraft.config.ServerSettings;
import com.tupinicraft.economy.Money;
import com.tupinicraft.economy.WalletService;
import com.tupinicraft.mandate.MandateService;
import com.tupinicraft.network.SkillDefinitionsPayload;
import com.tupinicraft.network.SkillNoticePayload;
import com.tupinicraft.network.SkillStatePayload;
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
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * Árvores de habilidades no servidor (autoritativo): compra com dinheiro ou pontos de cargo, efeitos
 * (modificadores de atributo) enquanto o requisito vale, suspensão/restauração automática e sincronização
 * com a tela do cliente (UI Lib).
 */
public final class SkillTreeService {
    /** Frequência da checagem de requisitos (4x por segundo): um efeito sem requisito some em até 0,25 s. */
    private static final int ENFORCE_INTERVAL_TICKS = ServerSettings.TICKS_PER_SECOND / 4;

    /**
     * Último estado enviado a cada jogador, só para não reenviar o mesmo pacote (cache de rede, não é estado do
     * mundo; some com o jogador). Uma entidade nova (ao renascer) recebe o estado de novo.
     */
    private static final Map<ServerPlayer, SkillStatePayload> LAST_SENT = Collections.synchronizedMap(new WeakHashMap<>());

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

    public static SkillTreeState state(ServerPlayer player) {
        return player.getData(ModAttachments.SKILL_TREE_STATE);
    }

    /** Compra uma skill: valida tudo no servidor e cobra. O efeito entra na próxima checagem (até 0,25 s). */
    public static Outcome purchase(ServerPlayer player, String skillId) {
        Optional<SkillDef> found = SkillCatalog.get().skill(skillId);
        if (found.isEmpty()) {
            return Outcome.fail("Habilidade desconhecida: " + skillId + ".");
        }
        SkillDef skill = found.get();
        SkillRule rule = skill.rule();
        SkillTreeState state = state(player);
        if (state.isUnlocked(skillId)) {
            return Outcome.fail("Você já comprou " + skill.name() + ".");
        }
        if (skill.parent().isPresent() && !state.isUnlocked(skill.parent().get())) {
            String parentName = SkillCatalog.get().skill(skill.parent().get()).map(SkillDef::name).orElse(skill.parent().get());
            return Outcome.fail("Compre antes \"" + parentName + "\" (a skill anterior desta árvore).");
        }
        SkillPlayerContext context = context(player);
        Optional<SkillGate.Problem> problem = SkillGate.purchaseProblem(rule, context);
        if (problem.isPresent()) {
            return Outcome.fail(describe(problem.get(), rule, context));
        }
        // Cobra (XP político nunca é consumido).
        if (rule.kind() == SkillTreeKind.OFFICE) {
            if (!changeOfficePoints(player, rule.office().orElseThrow(), -rule.officePointCost())) {
                return Outcome.fail("Pontos de " + rule.office().orElseThrow().displayName() + " insuficientes.");
            }
        } else if (!WalletService.debit(player, rule.moneyCost())) {
            return Outcome.fail("Saldo insuficiente.");
        }
        player.setData(ModAttachments.SKILL_TREE_STATE, state.withUnlocked(skillId));
        enforce(player);
        TupiniCraft.LOGGER.info("{} comprou a skill {}", player.getGameProfile().getName(), skillId);
        return Outcome.ok("Habilidade comprada: " + skill.name() + " (" + cost(rule) + ").");
    }

    /** Compra pedida pela tela da árvore (clique num nó): responde com uma notificação na tela. */
    public static void purchaseFromScreen(ServerPlayer player, ResourceLocation skillId) {
        String id = skillId.toString();
        Outcome outcome = purchase(player, id);
        if (!outcome.success()) {
            notify(player, false, "Não foi possível comprar", outcome.message());
            return;
        }
        SkillDef skill = SkillCatalog.get().skill(id).orElseThrow();
        boolean active = !state(player).suspended().contains(id);
        notify(player, true, active ? "Habilidade comprada e ativada" : "Habilidade comprada (suspensa)",
                Component.literal(skill.name() + " — " + cost(skill.rule())));
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

    /** 4x por segundo: aplica/suspende efeitos conforme os requisitos e sincroniza a tela se algo mudou. */
    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % ENFORCE_INTERVAL_TICKS != 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            enforce(player);
        }
    }

    static void enforce(ServerPlayer player) {
        SkillCatalogData catalog = SkillCatalog.get();
        SkillPlayerContext context = context(player);
        SkillTreeState state = state(player);
        SkillTreeState updated = state;
        Map<String, List<SkillEffectDef>> activeEffects = new LinkedHashMap<>();
        for (String skillId : state.unlocked()) {
            Optional<SkillDef> found = catalog.skill(skillId);
            if (found.isEmpty()) {
                // A compra permanece salva; os efeitos antigos são removidos na reconciliação abaixo.
                if (!updated.suspended().contains(skillId)) {
                    updated = updated.withSuspended(skillId, true);
                }
                continue;
            }
            SkillDef skill = found.get();
            boolean allowed = SkillGate.effectAllowed(skill.rule(), context);
            if (allowed) {
                activeEffects.put(skillId, skill.effects());
            }
            boolean wasSuspended = updated.suspended().contains(skillId);
            if (!allowed && !wasSuspended) {
                updated = updated.withSuspended(skillId, true);
                Component reason = Component.literal(skill.name() + ": " + requirementText(skill.rule()) + ". A compra continua salva.");
                player.sendSystemMessage(Component.literal("Habilidade suspensa — ").append(reason).withStyle(ChatFormatting.YELLOW));
                notify(player, false, "Habilidade suspensa", reason);
            } else if (allowed && wasSuspended) {
                updated = updated.withSuspended(skillId, false);
                player.sendSystemMessage(Component.literal("Habilidade restaurada: " + skill.name() + ".").withStyle(ChatFormatting.GREEN));
                notify(player, true, "Habilidade restaurada", Component.literal(skill.name()));
            }
        }
        SkillEffects.reconcile(player, activeEffects);
        if (!updated.equals(state)) {
            player.setData(ModAttachments.SKILL_TREE_STATE, updated);
        }
        syncState(player, catalog, context, updated);
    }

    private static void syncState(ServerPlayer player, SkillCatalogData catalog, SkillPlayerContext context, SkillTreeState state) {
        List<String> visible = new ArrayList<>();
        for (SkillTreeDef tree : catalog.treesInOrder()) {
            boolean bought = state.unlocked().stream().anyMatch(id -> catalog.skill(id).filter(s -> s.tree().equals(tree.id())).isPresent());
            if (SkillGate.treeVisible(tree, context, bought)) {
                visible.add(tree.id());
            }
        }
        SkillStatePayload payload = new SkillStatePayload(List.copyOf(state.unlocked()), List.copyOf(state.suspended()), visible);
        if (!payload.equals(LAST_SENT.get(player))) {
            LAST_SENT.put(player, payload);
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    /** Ao entrar e após /reload: envia as definições das árvores (o estado vai logo em seguida, no enforce). */
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        SkillDefinitionsPayload definitions = definitionsPayload(SkillCatalog.get());
        event.getRelevantPlayers().forEach(player -> {
            PacketDistributor.sendToPlayer(player, definitions);
            LAST_SENT.remove(player);
        });
    }

    static void onCatalogReloaded() {
        LAST_SENT.clear();
    }

    private static SkillDefinitionsPayload definitionsPayload(SkillCatalogData catalog) {
        List<SkillDefinitionsPayload.TreeView> trees = new ArrayList<>();
        List<SkillDefinitionsPayload.SkillView> skills = new ArrayList<>();
        for (SkillTreeDef tree : catalog.treesInOrder()) {
            trees.add(new SkillDefinitionsPayload.TreeView(tree.id(), tree.name(), tree.icon(), tree.background()));
            catalog.allSkills().stream().filter(s -> s.tree().equals(tree.id()) && s.parent().isEmpty())
                    .forEach(root -> addWithChildren(catalog, root, skills));
        }
        return new SkillDefinitionsPayload(trees, skills);
    }

    private static void addWithChildren(SkillCatalogData catalog, SkillDef skill, List<SkillDefinitionsPayload.SkillView> out) {
        out.add(new SkillDefinitionsPayload.SkillView(skill.id(), skill.tree(), skill.parent().orElse(""),
                new SkillDefinitionsPayload.Display(skill.name(), skill.description(), skill.icon(), skill.frame(),
                        "Custo: " + cost(skill.rule()), requirementText(skill.rule()))));
        catalog.childrenOf(skill.id()).forEach(child -> addWithChildren(catalog, child, out));
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
}
