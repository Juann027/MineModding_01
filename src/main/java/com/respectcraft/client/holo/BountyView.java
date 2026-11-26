package com.respectcraft.client.holo;

import com.respectcraft.bounty.BountyRules;
import com.respectcraft.client.ui.Ui;
import com.respectcraft.network.BountyClientCache;
import com.respectcraft.network.HoloNetworking;
import com.respectcraft.network.HoloNetworking.BountyAction;
import com.respectcraft.network.HoloNetworking.BountyEntry;
import com.respectcraft.notice.NoticeRules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Secao 07 "Bounty Hunt" (Etapa 4.11): caca a recompensa contra outros jogadores. Mostra o aviso de recompensa
 * contra mim, os contratos abertos (alvo, recompensa, pagador, tempo, cacadores e, nos que aceitei, o rastreio
 * grosseiro) com Accept / Abandon / Cancel, e o formulario para abrir um contrato (alvo entre os jogadores online,
 * duracao e a pilha da mao principal como recompensa; segundo clique confirma porque tira os itens).
 *
 * <p>So apresentacao: tudo vai como {@link HoloNetworking.BountyActionRequest} e o servidor ({@code BountyService})
 * decide. O quadro chega por {@link BountyClientCache}; enquanto a secao esta na frente pede atualizacao a cada 5 s.
 */
final class BountyView extends HoloView {
    private static final int REFRESH_TICKS = 100;
    private int refreshIn;
    private UUID pickedTarget;
    private int days = BountyRules.DURATION_DAYS[0];
    private float confirmUntil = -1F;

    BountyView(HoloMenu menu) { super(menu, HoloCharacterScreen.Section.BOUNTY); }

    @Override
    void onEnter() {
        super.onEnter();
        send(BountyAction.REFRESH, null, null);
        refreshIn = REFRESH_TICKS;
    }

    @Override
    void tick() {
        if (menu.section() != section() || menu.closing) return;
        if (--refreshIn <= 0) {
            refreshIn = REFRESH_TICKS;
            send(BountyAction.REFRESH, null, null);
        }
    }

    private static void send(BountyAction action, UUID bounty, UUID target, int days) {
        PacketDistributor.sendToServer(new HoloNetworking.BountyActionRequest(action, Optional.ofNullable(bounty),
                Optional.ofNullable(target), days));
    }

    private static void send(BountyAction action, UUID bounty, UUID target) { send(action, bounty, target, 0); }

    @Override
    float headerRight(HoloCanvas c, float w) {
        String s = tr("gui.respectcraft.holo.bounty.open_count", BountyClientCache.entries().size());
        float pw = c.font.width(s) + 8F;
        c.pill(s, w - 10F - pw, 7F, BountyClientCache.entries().isEmpty() ? 0xFF3A4654 : accent());
        return w - 14F - pw;
    }

