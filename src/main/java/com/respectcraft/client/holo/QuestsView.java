package com.respectcraft.client.holo;

import com.respectcraft.client.ui.Ui;
import com.respectcraft.network.QuestClientCache;
import com.respectcraft.network.QuestNetworking;
import com.respectcraft.network.QuestNetworking.QuestAction;
import com.respectcraft.network.QuestNetworking.QuestView;
import com.respectcraft.quest.QuestState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Secao 08 "Quests" (Etapa 4.14.3): as missoes descobertas do jogador, com filtros (todas, disponiveis, ativas,
 * concluidas, fracassadas, secretas), progresso, e o detalhe da selecionada (descricao, objetivos com barra,
 * recompensas, pre-requisitos revelados, tempo restante) com as acoes Accept / Track / Untrack / Abandon (segundo
 * clique) / Claim. Pistas de missoes ocultas aparecem como "rumores", sem nome.
 *
 * <p>So apresentacao: le {@link QuestClientCache} e manda {@link QuestNetworking.QuestActionRequest}; o
 * {@code QuestService} decide tudo. Cores por estado/categoria passam pelos estilos da HUD V2.
 */
final class QuestsView extends HoloView {
    enum Filter { ALL, AVAILABLE, ACTIVE, DONE, FAILED, SECRET }

    private Filter filter = Filter.ALL;
    private ResourceLocation selected;
    private float confirmAbandonUntil = -1F;

    QuestsView(HoloMenu menu) { super(menu, HoloCharacterScreen.Section.QUESTS); }

    @Override
    void onEnter() {
        super.onEnter();
        send(QuestAction.REFRESH, menuQuestId());
        QuestClientCache.markSeen();
    }

    @Override
    void tick() {
        if (menu.section() == section() && !menu.closing) QuestClientCache.markSeen();
    }

    /** Pedido sem missao usa um id neutro (o servidor so le o id nas acoes que precisam). */
    private static ResourceLocation menuQuestId() { return ResourceLocation.withDefaultNamespace("none"); }

    private static void send(QuestAction action, ResourceLocation id) {
        PacketDistributor.sendToServer(new QuestNetworking.QuestActionRequest(action, id));
    }

    static int stateColor(QuestState s) {
        return switch (s) {
            case AVAILABLE -> HoloStyle.CYAN;
            case ACTIVE -> 0xFF4FB4FF;
            case COMPLETED -> HoloStyle.GOLD;
            case CLAIMED -> HoloStyle.GREEN;
            case FAILED, EXPIRED -> HoloStyle.RED;
            default -> 0xFF6A7C8E;
        };
    }

    static String stateName(QuestState s) { return tr("gui.respectcraft.holo.quests.state." + s.id()); }

    private static boolean accepts(Filter f, QuestView v) {
        return switch (f) {
            case ALL -> true;
            case AVAILABLE -> v.state() == QuestState.AVAILABLE;
            case ACTIVE -> v.state() == QuestState.ACTIVE;
            case DONE -> v.state() == QuestState.COMPLETED || v.state() == QuestState.CLAIMED;
            case FAILED -> v.state() == QuestState.FAILED || v.state() == QuestState.EXPIRED || v.state() == QuestState.CANCELLED;
            case SECRET -> v.has(QuestView.SECRET);
        };
    }

    /** Ordem: resgatar, ativas, disponiveis, o resto; dentro, a ordem da definicao. */
    private static int rank(QuestView v) {
        return switch (v.state()) {
            case COMPLETED -> 0;
            case ACTIVE -> 1;
            case AVAILABLE -> 2;
            case FAILED, EXPIRED, CANCELLED -> 3;
            default -> 4;
        };
    }

    @Override
    float headerRight(HoloCanvas c, float w) {
        int active = 0;
        for (QuestClientCache.Entry e : QuestClientCache.all()) if (e.view().state() == QuestState.ACTIVE) active++;
        String s = tr("gui.respectcraft.holo.quests.active_count", active);
        float pw = c.font.width(s) + 8F;
        c.pill(s, w - 10F - pw, 7F, active == 0 ? 0xFF3A4654 : accent());
        return w - 14F - pw;
    }

