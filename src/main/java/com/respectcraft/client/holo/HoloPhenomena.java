package com.respectcraft.client.holo;

import com.respectcraft.notice.HoloNotice;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Ponto de extensao para os futuros <b>Holographic UI Phenomena</b> (Etapa 4.14.1; o sistema completo e de uma etapa
 * futura, veja o roadmap). Separa o <i>conteudo</i> (o {@link HoloNotice}), o <i>estado</i> (fila/tempo em
 * {@code NoticeClientCache}) e a <i>apresentacao</i> (os renderizadores) de <i>modificadores visuais</i>: cada
 * renderizador monta um {@link Look} a partir do estilo e o passa por aqui antes de desenhar.
 *
 * <p>Fontes de modificadores: os ids que o servidor pos no aviso ({@link HoloNotice#modifiers()}) e os
 * modificadores "globais" ligados por sistemas do cliente (ex.: futuramente uma dimensao que interfere no
 * holograma). Hoje <b>nenhum</b> modificador vem registrado: ids desconhecidos sao ignorados. Puramente estetico: um
 * fenomeno que mude regras de jogo precisa ser decidido no servidor, nunca aqui.
 */
public final class HoloPhenomena {
    private HoloPhenomena() {}

    /** Como algo holografico vai ser desenhado neste quadro; os modificadores mexem aqui. */
    public static final class Look {
        public int accent;
        public float alpha = 1F;
        public float offsetX, offsetY;
        /** 0..1: quanto o vidro "falha" (o renderizador decide como mostrar; 0 = nada). */
        public float interference;
        /** Transformacao do texto (ex.: corromper letras). Identidade por padrao. */
        public UnaryOperator<String> text = UnaryOperator.identity();

        public Look(int accent) { this.accent = accent; }
    }

    /** Um fenomeno visual: recebe o aviso (ou null para outros paineis), a idade em segundos e o visual. */
    @FunctionalInterface
    public interface Modifier {
        void apply(Look look, HoloNotice notice, float ageSeconds);
    }

    private static final Map<String, Modifier> REGISTRY = new LinkedHashMap<>();
    private static final List<String> GLOBAL = new ArrayList<>();

    /** Registra um fenomeno pelo id (feito no setup do cliente). */
    public static synchronized void register(String id, Modifier modifier) { REGISTRY.put(id, modifier); }

    /** Liga/desliga um fenomeno em tudo o que e holografico (estado visual do cliente, limpo no logout). */
    public static synchronized void setGlobal(String id, boolean on) {
        GLOBAL.remove(id);
        if (on) GLOBAL.add(id);
    }

    public static synchronized void clear() { GLOBAL.clear(); }

    /** Aplica os globais e os do aviso, nessa ordem. */
    public static synchronized Look apply(Look look, HoloNotice notice, float age) {
        for (String id : GLOBAL) run(id, look, notice, age);
        if (notice != null) for (String id : notice.modifiers()) run(id, look, notice, age);
        return look;
    }

    private static void run(String id, Look look, HoloNotice notice, float age) {
        Modifier m = REGISTRY.get(id);
        if (m != null) m.apply(look, notice, age);
    }
}
