package com.respectcraft.client.holo;

import com.respectcraft.client.ui.Ui;
import com.respectcraft.network.NoticeClientCache;
import com.respectcraft.notice.HoloNotice;
import com.respectcraft.notice.NoticeRules;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;

/**
 * Secao 06 "Alerts" (Etapa 4.11): historico dos avisos holograficos automaticos (chefes, portais, eventos de mundo,
 * caca a recompensa), mais novo em cima, com categoria, ha quanto tempo, texto e, quando o aviso tem local, a
 * distancia e a direcao grosseiras ate ele. Entrar na secao marca tudo como lido; os que eram novos ficam acesos.
 * So le {@link NoticeClientCache}; nada e pedido ao servidor.
 */
final class NoticesView extends HoloView {
    private final Set<NoticeClientCache.Entry> fresh = Collections.newSetFromMap(new IdentityHashMap<>());

    NoticesView(HoloMenu menu) { super(menu, HoloCharacterScreen.Section.ALERTS); }

    @Override
    void onEnter() {
        super.onEnter();
        fresh.clear();
        markSeen();
    }

    @Override
    void tick() {
        if (menu.section() == section() && !menu.closing) markSeen();
    }

    private void markSeen() {
        for (NoticeClientCache.Entry e : NoticeClientCache.history()) if (!e.read) fresh.add(e);
        NoticeClientCache.markAllRead();
    }

    @Override
    float headerRight(HoloCanvas c, float w) {
        float bw = 44F;
        boolean any = !NoticeClientCache.history().isEmpty();
        c.button("alerts.clear", tr("gui.respectcraft.holo.alerts.clear"), w - 10F - bw, 6F, bw, 13F, null, HoloStyle.RED,
                any, false, "", () -> {
                    NoticeClientCache.clearHistory();
                    fresh.clear();
                    Ui.playClick(0.8F);
                });
        return w - 14F - bw;
    }

    @Override
    float body(HoloCanvas c, float w, float viewportHeight, float since) {
        List<NoticeClientCache.Entry> list = NoticeClientCache.history();
        float x = 10F, iw = w - 20F, y = 3F;
        int i = 1;
        c.stage(since, i++);
        if (list.isEmpty()) {
            y += c.wrap(Component.translatable("gui.respectcraft.holo.alerts.empty"), x, y, iw, 4, c.muted()) + 6F;
            return y;
        }
        long now = Util.getMillis();
        for (NoticeClientCache.Entry e : list) {
            c.stage(since, Math.min(i++, 8));
            HoloNotice n = e.notice;
            int col = n.kind().color();
            boolean isNew = fresh.contains(e);
            String loc = location(c, n);
            int bodyLines = Math.min(3, c.lineCount(n.body(), iw - 22F));
            float cardH = 25F + bodyLines * 10F + (loc == null ? 0F : 11F);
            float pulse = isNew ? 0.75F + 0.25F * (float) Math.sin(c.time * 3F) : 0.5F;
            c.fill(x, y, iw, cardH, Ui.alpha(col, isNew ? 0.11F : 0.05F));
            c.frame(x, y, iw, cardH, Ui.alpha(col, (isNew ? 0.6F : 0.25F) * pulse + 0.1F));
            c.fill(x, y, 2F, cardH, Ui.alpha(col, 0.9F));
            if (isNew) c.glow(x + 9F, y + 9F, 10F, Ui.alpha(col, 0.35F * pulse));
            c.textCentered(n.kind().glyph(), x + 9.5F, y + 5F, col, false);
            String kind = tr(n.kind().translationKey()).toUpperCase(java.util.Locale.ROOT);
            float kx = x + 18F;
            kx += c.caption(kind, kx, y + 5F, Ui.lerpColor(col, c.muted(), 0.25F)) + 6F;
            if (isNew) c.pill(tr("gui.respectcraft.holo.alerts.new"), kx, y + 3F, col);
            String ago = ago((now - e.arrivedMillis) / 1000L);
            c.textRight(ago, x + iw - 5F, y + 4F, c.dim(), false);
            String title = n.title().getString();
            c.text(Ui.fit(c.font, title, (int) (iw - 24F)), x + 18F, y + 14F, c.text(), true);
            float by = y + 25F;
            c.wrap(n.body(), x + 18F, by, iw - 22F, 3, c.muted());
            by += bodyLines * 10F;
            if (loc != null) c.text(Ui.fit(c.font, loc, (int) (iw - 24F)), x + 18F, by + 1F, Ui.lerpColor(col, c.text(), 0.4F), false);
            if (c.over(x, y, iw, cardH)) c.tooltip(List.of(n.title().copy().withStyle(s -> s.withColor(col & 0xFFFFFF).withBold(true)),
                    n.body().copy().withStyle(ChatFormatting.GRAY)));
            y += cardH + 4F;
        }
        return y;
    }

    /** "x y z · 120 m NE" no mesmo nivel; "x y z · other dimension" fora dele; null sem local. */
    static String location(HoloCanvas c, HoloNotice n) {
        if (n.location().isEmpty()) return null;
        HoloNotice.Location l = n.location().get();
        String coords = "⌖ " + l.pos().getX() + " " + l.pos().getY() + " " + l.pos().getZ();
        var player = c.mc.player;
        if (player == null) return coords;
        if (!player.level().dimension().location().equals(l.dimension())) return coords + " · " + tr("gui.respectcraft.holo.alerts.other_dimension");
        double dx = l.pos().getX() + 0.5 - player.getX(), dz = l.pos().getZ() + 0.5 - player.getZ();
        return coords + " · " + distance(dx, dz);
    }

    /** "120 m NE", ou "right here" a menos de 5 blocos. */
    static String distance(double dx, double dz) {
        int d = NoticeRules.roughDistance(Math.sqrt(dx * dx + dz * dz));
        if (d < 5) return tr("gui.respectcraft.holo.alerts.here");
        int dir = NoticeRules.direction(dx, dz);
        return d + " m" + (dir < 0 ? "" : " " + NoticeRules.WINDS[dir]);
    }

    static String ago(long seconds) {
        if (seconds < 5) return tr("gui.respectcraft.holo.alerts.now");
        if (seconds < 60) return tr("gui.respectcraft.holo.alerts.seconds", seconds);
        if (seconds < 3600) return tr("gui.respectcraft.holo.alerts.minutes", seconds / 60);
        return tr("gui.respectcraft.holo.alerts.hours", seconds / 3600);
    }
}