    @Override
    float body(HoloCanvas c, float w, float viewportHeight, float since) {
        float x = 10F, iw = w - 20F, y = 3F;
        int i = 1;
        // ---- filtros
        c.stage(since, i++);
        float fx = x;
        for (Filter f : Filter.values()) {
            String label = tr("gui.respectcraft.holo.quests.filter." + f.name().toLowerCase(Locale.ROOT));
            float bw = c.font.width(label) + 8F;
            if (fx + bw > x + iw) {
                fx = x;
                y += 15F;
            }
            c.button("quests.filter." + f.name(), label, fx, y, bw, 12F, null, accent(), true, filter == f, "", () -> {
                filter = f;
                Ui.playClick(1.2F);
            });
            fx += bw + 3F;
        }
        y += 17F;
        if (!QuestClientCache.received()) {
            c.text(tr("gui.respectcraft.holo.quests.loading"), x, y, c.dim(), false);
            return y + 12F;
        }
        List<QuestClientCache.Entry> list = new ArrayList<>();
        for (QuestClientCache.Entry e : QuestClientCache.all()) if (accepts(filter, e.view())) list.add(e);
        list.sort(Comparator.<QuestClientCache.Entry>comparingInt(e -> rank(e.view())).thenComparingInt(e -> e.view().sort())
                .thenComparing(e -> e.view().id()));
        if (list.isEmpty()) {
            c.stage(since, i++);
            y += c.wrap(Component.translatable("gui.respectcraft.holo.quests.empty." + filter.name().toLowerCase(Locale.ROOT)), x, y, iw, 3, c.dim()) + 6F;
        }
        for (QuestClientCache.Entry e : list) {
            c.stage(since, Math.min(i++, 8));
            y = card(c, e, x, y, iw) + 4F;
        }
        // ---- pistas (rumores) das ocultas
        List<Component> hints = QuestClientCache.hints();
        if (!hints.isEmpty() && (filter == Filter.ALL || filter == Filter.AVAILABLE)) {
            c.stage(since, Math.min(i++, 9));
            y += 2F;
            c.caption(tr("gui.respectcraft.holo.quests.rumors"), x, y + 1F, HoloStyle.VIOLET);
            y += 12F;
            for (Component h : hints) {
                int lines = Math.min(3, c.lineCount(h, iw - 20F));
                float hh = 8F + lines * 10F;
                c.fill(x, y, iw, hh, Ui.alpha(HoloStyle.VIOLET, 0.06F));
                c.frame(x, y, iw, hh, Ui.alpha(HoloStyle.VIOLET, 0.25F));
                c.textCentered("?", x + 8F, y + 4F, HoloStyle.VIOLET, false);
                c.wrap(h, x + 16F, y + 4F, iw - 20F, 3, c.muted());
                y += hh + 3F;
            }
        }
        return y;
    }

    /** Um cartao de missao; a selecionada abre o detalhe embaixo do titulo. */
    private float card(HoloCanvas c, QuestClientCache.Entry e, float x, float y, float iw) {
        QuestView v = e.view();
        boolean open = v.id().equals(selected);
        int col = v.has(QuestView.SECRET) ? HoloStyle.VIOLET : stateColor(v.state());
        int done = 0, total = 0;
        for (QuestNetworking.ObjectiveView o : v.objectives()) {
            done += Math.min(o.value(), o.count());
            total += o.count();
        }
        float headH = 27F;
        float top = y;
        // ---- cabecalho clicavel
        boolean hot = c.over(x, y, iw, headH);
        float k = c.hoverAnim("quest.card." + v.id(), hot || open);
        boolean fresh = QuestClientCache.unseen(v.id());
        float h = headH + (open ? detailHeight(c, v, iw) : 0F);
        c.fill(x, y, iw, h, Ui.alpha(col, 0.05F + 0.05F * k));
        c.frame(x, y, iw, h, Ui.alpha(col, 0.25F + 0.35F * k));
        c.fill(x, y, 2F, h, Ui.alpha(col, 0.9F));
        if (v.has(QuestView.TRACKED)) c.glow(x + iw - 8F, y + 6F, 7F, Ui.alpha(accent(), 0.5F));
        float tx = x + 7F;
        String state = stateName(v.state()).toUpperCase(Locale.ROOT);
        float sw = c.font.width(state) + 8F;
        c.pill(state, x + iw - 5F - sw, y + 3F, stateColor(v.state()));
        float right = x + iw - 9F - sw;
        if (v.has(QuestView.TRACKED)) {
            c.textRight("◉", right, y + 5F, accent(), false);
            right -= 10F;
        }
        String title = Ui.fit(c.font, v.title().getString(), (int) (right - tx - (fresh ? 30F : 0F)));
        c.text(title, tx, y + 5F, c.text(), true);
        if (fresh) c.pill(tr("gui.respectcraft.holo.alerts.new"), tx + c.font.width(title) + 4F, y + 3F, col);
        String cat = (v.has(QuestView.SECRET) ? tr("gui.respectcraft.holo.quests.secret") + " · " : "")
                + Component.translatableWithFallback("gui.respectcraft.holo.quests.category." + v.category(), v.category()).getString();
        c.text(Ui.fit(c.font, cat.toUpperCase(Locale.ROOT), (int) (iw * 0.5F)), tx, y + 16F, Ui.lerpColor(col, c.muted(), 0.35F), false);
        // barra de progresso total
        float bx = x + iw * 0.5F + 4F, bw = iw * 0.5F - 40F;
        float frac = total == 0 ? 0F : done / (float) total;
        c.fill(bx, y + 19F, bw, 2F, 0x40FFFFFF);
        c.fill(bx, y + 19F, bw * frac, 2F, col);
        c.textRight(done + "/" + total, x + iw - 5F, y + 16F, c.muted(), false);
        HoloZone z = c.zone(x, y, iw, headH);
        z.click = () -> {
            selected = open ? null : v.id();
            confirmAbandonUntil = -1F;
            Ui.playClick(open ? 0.9F : 1.15F);
        };
        if (hot && !open) c.tooltip(List.of(v.title().copy().withStyle(ChatFormatting.BOLD), Component.translatable("gui.respectcraft.holo.quests.click")
                .withStyle(ChatFormatting.GRAY)));
        y += headH;
        if (open) y = details(c, e, x, y, iw);
        return Math.max(y, top + h);
    }

