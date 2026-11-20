package com.respectcraft.client.holo;

import com.respectcraft.client.ui.Ui;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Janela holografica que abre na frente do arco (Etapa 4.8.4-C): o criador de personagem ({@link CreatorView}) e a
 * roleta de nascimento ({@link RouletteView}). Vive na placa {@link HoloMenu#popup}, desenhada com o mesmo
 * {@link HoloCanvas}, as mesmas superficies e a mesma passada 3D das outras placas; enquanto aberta ela e a unica que
 * responde (como o painel Display) e o arco fica recuado e esmaecido atras dela.
 *
 * <p>So apresentacao: o estado do criador e da roleta e do {@code AppearanceDraft} e do {@code RouletteSession},
 * os mesmos da tela de reserva.
 */
abstract class HoloPopup {
    enum Kind { CREATOR, ROULETTE }

    static final String KEY = "gui.respectcraft.holo.popup.";
    /** Maior tamanho de janela (unidades de layout): a superficie nao e realocada na troca criador -> roleta. */
    static final float MAX_W = 430F, MAX_H = 270F;

    final HoloMenu menu;
    final Kind kind;

    HoloPopup(HoloMenu menu, Kind kind) {
        this.menu = menu;
        this.kind = kind;
    }

    abstract float width();
    abstract float height();
    abstract int accent();
    abstract void draw(HoloCanvas c, float w, float h, float since);

    /** Um tick do cliente (so enquanto aberta). */
    void tick() {}

    /** Roda sobre a janela fora de um seletor; true se usou. */
    boolean scrollBy(double amount) { return false; }

    /** Fechou (ou os paineis fecharam): limpa o que for so desta janela (ex.: previa da aparencia). */
    void closed() {}

    /**
     * Vidro, titulo, passo do primeiro personagem ("Step 1 of 2") e o botao de fechar os paineis. Devolve a borda
     * esquerda ocupada a direita do titulo.
     */
    void header(HoloCanvas c, float w, float since, String titleKey, int step) {
        c.glass(w, c.panel.h, accent());
        if (HoloConfig.motion() > 0F) c.scan(w, c.panel.h, ((c.time * 0.16F) % 1.4F) / 1.2F, accent());
        c.alpha = c.panel.contentAlpha * c.reveal(since, 0, 0F);
        float x = 10F;
        x += c.type(HoloCanvas.Type.MONO, kind == Kind.CREATOR ? "A" : "B", x, 10F, 1.15F, accent()) + 7F;
        c.fill(x - 4F, 8F, 1F, 11F, Ui.alpha(accent(), 0.5F));
        float closeX = w - 22F;
        float right = closeX - 6F;
        if (step > 0) {
            String s = HoloView.tr(KEY + "step", step, 2).toUpperCase(Locale.ROOT);
            float sw = c.measure(HoloCanvas.Type.LABEL, s, 1F);
            c.caption(s, right - sw, 11F, c.muted());
            right -= sw + 10F;
        }
        String title = HoloView.tr(titleKey).toUpperCase(Locale.ROOT);
        float size = c.fitSize(HoloCanvas.Type.LABEL, title, 1.3F, 0.8F, right - x - 4F);
        c.type(HoloCanvas.Type.LABEL, title, x, 10F, size, c.text());
        // fechar: dispensa os paineis inteiros (como K); Back volta para onde a janela foi aberta
        boolean hot = c.over(closeX, 5F, 16F, 16F);
        float k = c.hoverAnim("popup.close", hot);
        c.fill(closeX, 5F, 16F, 16F, Ui.alpha(HoloStyle.RED, 0.08F + 0.2F * k));
        c.frame(closeX, 5F, 16F, 16F, Ui.alpha(HoloStyle.RED, 0.35F + 0.5F * k));
        c.textCentered("✕", closeX + 8.5F, 9F, Ui.lerpColor(HoloStyle.RED, 0xFFFFFFFF, 0.3F + 0.5F * k), false);
        c.zone(closeX, 5F, 16F, 16F).click = menu::requestClose;
        menu.hoverSound("popup.close", hot);
        if (hot) c.tooltip(List.of(Component.translatable(KEY + "close").withStyle(ChatFormatting.BOLD),
                Component.translatable(KEY + "close.desc", com.respectcraft.client.character.CharacterCreatorKeybinds.menuKeyName())
                        .withStyle(ChatFormatting.GRAY)));
        c.alpha = c.panel.contentAlpha;
        c.divider(8F, HoloView.HEADER - 2F, w - 16F, accent());
    }

    /** Segundos para o segundo clique de uma acao que gasta algo (novo giro, Continuar com giro sobrando). */
    static final float CONFIRM_WINDOW = 2.5F;
}
