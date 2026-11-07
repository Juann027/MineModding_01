package com.tupinicraft.network;

import com.tupinicraft.TupiniCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/**
 * Servidor → cliente: o que a tela da árvore precisa desenhar (abas e nós), com os textos já prontos.
 * Enviado ao entrar e após {@code /reload}. O cliente não conhece custos nem regras; só exibe.
 */
public record SkillDefinitionsPayload(List<TreeView> trees, List<SkillView> skills) implements CustomPacketPayload {
    public static final Type<SkillDefinitionsPayload> TYPE = new Type<>(TupiniCraft.id("skill_definitions"));

    /** Aba: nome, ícone (item), textura de fundo. */
    public record TreeView(String id, String name, String icon, String background) {
        public static final StreamCodec<ByteBuf, TreeView> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, TreeView::id,
                ByteBufCodecs.STRING_UTF8, TreeView::name,
                ByteBufCodecs.STRING_UTF8, TreeView::icon,
                ByteBufCodecs.STRING_UTF8, TreeView::background,
                TreeView::new);
    }

    /** Textos e aparência de um nó. */
    public record Display(String name, String description, String icon, String frame, String costLine, String requirementLine) {
        public static final StreamCodec<ByteBuf, Display> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Display::name,
                ByteBufCodecs.STRING_UTF8, Display::description,
                ByteBufCodecs.STRING_UTF8, Display::icon,
                ByteBufCodecs.STRING_UTF8, Display::frame,
                ByteBufCodecs.STRING_UTF8, Display::costLine,
                ByteBufCodecs.STRING_UTF8, Display::requirementLine,
                Display::new);
    }

    /** Nó: árvore, pai ("" = raiz) e aparência. A ordem da lista é a ordem de desenho dos filhos. */
    public record SkillView(String id, String tree, String parent, Display display) {
        public static final StreamCodec<ByteBuf, SkillView> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, SkillView::id,
                ByteBufCodecs.STRING_UTF8, SkillView::tree,
                ByteBufCodecs.STRING_UTF8, SkillView::parent,
                Display.STREAM_CODEC, SkillView::display,
                SkillView::new);
    }

    public static final StreamCodec<ByteBuf, SkillDefinitionsPayload> STREAM_CODEC = StreamCodec.composite(
            TreeView.STREAM_CODEC.apply(ByteBufCodecs.list()), SkillDefinitionsPayload::trees,
            SkillView.STREAM_CODEC.apply(ByteBufCodecs.list()), SkillDefinitionsPayload::skills,
            SkillDefinitionsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