    @Override
    float body(HoloCanvas c, float w, float viewportHeight, float since) {
        float x = 10F, iw = w - 20F, y = 3F;
        int i = 1;
        List<BountyEntry> entries = new ArrayList<>(BountyClientCache.entries());
        // Contra mim primeiro, depois os que cacei, depois os meus, depois o resto (menos tempo primeiro).
        entries.sort(Comparator.<BountyEntry>comparingInt(e -> e.onMe() ? 0 : e.hunting() ? 1 : e.mine() ? 2 : 3)
                .thenComparingLong(BountyEntry::remainingTicks));

        // ---- recompensa contra mim
        int onMe = BountyClientCache.onMe();
        if (onMe > 0) {
            c.stage(since, i++);
            float pulse = 0.7F + 0.3F * (float) Math.sin(c.time * 4F);
            float h = 30F;
            c.fill(x, y, iw, h, Ui.alpha(HoloStyle.RED, 0.12F));
            c.frame(x, y, iw, h, Ui.alpha(HoloStyle.RED, 0.7F * pulse));
            c.brackets(x - 2F, y - 2F, iw + 4F, h + 4F, 6F, HoloStyle.RED);
            c.glow(x + 12F, y + 15F, 14F, Ui.alpha(HoloStyle.RED, 0.4F * pulse));
            c.textCentered("☠", x + 12F, y + 11F, HoloStyle.RED, true);
            c.text(Ui.fit(c.font, tr("gui.respectcraft.holo.bounty.on_me", onMe), (int) (iw - 30F)), x + 24F, y + 6F, 0xFFFFFFFF, true);
            c.text(Ui.fit(c.font, tr("gui.respectcraft.holo.bounty.on_me.desc"), (int) (iw - 30F)), x + 24F, y + 17F, c.muted(), false);
            y += h + 8F;
        }

        // ---- contratos
        c.stage(since, i++);
        c.caption(tr("gui.respectcraft.holo.bounty.contracts"), x, y + 1F, c.muted());
        y += 13F;
        if (!BountyClientCache.received()) {
            c.text(tr("gui.respectcraft.holo.bounty.loading"), x, y, c.dim(), false);
            y += 14F;
        } else if (entries.isEmpty()) {
            y += c.wrap(Component.translatable("gui.respectcraft.holo.bounty.none"), x, y, iw, 3, c.dim()) + 6F;
        }
        for (BountyEntry e : entries) {
            c.stage(since, Math.min(i++, 8));
            y = card(c, e, x, y, iw) + 4F;
        }

        // ---- abrir contrato
        y += 4F;
        c.stage(since, Math.min(i++, 9));
        c.divider(x, y, iw, accent());
        y += 6F;
        c.caption(tr("gui.respectcraft.holo.bounty.post"), x, y + 1F, c.muted());
        y += 14F;
        List<PlayerInfo> others = others(c);
        PlayerInfo target = picked(others);
        float labelW = 52F, fx = x + labelW, fw = iw - labelW;
        // alvo
        c.text(tr("gui.respectcraft.holo.bounty.target"), x, y + 3F, c.muted(), false);
        if (target == null) {
            c.text(Ui.fit(c.font, tr("gui.respectcraft.holo.bounty.no_players"), (int) fw), fx, y + 3F, c.dim(), false);
        } else {
            selector(c, "bounty.target", fx, y, fw, target.getProfile().getName(), others.size() > 1, dir -> {
                int idx = others.indexOf(target);
                pickedTarget = others.get(Math.floorMod(idx + dir, others.size())).getProfile().getId();
                confirmUntil = -1F;
            });
        }
        y += 17F;
        // duracao
        c.text(tr("gui.respectcraft.holo.bounty.duration"), x, y + 3F, c.muted(), false);
        selector(c, "bounty.days", fx, y, fw, tr("gui.respectcraft.holo.bounty.days", days, days * 20), true, dir -> {
            days = BountyRules.nextDuration(days, dir);
            confirmUntil = -1F;
        });
        y += 17F;
        // recompensa
        c.text(tr("gui.respectcraft.holo.bounty.reward"), x, y + 1F, c.muted(), false);
        ItemStack hand = c.mc.player == null ? ItemStack.EMPTY : c.mc.player.getMainHandItem();
        if (hand.isEmpty()) {
            c.text(Ui.fit(c.font, tr("gui.respectcraft.holo.bounty.hold_reward"), (int) fw), fx, y + 1F, HoloStyle.GOLD, false);
        } else {
            String rw = hand.getCount() + "× " + hand.getHoverName().getString();
            c.text(Ui.fit(c.font, rw, (int) fw), fx, y + 1F, rarityColor(hand), true);
        }
        y += 14F;
        boolean confirming = confirmUntil > c.time;
        boolean can = target != null && !hand.isEmpty();
        String label = confirming ? tr("gui.respectcraft.holo.bounty.post_confirm") : tr("gui.respectcraft.holo.bounty.post_button");
        UUID targetId = target == null ? null : target.getProfile().getId();
        c.button("bounty.post", label, x, y, Math.min(iw, 150F), 15F, SkillDefIcon.SKULL, confirming ? HoloStyle.RED : accent(), can,
                confirming, "", () -> {
                    if (confirmUntil > menu.time()) {
                        confirmUntil = -1F;
                        send(BountyAction.PLACE, null, targetId, days);
                        Ui.playClick(0.7F);
                    } else {
                        confirmUntil = menu.time() + HoloPopup.CONFIRM_WINDOW;
                        Ui.playClick(1.1F);
                    }
                });
        y += 21F;
        y += c.wrap(Component.translatable("gui.respectcraft.holo.bounty.rules", BountyRules.MAX_ACTIVE_PER_POSTER,
                BountyRules.MAX_CONTRACTS_PER_HUNTER), x, y, iw, 4, c.dim()) + 4F;
        return y;
    }

