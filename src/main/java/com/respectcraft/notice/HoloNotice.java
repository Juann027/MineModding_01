package com.respectcraft.notice;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * Um aviso holografico (Etapa 4.11): categoria, gravidade, titulo, texto, local opcional (dimensao + bloco, para a
 * distancia/direcao no cliente e o farol do Calamidade) e uma chave de duplicata (o cliente ignora o mesmo aviso
 * repetido dentro de {@link NoticeRules#DEDUPE_MILLIS}). So exibicao: nada aqui muda dados de jogo.
 *
 * <p>Etapa 4.14.1 (Notification HUD V2): o mesmo aviso escolhe a <b>apresentacao</b> ({@link NoticePresentation}:
 * cartao, janela cinematografica ou os dois), o <b>estilo</b> ({@link NoticeStyle}, cor/glifo/prioridade/som),
 * subtitulo, duracao propria ({@code durationMillis}, 0 = padrao da config do jogador), grupo (atualizacoes
 * frequentes do mesmo grupo se substituem em vez de empilhar) e {@code modifiers}: ids de "fenomenos" visuais
 * (Holographic UI Phenomena, etapa futura). O cliente ignora ids que nao conhece.
 */
public record HoloNotice(NoticeKind kind, NoticeSeverity severity, Component title, Component body,
                         Optional<Location> location, String dedupeKey, NoticePresentation presentation,
                         NoticeStyle style, Optional<Component> subtitle, int durationMillis, String group,
                         List<String> modifiers) {
    /** Onde o evento aconteceu. */
    public record Location(ResourceLocation dimension, BlockPos pos) {}

    public static final int MAX_MODIFIERS = 4;

    public HoloNotice {
        dedupeKey = clip(dedupeKey);
        group = clip(group);
        presentation = presentation == null ? NoticePresentation.TOAST : presentation;
        style = style == null ? NoticeStyle.SYSTEM_INFO : style;
        subtitle = subtitle == null ? Optional.empty() : subtitle;
        durationMillis = durationMillis <= 0 ? 0 : NoticeRules.clampCinematicMillis(durationMillis);
        List<String> mods = new ArrayList<>();
        if (modifiers != null) for (String m : modifiers) if (m != null && !m.isEmpty() && mods.size() < MAX_MODIFIERS) mods.add(clip(m));
        modifiers = List.copyOf(mods);
    }

    /** Construtor da Etapa 4.11 (cartao comum). */
    public HoloNotice(NoticeKind kind, NoticeSeverity severity, Component title, Component body,
                      Optional<Location> location, String dedupeKey) {
        this(kind, severity, title, body, location, dedupeKey, NoticePresentation.TOAST, NoticeStyle.SYSTEM_INFO,
                Optional.empty(), 0, "", List.of());
    }

    private static String clip(String s) {
        if (s == null) return "";
        return s.length() > 128 ? s.substring(0, 128) : s;
    }

    public static HoloNotice of(NoticeKind kind, Component title, Component body) {
        return new HoloNotice(kind, kind.defaultSeverity(), title, body, Optional.empty(), "");
    }

    /** Janela cinematografica (sem cartao) do estilo dado. */
    public static HoloNotice cinematic(NoticeKind kind, NoticeStyle style, Component title, Component body) {
        return of(kind, title, body).present(NoticePresentation.CINEMATIC).style(style);
    }

    public HoloNotice at(Level level, BlockPos pos) {
        return new HoloNotice(kind, severity, title, body, Optional.of(new Location(level.dimension().location(), pos.immutable())),
                dedupeKey, presentation, style, subtitle, durationMillis, group, modifiers);
    }

    public HoloNotice severity(NoticeSeverity s) {
        return new HoloNotice(kind, s, title, body, location, dedupeKey, presentation, style, subtitle, durationMillis, group, modifiers);
    }

    public HoloNotice dedupe(String key) {
        return new HoloNotice(kind, severity, title, body, location, key, presentation, style, subtitle, durationMillis, group, modifiers);
    }

    public HoloNotice present(NoticePresentation p) {
        return new HoloNotice(kind, severity, title, body, location, dedupeKey, p, style, subtitle, durationMillis, group, modifiers);
    }

    public HoloNotice style(NoticeStyle s) {
        return new HoloNotice(kind, severity, title, body, location, dedupeKey, presentation, s, subtitle, durationMillis, group, modifiers);
    }

    public HoloNotice subtitle(Component c) {
        return new HoloNotice(kind, severity, title, body, location, dedupeKey, presentation, style, Optional.ofNullable(c), durationMillis, group, modifiers);
    }

    /** Duracao da janela cinematografica; {@link NoticeRules#MIN_CINEMATIC_MILLIS} (0,5 s) a {@link NoticeRules#MAX_CINEMATIC_MILLIS}. */
    public HoloNotice duration(int millis) {
        return new HoloNotice(kind, severity, title, body, location, dedupeKey, presentation, style, subtitle, millis, group, modifiers);
    }

    public HoloNotice group(String key) {
        return new HoloNotice(kind, severity, title, body, location, dedupeKey, presentation, style, subtitle, durationMillis, key, modifiers);
    }

    public HoloNotice modifiers(List<String> ids) {
        return new HoloNotice(kind, severity, title, body, location, dedupeKey, presentation, style, subtitle, durationMillis, group, ids);
    }

    /** Prioridade na fila da janela: a do estilo, +1 para avisos criticos. */
    public int priority() {
        return style.priority() + (severity == NoticeSeverity.CRITICAL ? 1 : 0);
    }

    public boolean showsCard() { return presentation != NoticePresentation.CINEMATIC; }
    public boolean showsCinematic() { return presentation != NoticePresentation.TOAST; }

    public static final StreamCodec<RegistryFriendlyByteBuf, HoloNotice> STREAM_CODEC = StreamCodec.of(
            (buf, n) -> {
                buf.writeVarInt(n.kind().ordinal());
                buf.writeVarInt(n.severity().ordinal());
                ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, n.title());
                ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, n.body());
                buf.writeBoolean(n.location().isPresent());
                n.location().ifPresent(l -> {
                    buf.writeResourceLocation(l.dimension());
                    buf.writeBlockPos(l.pos());
                });
                buf.writeUtf(n.dedupeKey(), 128);
                buf.writeVarInt(n.presentation().ordinal());
                buf.writeVarInt(n.style().ordinal());
                buf.writeBoolean(n.subtitle().isPresent());
                n.subtitle().ifPresent(c -> ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, c));
                buf.writeVarInt(n.durationMillis());
                buf.writeUtf(n.group(), 128);
                buf.writeVarInt(n.modifiers().size());
                for (String m : n.modifiers()) buf.writeUtf(m, 128);
            },
            buf -> {
                NoticeKind kind = NoticeKind.byOrdinal(buf.readVarInt());
                NoticeSeverity severity = NoticeSeverity.byOrdinal(buf.readVarInt());
                Component title = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
                Component body = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
                Optional<Location> location = buf.readBoolean()
                        ? Optional.of(new Location(buf.readResourceLocation(), buf.readBlockPos())) : Optional.empty();
                String dedupe = buf.readUtf(128);
                NoticePresentation presentation = NoticePresentation.byOrdinal(buf.readVarInt());
                NoticeStyle style = NoticeStyle.byOrdinal(buf.readVarInt());
                Optional<Component> subtitle = buf.readBoolean()
                        ? Optional.of(ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf)) : Optional.empty();
                int duration = buf.readVarInt();
                String group = buf.readUtf(128);
                int count = Math.min(buf.readVarInt(), MAX_MODIFIERS);
                List<String> mods = new ArrayList<>(count);
                for (int i = 0; i < count; i++) mods.add(buf.readUtf(128));
                return new HoloNotice(kind, severity, title, body, location, dedupe, presentation, style, subtitle,
                        duration, group, mods);
            });
}
