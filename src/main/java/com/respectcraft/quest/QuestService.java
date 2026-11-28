package com.respectcraft.quest;

import com.respectcraft.RespectCraft;
import com.respectcraft.finisher.BossDefeatEvent;
import com.respectcraft.finisher.FinisherExecution;
import com.respectcraft.finisher.FinisherParticipants;
import com.respectcraft.finisher.FinisherSessionEvent;
import com.respectcraft.network.QuestNetworking;
import com.respectcraft.network.QuestNetworking.ObjectiveView;
import com.respectcraft.network.QuestNetworking.QuestView;
import com.respectcraft.notice.HoloNotice;
import com.respectcraft.notice.HoloNotices;
import com.respectcraft.notice.NoticeKind;
import com.respectcraft.notice.NoticePresentation;
import com.respectcraft.notice.NoticeStyle;
import com.respectcraft.profile.CharacterProfile;
import com.respectcraft.profile.ProfileService;
import com.respectcraft.registry.ModAttachments;
import com.respectcraft.skill.SkillDef;
import com.respectcraft.skill.Skills;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Quest Engine no servidor (Etapa 4.14.2): o <b>unico dono</b> do progresso de missoes ({@link QuestLog} no
 * attachment {@code respectcraft:quests}). Traduz os eventos do jogo em {@link QuestEvent}, roda o motor puro
 * ({@link QuestEngine}), grava, avisa (janelas/cartoes privados por {@link HoloNotices}), concede recompensas e
 * sincroniza so o jogador dono. O cliente so pede ({@link QuestNetworking.QuestActionRequest}) e mostra.
 *
 * <p><b>Finalizacoes:</b> objetivos/condicoes {@code finisher} so contam {@code FinisherSessionEvent.Completed} com
 * {@code SUCCESS} e morte confirmada; CANCELLED ({@code Aborted}) e FAILED nunca. A execucao real tambem gera a
 * morte vanilla ({@code LivingDeathEvent}), que conta <i>so</i> para {@code kill}: cada tipo de objetivo tem uma
 * unica fonte, entao nada e contado duas vezes.
 *
 * <p><b>Recompensas e persistencia:</b> o resgate marca CLAIMED no registro <i>antes</i> de entregar. XP,
 * inventario, ficha (habilidades) e o registro de missoes ficam todos no arquivo do jogador e sao gravados juntos
 * pelo salvamento normal do Minecraft; o arquivo e trocado inteiro (arquivo temporario + troca), entao depois de uma
 * queda o jogador volta ou ao estado anterior inteiro (missao ainda COMPLETED, nada entregue) ou ao posterior
 * inteiro. Itens que nao cabem no inventario caem no chao aos pes do jogador; esses ficam no chunk, salvo a parte:
 * numa queda entre os dois salvamentos eles podem se perder. Nao ha atomicidade alem disso.
 */
public final class QuestService {
    private QuestService() {}

    private static final int MAX_REWARD_DEPTH = 3;

    /** Limite de pedidos por jogador (attachment sem save). */
    public static final class Limiter {
        long windowTick = Long.MIN_VALUE;
        int count;
    }

    // ================================================================================================ dados

    public static QuestLog get(ServerPlayer player) { return player.getData(ModAttachments.QUESTS); }

    static QuestCatalog catalog() { return QuestRegistry.catalog(); }

    static long now(MinecraftServer server) { return server.overworld().getGameTime(); }

    public static QuestContext context(ServerPlayer player) {
        MinecraftServer server = player.server;
        return new QuestContext() {
            public boolean hasAdvancement(ResourceLocation id) {
                if (id == null) return false;
                AdvancementHolder h = server.getAdvancements().get(id);
                return h != null && player.getAdvancements().getOrStartProgress(h).isDone();
            }
            public ResourceLocation dimension() { return player.level().dimension().location(); }
            public long now() { return QuestService.now(server); }
            public QuestState questState(ResourceLocation id) { return get(player).state(id); }
        };
    }

    // ================================================================================================ eventos

    public static void onAdvancement(AdvancementEvent.AdvancementEarnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        fire(player, QuestEvent.advancement(event.getAdvancement().id()));
    }