    /** Cartao de um contrato; devolve o fim. */
    private float card(HoloCanvas c, BountyEntry e, float x, float y, float iw) {
        int col = e.onMe() ? HoloStyle.RED : e.hunting() ? HoloStyle.GREEN : e.mine() ? HoloStyle.GOLD : accent();
        boolean track = e.hunting();
        float h = track ? 52F : 41F;
        c.fill(x, y, iw, h, Ui.alpha(col, 0.07F));
        c.frame(x, y, iw, h, Ui.alpha(col, 0.4F));
        c.fill(x, y, 2F, h, Ui.alpha(col, 0.9F));
        // linha 1: alvo e tempo
        String time = remaining(BountyClientCache.remainingNow(e));
        float tw = c.font.width(time);
        c.text(time, x + iw - 5F - tw, y + 5F, c.muted(), false);
        float nx = x + 7F;
        if (e.onMe()) nx += c.pill(tr("gui.respectcraft.holo.bounty.you"), nx, y + 3F, HoloStyle.RED) + 4F;
        else if (e.mine()) nx += c.pill(tr("gui.respectcraft.holo.bounty.yours"), nx, y + 3F, HoloStyle.GOLD) + 4F;
        else if (e.hunting()) nx += c.pill(tr("gui.respectcraft.holo.bounty.hunting"), nx, y + 3F, HoloStyle.GREEN) + 4F;
        c.text(Ui.fit(c.font, e.targetName(), (int) (x + iw - tw - 10F - nx)), nx, y + 5F, 0xFFFFFFFF, true);
        // linha 2: recompensa e pagador
        String reward = e.reward().getCount() + "× " + e.reward().getHoverName().getString();
        String by = tr("gui.respectcraft.holo.bounty.by", e.posterName());
        float rwMax = iw - 14F - Math.min(c.font.width(by), iw * 0.4F) - 6F;
        String rs = Ui.fit(c.font, reward, (int) rwMax);
        c.text(rs, x + 7F, y + 16F, rarityColor(e.reward()), false);
        c.textRight(Ui.fit(c.font, by, (int) (iw * 0.4F)), x + iw - 5F, y + 16F, c.muted(), false);
        // linha 3: cacadores e acoes
        c.text(tr("gui.respectcraft.holo.bounty.hunters", e.hunters(), BountyRules.MAX_HUNTERS), x + 7F, y + 29F, c.muted(), false);
        float bw = 58F, bx = x + iw - 4F - bw, byy = y + 26F;
        String key = "bounty." + e.shortId();
        if (e.hunting()) {
            c.button(key + ".abandon", tr("gui.respectcraft.holo.bounty.abandon"), bx, byy, bw, 12F, null, HoloStyle.RED, true, false, "",
                    () -> send(BountyAction.ABANDON, e.id(), null));
        } else if (e.mine()) {
            boolean can = e.hunters() == 0;
            c.button(key + ".cancel", tr("gui.respectcraft.holo.bounty.cancel"), bx, byy, bw, 12F, null, HoloStyle.GOLD, can, false, "",
                    () -> send(BountyAction.CANCEL, e.id(), null));
            if (!can && c.over(bx, byy, bw, 12F)) c.tooltip(List.of(Component.translatable(BountyRules.Result.HAS_HUNTERS.translationKey())));
        } else if (!e.onMe()) {
            boolean can = e.hunters() < BountyRules.MAX_HUNTERS;
            c.button(key + ".accept", tr("gui.respectcraft.holo.bounty.accept"), bx, byy, bw, 12F, null, HoloStyle.GREEN, can, false, "",
                    () -> send(BountyAction.ACCEPT, e.id(), null));
        }
        if (track) {
            String t = switch (e.distance()) {
                case BountyEntry.TRACK_OFFLINE -> tr("gui.respectcraft.holo.bounty.track_offline");
                case BountyEntry.TRACK_OTHER_DIMENSION -> tr("gui.respectcraft.holo.bounty.track_dimension");
                case BountyEntry.TRACK_NONE -> tr("gui.respectcraft.holo.bounty.track_unknown");
                default -> tr("gui.respectcraft.holo.bounty.track", e.distance(),
                        e.direction() >= 0 && e.direction() < 8 ? NoticeRules.WINDS[e.direction()] : "");
            };
            c.text(Ui.fit(c.font, "⌖ " + t, (int) (iw - 14F)), x + 7F, y + 41F, HoloStyle.GREEN, false);
        }
        if (c.over(x, y, iw, 22F)) c.tooltip(List.of(Component.literal(e.targetName()).withStyle(ChatFormatting.BOLD),
                Component.literal(reward).withStyle(s -> s.withColor(rarityColor(e.reward()) & 0xFFFFFF)),
                Component.translatable("gui.respectcraft.holo.bounty.by", e.posterName()).withStyle(ChatFormatting.GRAY),
                Component.literal("#" + e.shortId()).withStyle(ChatFormatting.DARK_GRAY)));
        return y + h;
    }

