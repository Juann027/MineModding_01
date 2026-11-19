package com.respectcraft.network;

import com.respectcraft.needs.ActiveNeedEffects;
import com.respectcraft.needs.NeedEffectDef;
import com.respectcraft.needs.NeedEffects;
import com.respectcraft.needs.Needs;
import com.respectcraft.profile.CharacterProfile;
import com.respectcraft.profile.SpecialityOdds;

/** Copia local (so exibicao) da ficha e das necessidades do proprio jogador. Limpa no logout. */
public final class ProfileClientCache {
    private static CharacterProfile profile = CharacterProfile.DEFAULT;
    private static Needs needs = Needs.DEFAULT;
    private static ActiveNeedEffects effects = ActiveNeedEffects.EMPTY;
    private static long effectsReceivedAt;
    private static long profileRevision;
    private static boolean received;
    private static GameplayNetworking.BirthRollReply lastRollReply;
    private static long rollReplyRevision;
    private static SpecialityOdds odds = SpecialityOdds.DEFAULT;
    /** Contador dos pedidos de giro desta sessao do cliente (so identificacao nos logs; nao e zerado no logout). */
    private static int rollRequestCounter;

    private ProfileClientCache() {}

    static void receive(CharacterProfile value) {
        profile = value;
        profileRevision++;
        received = true;
    }

    static void receive(Needs value) { needs = value; }

    static void receive(GameplayNetworking.BirthRollReply reply) {
        lastRollReply = reply;
        rollReplyRevision++;
    }

    static void receive(SpecialityOdds value) { odds = value; }

    /** Chances de raridade do servidor (so exibicao; a padrao ate o servidor mandar). */
    public static SpecialityOdds odds() { return odds; }

    /** Ultima resposta do servidor a um pedido de giro (null se nenhuma nesta sessao). */
    public static GameplayNetworking.BirthRollReply lastRollReply() { return lastRollReply; }
    /** Id novo para um pedido de giro (casa a resposta e as linhas de log do cliente e do servidor). */
    public static int nextRollRequestId() { return ++rollRequestCounter & 0x7FFFFFFF; }
    /** Muda a cada resposta de giro recebida (a roleta detecta a resposta ao seu pedido). */
    public static long rollReplyRevision() { return rollReplyRevision; }

    static void receive(ActiveNeedEffects value) {
        effects = value;
        effectsReceivedAt = System.currentTimeMillis();
    }

    /** Efeitos de consumiveis em andamento e segundos restantes estimados desde o ultimo pacote. */
    public static java.util.List<java.util.Map.Entry<NeedEffectDef, Integer>> activeEffects() {
        int passed = (int) ((System.currentTimeMillis() - effectsReceivedAt) / 1000L);
        java.util.List<java.util.Map.Entry<NeedEffectDef, Integer>> list = new java.util.ArrayList<>();
        for (ActiveNeedEffects.Entry e : effects.entries()) {
            NeedEffectDef def = NeedEffects.get(e.id());
            if (def == null) continue;
            int left = def.lifetimeSeconds() - e.elapsed() - passed;
            if (left > 0) list.add(java.util.Map.entry(def, left));
        }
        return list;
    }

    public static CharacterProfile profile() { return profile; }
    public static Needs needs() { return needs; }
    /** Muda a cada ficha recebida (telas detectam a resposta do servidor). */
    public static long revision() { return profileRevision; }
    /** O servidor ja mandou a ficha nesta sessao. */
    public static boolean received() { return received; }

    public static void clear() {
        profile = CharacterProfile.DEFAULT;
        needs = Needs.DEFAULT;
        effects = ActiveNeedEffects.EMPTY;
        profileRevision = 0;
        received = false;
        lastRollReply = null;
        rollReplyRevision = 0;
        odds = SpecialityOdds.DEFAULT;
    }
}