    /**
     * Etapa 4.15: conquista <b>sem display</b> (oculta, ex.: {@code respectcraft:time_warp/*}) concluida agora. O
     * NeoForge 21.1 so dispara {@code AdvancementEarnEvent} para conquistas com display; para as outras, a conclusao
     * vem por aqui. Conquistas com display nunca passam por aqui (sem contagem dupla).
     */
    public static void onAdvancementProgress(AdvancementEvent.AdvancementProgressEvent event) {
        if (event.getProgressType() != AdvancementEvent.AdvancementProgressEvent.ProgressType.GRANT) return;
        if (event.getAdvancement().value().display().isPresent() || !event.getAdvancementProgress().isDone()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        fire(player, QuestEvent.advancement(event.getAdvancement().id()));
    }

    /** Morte (LOWEST: so a que ninguem cancelou, ex. totem). Mata = {@code kill}; jogador morto = {@code fail_on_death}. */
    public static void onDeath(LivingDeathEvent event) {
        if (event.isCanceled()) return;
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) return;
        if (victim instanceof ServerPlayer dead) apply(dead, QuestEngine.death(get(dead), catalog(), context(dead)), 0);
        ServerPlayer killer = event.getSource().getEntity() instanceof ServerPlayer p ? p
                : victim.getKillCredit() instanceof ServerPlayer p2 ? p2 : null;
        if (killer == null || killer == victim) return;
        fire(killer, QuestEvent.kill(BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType()), tags(victim)));
    }

    /** Finalizacao concluida: so SUCCESS com morte confirmada conta (FAILED/CANCELLED nunca). */
    public static void onFinisherCompleted(FinisherSessionEvent.Completed event) {
        if (event.getOutcome() != FinisherExecution.Outcome.SUCCESS || !event.killConfirmed()) return;
        if (!(event.getAttacker() instanceof ServerPlayer player)) return;
        LivingEntity victim = event.getVictim();
        String kind = FinisherParticipants.kindOf(victim).name().toLowerCase(Locale.ROOT);
        fire(player, QuestEvent.finisher(BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType()), tags(victim),
                event.getFinisher() == null ? null : event.getFinisher().id(), kind));
    }

    public static void onBossDefeat(BossDefeatEvent event) {
        event.getCredited().ifPresent(player -> fire(player, QuestEvent.boss(
                BuiltInRegistries.ENTITY_TYPE.getKey(event.getBoss().getType()), tags(event.getBoss()),
                event.getMethod() == BossDefeatEvent.Method.FINISHER)));
    }

    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) fire(player, QuestEvent.dimension(event.getTo().location()));
    }

    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        apply(player, QuestEngine.reconcile(get(player), catalog(), context(player)), 0);
        snapshot(player);
    }

    /** {@code /reload}: definicoes novas valem para todos (descobertas, migracao de objetivos, novo snapshot). */
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() != null) return; // login: feito em onLogin
        event.getRelevantPlayers().forEach(player -> {
            apply(player, QuestEngine.reconcile(get(player), catalog(), context(player)), 0);
            snapshot(player);
        });
    }

    /** A cada {@link QuestRules#SWEEP_TICKS}: tempo limite e reabertura das repetiveis, so dos jogadores online. */
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % QuestRules.SWEEP_TICKS != 0) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            QuestLog log = get(player);
            if (log.quests().isEmpty()) continue;
            apply(player, QuestEngine.sweep(log, catalog(), context(player)), 0);
        }
    }

    private static Predicate<ResourceLocation> tags(LivingEntity e) {
        EntityType<?> type = e.getType();
        return t -> type.is(TagKey.create(Registries.ENTITY_TYPE, t));
    }

    /** Um acontecimento: so roda o motor se alguma missao depende do gatilho. */
    public static void fire(ServerPlayer player, QuestEvent event) {
        QuestCatalog cat = catalog();
        if (cat.objectiveCandidates(event.trigger()).isEmpty() && cat.discoveryCandidates(event.trigger()).isEmpty()) return;
        apply(player, QuestEngine.handle(get(player), cat, event, context(player)), 0);
    }

    // ================================================================================================ pedidos

    public static void handle(ServerPlayer player, QuestNetworking.QuestActionRequest req) {
        if (!allow(player)) return;
        QuestCatalog cat = catalog();
        ResourceLocation id = req.quest();
        QuestEngine.Result r = switch (req.action()) {
            case REFRESH -> null;
            case ACCEPT -> QuestEngine.accept(get(player), cat, id, context(player));
            case ABANDON -> QuestEngine.abandon(get(player), cat, id, context(player));
            case CLAIM -> QuestEngine.claim(get(player), cat, id, context(player));
            case TRACK -> QuestEngine.track(get(player), cat, id, true);
            case UNTRACK -> QuestEngine.track(get(player), cat, id, false);
        };
        if (r == null) {
            snapshot(player);
            return;
        }
        if (r.error != null) {
            RespectCraft.LOGGER.debug("Quest request {} {} from {} refused: {}", req.action(), id, player.getGameProfile().getName(), r.error);
            HoloNotices.toPlayer(player, HoloNotice.of(NoticeKind.QUEST, Component.translatable("quest.respectcraft.refused.title"),
                    Component.translatable("quest.respectcraft.refused." + r.error)).dedupe("quest_refused:" + r.error + ":" + id));
            if (get(player).state(id).discovered() && cat.get(id) != null) update(player, id);
            return;
        }
        apply(player, r, 0);
    }

    private static boolean allow(ServerPlayer player) {
        Limiter l = player.getData(ModAttachments.QUEST_REQUESTS);
        long tick = player.server.getTickCount();
        if (tick - l.windowTick >= 20) {
            l.windowTick = tick;
            l.count = 0;
        }
        return ++l.count <= QuestRules.MAX_REQUESTS_PER_SECOND;
    }

    // ================================================================================================ aplicar

    /** Grava, avisa, concede e sincroniza. Recusas de operacoes internas so vao ao log. */
    public static void apply(ServerPlayer player, QuestEngine.Result r, int depth) {
        if (r.error != null || !r.changed()) return;
        player.setData(ModAttachments.QUESTS, r.log);
        QuestCatalog cat = catalog();
        boolean fullSync = false;
        Set<ResourceLocation> touched = new LinkedHashSet<>();
        Set<ResourceLocation> discovered = new HashSet<>(), completed = new HashSet<>();
        for (QuestEngine.Change c : r.changes) {
            if (c.kind() == QuestEngine.ChangeKind.DISCOVERED) discovered.add(c.quest());
            if (c.kind() == QuestEngine.ChangeKind.COMPLETED) completed.add(c.quest());
        }
        List<QuestDef> toReward = new ArrayList<>();
        for (QuestEngine.Change c : r.changes) {
            touched.add(c.quest());
            QuestDef def = cat.get(c.quest());
            switch (c.kind()) {
                case DISCOVERED -> {
                    fullSync = true;
                    if (def != null) noticeDiscovered(player, def);
                    RespectCraft.LOGGER.info("Quest {} discovered by {}", c.quest(), player.getGameProfile().getName());
                }
                case ACCEPTED -> {
                    if (def != null && !discovered.contains(c.quest())) toast(player, NoticeStyle.SYSTEM_INFO,
                            Component.translatable("quest.respectcraft.notice.accepted"), def.title(), "accepted:" + c.quest());
                }
                case PROGRESS -> {
                    if (def != null && !completed.contains(c.quest())) noticeProgress(player, def, c);
                }
                case COMPLETED -> {
                    if (def != null) noticeCompleted(player, def);
                    RespectCraft.LOGGER.info("Quest {} completed by {}", c.quest(), player.getGameProfile().getName());
                }
                case CLAIMED -> {
                    if (def != null) toReward.add(def);
                }
                case FAILED, EXPIRED -> {
                    if (def != null) noticeFailed(player, def, c.kind());
                }
                case CANCELLED -> {
                    if (def != null) toast(player, NoticeStyle.SYSTEM_INFO, Component.translatable("quest.respectcraft.notice.abandoned"),
                            def.title(), "");
                }
                case REOPENED -> {
                    if (def != null) toast(player, NoticeStyle.QUEST_DISCOVERED, Component.translatable("quest.respectcraft.notice.reopened"),
                            def.title(), "reopened:" + c.quest());
                }
                case RESET -> fullSync = true;
                case MIGRATED -> RespectCraft.LOGGER.warn("Quest {} of {}: objectives changed in the definition; progress kept and clamped",
                        c.quest(), player.getGameProfile().getName());
                case TRACKED -> {}
            }
        }
        if (fullSync) snapshot(player);
        else for (ResourceLocation id : touched) update(player, id);
        for (QuestDef def : toReward) grant(player, def, depth);
    }

    // ================================================================================================ recompensas

    /** Entrega as recompensas de uma missao ja marcada como resgatada (CLAIMED gravado antes). */
    static void grant(ServerPlayer player, QuestDef def, int depth) {
        List<Component> got = new ArrayList<>();
        boolean dropped = false;
        for (QuestReward r : def.rewards()) {
            switch (r.type()) {
                case "experience" -> {
                    player.giveExperiencePoints(r.amount());
                    got.add(rewardLabel(player, r));
                }
                case "item" -> {
                    Item item = BuiltInRegistries.ITEM.get(r.id().orElseThrow());
                    if (item == Items.AIR) break;
                    int left = r.amount();
                    while (left > 0) {
                        int n = Math.min(left, item.getDefaultMaxStackSize());
                        left -= n;
                        ItemStack stack = new ItemStack(item, n);
                        player.getInventory().add(stack);
                        if (!stack.isEmpty()) {
                            var drop = player.drop(stack, false);
                            if (drop != null) drop.setNoPickUpDelay();
                            dropped = true;
                        }
                    }
                    got.add(rewardLabel(player, r));
                }
                case "skill" -> {
                    SkillDef skill = Skills.get(r.id().orElseThrow());
                    CharacterProfile profile = ProfileService.get(player);
                    if (skill == null || profile.skills().contains(skill.id().toString())) break;
                    List<String> skills = new ArrayList<>(profile.skills());
                    skills.add(skill.id().toString());
                    ProfileService.set(player, profile.withSkills(skills, profile.skillPoints()));
                    got.add(rewardLabel(player, r));
                }
                case "skill_points" -> {
                    CharacterProfile profile = ProfileService.get(player);
                    ProfileService.set(player, profile.withSkills(profile.skills(), profile.skillPoints() + r.amount()));
                    got.add(rewardLabel(player, r));
                }
                case "quest" -> {
                    if (depth >= MAX_REWARD_DEPTH) break;
                    apply(player, QuestEngine.forceDiscover(get(player), catalog(), r.id().orElseThrow(), context(player)), depth + 1);
                }
                default -> {}
            }
        }
        RespectCraft.LOGGER.info("Quest {} rewards granted to {} ({} reward(s){})", def.id(), player.getGameProfile().getName(),
                got.size(), dropped ? ", inventory full: items dropped" : "");
        if (!got.isEmpty()) {
            MutableComponent body = Component.empty();
            for (int i = 0; i < got.size(); i++) {
                if (i > 0) body.append(", ");
                body.append(got.get(i));
            }
            if (dropped) body.append(" ").append(Component.translatable("quest.respectcraft.notice.dropped"));
            HoloNotices.toPlayer(player, HoloNotice.of(NoticeKind.QUEST, Component.translatable("quest.respectcraft.notice.claimed", def.title()), body)
                    .style(NoticeStyle.REWARD_AVAILABLE).dedupe("claimed:" + def.id() + ":" + get(player).get(def.id()).completions()));
        }
    }

    // ================================================================================================ avisos (privados)

    private static void noticeDiscovered(ServerPlayer player, QuestDef def) {
        NoticeStyle style = def.notices().discover().orElse(def.secret() ? NoticeStyle.SECRET_DISCOVERED : NoticeStyle.QUEST_DISCOVERED);
        Component title = def.notices().discoverTitle().orElse(Component.translatable(def.secret()
                ? "quest.respectcraft.notice.secret_title" : "quest.respectcraft.notice.discovered_title"));
        QuestProgress p = get(player).get(def.id());
        Component body = p.state() == QuestState.ACTIVE ? def.description()
                : Component.translatable("quest.respectcraft.notice.open_to_accept", def.description());
        HoloNotices.toPlayer(player, HoloNotice.cinematic(NoticeKind.QUEST, style, title, body).subtitle(def.title())
                .present(NoticePresentation.CINEMATIC).duration(def.secret() ? 6000 : 5000).dedupe("discovered:" + def.id()));
    }

    private static void noticeProgress(ServerPlayer player, QuestDef def, QuestEngine.Change c) {
        QuestObjective o = null;
        for (QuestObjective x : def.objectives()) if (x.id().equals(c.objective())) o = x;
        if (o == null) return;
        HoloNotices.toPlayer(player, HoloNotice.cinematic(NoticeKind.QUEST, NoticeStyle.OBJECTIVE_UPDATED,
                        objectiveLabel(player, o), Component.literal(c.value() + " / " + c.count()))
                .subtitle(def.title()).duration(1600).group("quest_progress:" + def.id()));
    }

    private static void noticeCompleted(ServerPlayer player, QuestDef def) {
        NoticeStyle style = def.notices().complete().orElse(NoticeStyle.QUEST_COMPLETED);
        Component body = def.flags().autoClaim() ? Component.translatable("quest.respectcraft.notice.completed_auto")
                : Component.translatable("quest.respectcraft.notice.completed_claim");
        HoloNotices.toPlayer(player, HoloNotice.cinematic(NoticeKind.QUEST, style, Component.translatable("quest.respectcraft.notice.completed_title"), body)
                .subtitle(def.title()).duration(4500).dedupe("completed:" + def.id() + ":" + get(player).get(def.id()).completions()));
    }

    private static void noticeFailed(ServerPlayer player, QuestDef def, QuestEngine.ChangeKind kind) {
        NoticeStyle style = def.notices().fail().orElse(NoticeStyle.QUEST_FAILED);
        HoloNotices.toPlayer(player, HoloNotice.cinematic(NoticeKind.QUEST, style,
                        Component.translatable(kind == QuestEngine.ChangeKind.EXPIRED ? "quest.respectcraft.notice.expired_title"
                                : "quest.respectcraft.notice.failed_title"),
                        Component.translatable(def.flags().retry() ? "quest.respectcraft.notice.failed_retry" : "quest.respectcraft.notice.failed_final"))
                .subtitle(def.title()).duration(4000));
    }

    private static void toast(ServerPlayer player, NoticeStyle style, Component title, Component body, String dedupe) {
        HoloNotices.toPlayer(player, HoloNotice.of(NoticeKind.QUEST, title, body).style(style).dedupe(dedupe));
    }

    // ================================================================================================ sincronizacao

    public static void snapshot(ServerPlayer player) {
        QuestCatalog cat = catalog();
        QuestLog log = get(player);
        List<QuestView> views = new ArrayList<>();
        List<Component> hints = new ArrayList<>();
        long now = now(player.server);
        QuestContext ctx = context(player);
        for (QuestDef def : cat.all()) {
            QuestProgress p = log.get(def.id());
            if (p.state().discovered()) {
                if (views.size() < QuestNetworking.MAX_ENTRIES) views.add(view(player, def, p, now, ctx));
            } else if (def.hint().isPresent() && hints.size() < QuestNetworking.MAX_HINTS) {
                hints.add(def.hint().get());
            }
        }
        PacketDistributor.sendToPlayer(player, new QuestNetworking.QuestSnapshot(views, hints));
    }

    public static void update(ServerPlayer player, ResourceLocation id) {
        QuestDef def = catalog().get(id);
        QuestProgress p = get(player).get(id);
        if (def == null || !p.state().discovered()) return;
        PacketDistributor.sendToPlayer(player, new QuestNetworking.QuestUpdate(view(player, def, p, now(player.server), context(player))));
    }

    static QuestView view(ServerPlayer player, QuestDef def, QuestProgress p, long now, QuestContext ctx) {
        List<ObjectiveView> objectives = new ArrayList<>();
        for (QuestObjective o : def.objectives()) objectives.add(new ObjectiveView(objectiveLabel(player, o), Math.min(o.count(), p.value(o.id())), o.count()));
        List<Component> rewards = new ArrayList<>();
        for (QuestReward r : def.rewards()) rewards.add(rewardLabel(player, r));
        long remaining = -1;
        if (p.state() == QuestState.ACTIVE && def.flags().timeLimitTicks() > 0) remaining = Math.max(0, def.flags().timeLimitTicks() - (now - p.acceptedAt()));
        else if (p.state() == QuestState.CLAIMED && def.flags().repeatable()) remaining = Math.max(0, p.cooldownUntil() - now);
        int flags = 0;
        if (def.secret()) flags |= QuestView.SECRET;
        if (p.tracked()) flags |= QuestView.TRACKED;
        if (!def.flags().autoAccept()) flags |= QuestView.MANUAL_ACCEPT;
        if (!def.flags().autoClaim()) flags |= QuestView.MANUAL_CLAIM;
        if (def.flags().abandonable()) flags |= QuestView.ABANDONABLE;
        if (def.flags().repeatable()) flags |= QuestView.REPEATABLE;
        if (def.flags().retry()) flags |= QuestView.RETRY;
        if (QuestRules.canAccept(p.state(), def.flags()) && def.requires().map(c -> c.test(ctx, null)).orElse(true)) flags |= QuestView.CAN_ACCEPT;
        return new QuestView(def.id(), def.category(), p.state(), def.title(), def.description(), objectives, rewards,
                def.requirementsText().size() > QuestNetworking.MAX_LINES ? def.requirementsText().subList(0, QuestNetworking.MAX_LINES) : def.requirementsText(),
                remaining, p.completions(), flags, def.sort());
    }

    // ================================================================================================ textos

    static Component objectiveLabel(ServerPlayer player, QuestObjective o) {
        if (o.description().isPresent()) return o.description().get();
        String k = "quest.respectcraft.objective.";
        return switch (o.type()) {
            case "kill" -> Component.translatable(k + "kill", o.count(), targetName(o.matcher()));
            case "finisher" -> Component.translatable(k + "finisher", o.count(), targetName(o.matcher()));
            case "boss_defeat" -> Component.translatable(k + "boss", targetName(o.matcher()));
            case "advancement" -> {
                AdvancementHolder h = player.server.getAdvancements().get(o.targetId());
                Component name = h == null ? Component.literal(String.valueOf(o.targetId()))
                        : h.value().display().map(d -> (Component) d.getTitle()).orElse(Component.literal(String.valueOf(o.targetId())));
                yield Component.translatable(k + "advancement", name);
            }
            case "enter_dimension" -> Component.translatable(k + "dimension", dimensionName(o.targetId()));
            case "complete_quest" -> {
                QuestDef other = catalog().get(o.targetId());
                boolean known = other != null && (!other.secret() || get(player).state(other.id()).discovered());
                yield Component.translatable(k + "quest", known ? other.title() : Component.literal("???"));
            }
            default -> Component.literal(o.id());
        };
    }

    static Component targetName(QuestMatcher m) {
        if (m.any()) return Component.translatable("quest.respectcraft.target.any");
        if (m.tag()) return Component.translatable("quest.respectcraft.target.tag." + m.id().getNamespace() + "." + m.id().getPath().replace('/', '.'));
        Optional<EntityType<?>> type = BuiltInRegistries.ENTITY_TYPE.getOptional(m.id());
        return type.<Component>map(EntityType::getDescription).orElse(Component.literal(m.id().toString()));
    }

    static Component dimensionName(ResourceLocation id) {
        return id == null ? Component.literal("?") : Component.translatable("dimension." + id.getNamespace() + "." + id.getPath());
    }

    static Component rewardLabel(ServerPlayer player, QuestReward r) {
        String k = "quest.respectcraft.reward.";
        return switch (r.type()) {
            case "experience" -> Component.translatable(k + "experience", r.amount());
            case "item" -> Component.translatable(k + "item", r.amount(), BuiltInRegistries.ITEM.get(r.id().orElseThrow()).getDescription());
            case "skill" -> {
                SkillDef skill = Skills.get(r.id().orElseThrow());
                yield Component.translatable(k + "skill", skill == null ? Component.literal(r.id().get().toString()) : Component.translatable(skill.translationKey()));
            }
            case "skill_points" -> Component.translatable(k + "skill_points", r.amount());
            case "quest" -> {
                QuestDef other = catalog().get(r.id().orElseThrow());
                yield other == null || other.secret() ? Component.translatable(k + "quest_secret") : Component.translatable(k + "quest", other.title());
            }
            default -> Component.literal(r.type());
        };
    }
}