    private float detailHeight(HoloCanvas c, QuestView v, float iw) {
        float h = 4F + Math.min(5, c.lineCount(v.description(), iw - 14F)) * 10F + 4F;
        h += 12F + v.objectives().size() * 15F;
        if (!v.rewards().isEmpty()) h += 12F + v.rewards().size() * 10F;
        if (!v.requirements().isEmpty()) {
            h += 12F;
            for (Component r : v.requirements()) h += Math.min(3, c.lineCount(r, iw - 20F)) * 10F;
        }
        if (v.remainingTicks() >= 0 || v.has(QuestView.REPEATABLE)) h += 11F;
        return h + 22F;
    }

    private float details(HoloCanvas c, QuestClientCache.Entry e, float x, float y, float iw) {
        QuestView v = e.view();
        float tx = x + 7F, tw = iw - 14F;
        int col = v.has(QuestView.SECRET) ? HoloStyle.VIOLET : stateColor(v.state());
        c.fill(tx, y, tw, 1F, Ui.alpha(col, 0.3F));
        y += 4F;
        y += c.wrap(v.description(), tx, y, tw, 5, c.muted()) + 4F;
        // objetivos
        c.caption(tr("gui.respectcraft.holo.quests.objectives"), tx, y + 1F, Ui.lerpColor(col, c.muted(), 0.3F));
        y += 12F;
        for (QuestNetworking.ObjectiveView o : v.objectives()) {
            boolean ok = o.value() >= o.count();
            String mark = ok ? "✔" : "•";
            c.text(mark, tx, y, ok ? HoloStyle.GREEN : col, false);
            String count = o.value() + "/" + o.count();
            float cw = c.font.width(count);
            c.text(Ui.fit(c.font, o.label().getString(), (int) (tw - cw - 16F)), tx + 9F, y, ok ? c.muted() : c.text(), false);
            c.textRight(count, tx + tw, y, ok ? HoloStyle.GREEN : c.muted(), false);
            float frac = o.count() == 0 ? 0F : Math.min(1F, o.value() / (float) o.count());
            c.fill(tx + 9F, y + 10F, tw - 9F, 1F, 0x30FFFFFF);
            c.fill(tx + 9F, y + 10F, (tw - 9F) * frac, 1F, ok ? HoloStyle.GREEN : col);
            if (c.over(tx, y - 1F, tw, 12F)) c.tooltip(List.of(o.label()));
            y += 15F;
        }
        // recompensas
        if (!v.rewards().isEmpty()) {
            c.caption(tr("gui.respectcraft.holo.quests.rewards"), tx, y + 1F, HoloStyle.GOLD);
            y += 12F;
            for (Component r : v.rewards()) {
                c.text("✦", tx, y, HoloStyle.GOLD, false);
                c.text(Ui.fit(c.font, r.getString(), (int) (tw - 10F)), tx + 9F, y, v.state() == QuestState.CLAIMED ? c.dim() : c.text(), false);
                y += 10F;
            }
        }
        // pre-requisitos revelados
        if (!v.requirements().isEmpty()) {
            boolean met = v.has(QuestView.CAN_ACCEPT) || v.state() != QuestState.AVAILABLE;
            c.caption(tr("gui.respectcraft.holo.quests.requirements"), tx, y + 1F, met ? c.muted() : HoloStyle.RED);
            y += 12F;
            for (Component r : v.requirements()) {
                int lines = Math.min(3, c.lineCount(r, tw - 9F));
                c.text("›", tx, y, c.muted(), false);
                c.wrap(r, tx + 9F, y, tw - 9F, 3, c.muted());
                y += lines * 10F;
            }
        }
        // tempo
        if (v.remainingTicks() >= 0 || v.has(QuestView.REPEATABLE)) {
            String t;
            long rem = e.remainingTicks();
            if (v.state() == QuestState.ACTIVE && rem >= 0) t = tr("gui.respectcraft.holo.quests.time_left", time(rem));
            else if (v.state() == QuestState.CLAIMED && rem >= 0) t = tr("gui.respectcraft.holo.quests.repeat_in", time(rem));
            else t = tr("gui.respectcraft.holo.quests.repeatable", v.completions());
            c.text(Ui.fit(c.font, "⌛ " + t, (int) tw), tx, y + 1F, rem >= 0 && rem < 1200 && v.state() == QuestState.ACTIVE ? HoloStyle.RED : c.muted(), false);
            y += 11F;
        }
        // acoes
        y += 3F;
        float bx = tx, bh = 14F;
        QuestState s = v.state();
        boolean canAccept = v.has(QuestView.CAN_ACCEPT) && (s == QuestState.AVAILABLE || ((s == QuestState.FAILED || s == QuestState.EXPIRED
                || s == QuestState.CANCELLED) && v.has(QuestView.RETRY)));
        if (canAccept || (s == QuestState.AVAILABLE && v.has(QuestView.MANUAL_ACCEPT))) {
            String label = tr(s == QuestState.AVAILABLE ? "gui.respectcraft.holo.quests.accept" : "gui.respectcraft.holo.quests.retry");
            float bw = Math.max(52F, c.font.width(label) + 12F);
            c.button("quest.accept." + v.id(), label, bx, y, bw, bh, null, HoloStyle.GREEN, canAccept, false, "", () -> {
                send(QuestAction.ACCEPT, v.id());
                Ui.playClick(1.25F);
            });
            bx += bw + 4F;
        }
        if (s == QuestState.COMPLETED && v.has(QuestView.MANUAL_CLAIM)) {
            String label = tr("gui.respectcraft.holo.quests.claim");
            float bw = Math.max(60F, c.font.width(label) + 12F);
            c.button("quest.claim." + v.id(), label, bx, y, bw, bh, null, HoloStyle.GOLD, true, true, "", () -> {
                send(QuestAction.CLAIM, v.id());
                Ui.playClick(1.3F);
            });
            bx += bw + 4F;
        }
        if (s == QuestState.ACTIVE || s == QuestState.AVAILABLE) {
            boolean tracked = v.has(QuestView.TRACKED);
            String label = tr(tracked ? "gui.respectcraft.holo.quests.untrack" : "gui.respectcraft.holo.quests.track");
            float bw = Math.max(52F, c.font.width(label) + 12F);
            c.button("quest.track." + v.id(), label, bx, y, bw, bh, null, accent(), true, tracked, "", () -> {
                send(tracked ? QuestAction.UNTRACK : QuestAction.TRACK, v.id());
                Ui.playClick(1.1F);
            });
            bx += bw + 4F;
        }
        if (s == QuestState.ACTIVE && v.has(QuestView.ABANDONABLE)) {
            boolean confirming = confirmAbandonUntil > c.time;
            String label = tr(confirming ? "gui.respectcraft.holo.quests.abandon_confirm" : "gui.respectcraft.holo.quests.abandon");
            float bw = Math.max(52F, c.font.width(label) + 12F);
            if (bx + bw <= tx + tw) c.button("quest.abandon." + v.id(), label, bx, y, bw, bh, null, HoloStyle.RED, true, confirming, "", () -> {
                if (confirming) {
                    send(QuestAction.ABANDON, v.id());
                    confirmAbandonUntil = -1F;
                    Ui.playClick(0.8F);
                } else {
                    confirmAbandonUntil = c.time + 3F;
                    Ui.playClick(1.0F);
                }
            });
        }
        return y + bh + 5F;
    }

    static String time(long ticks) {
        long s = ticks / 20L;
        if (s >= 3600) return String.format(Locale.ROOT, "%dh %02dm", s / 3600, (s % 3600) / 60);
        if (s >= 60) return String.format(Locale.ROOT, "%dm %02ds", s / 60, s % 60);
        return s + "s";
    }
}