    /** Seletor "◀ valor ▶". */
    private void selector(HoloCanvas c, String key, float x, float y, float w, String value, boolean enabled,
                          java.util.function.IntConsumer step) {
        // 20 de largura: o botao reserva margens e cortaria o glifo num botao mais estreito
        float bw = 20F;
        c.button(key + ".prev", "◀", x, y, bw, 13F, null, accent(), enabled, false, "", () -> step.accept(-1));
        c.button(key + ".next", "▶", x + w - bw, y, bw, 13F, null, accent(), enabled, false, "", () -> step.accept(1));
        String shown = Ui.fit(c.font, value, (int) (w - 2F * bw - 6F));
        c.textCentered(shown, x + w / 2F, y + 3F, c.text(), false);
        HoloZone z = c.zone(x + bw, y, w - 2F * bw, 13F);
        z.scroll = amount -> step.accept(amount > 0 ? -1 : 1);
    }

    private static List<PlayerInfo> others(HoloCanvas c) {
        List<PlayerInfo> list = new ArrayList<>();
        if (c.mc.getConnection() == null || c.mc.player == null) return list;
        UUID me = c.mc.player.getUUID();
        for (PlayerInfo p : c.mc.getConnection().getOnlinePlayers()) if (!p.getProfile().getId().equals(me)) list.add(p);
        list.sort(Comparator.comparing(p -> p.getProfile().getName(), String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    private PlayerInfo picked(List<PlayerInfo> others) {
        if (others.isEmpty()) return null;
        for (PlayerInfo p : others) if (p.getProfile().getId().equals(pickedTarget)) return p;
        pickedTarget = others.get(0).getProfile().getId();
        return others.get(0);
    }

    private static int rarityColor(ItemStack stack) {
        Integer c = stack.getRarity().color().getColor();
        return c == null ? 0xFFFFFFFF : 0xFF000000 | c;
    }

    /** Tempo real restante (20 ticks/s). */
    static String remaining(long ticks) {
        long s = ticks / 20L;
        if (s >= 3600) return (s / 3600) + "h " + String.format(java.util.Locale.ROOT, "%02d", (s % 3600) / 60) + "m";
        return (s / 60) + ":" + String.format(java.util.Locale.ROOT, "%02d", s % 60);
    }

    /** Icone do botao (a caveira das habilidades). */
    private static final class SkillDefIcon {
        static final net.minecraft.resources.ResourceLocation SKULL = com.respectcraft.skill.SkillDef.iconTexture("caveira");
    }
}
