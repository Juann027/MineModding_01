package com.tupinicraft.network;

import com.tupinicraft.needs.Needs;
import com.tupinicraft.profile.CharacterProfile;

/** Copia local (so exibicao) da ficha e das necessidades do proprio jogador. Limpa no logout. */
public final class ProfileClientCache {
    private static CharacterProfile profile = CharacterProfile.DEFAULT;
    private static Needs needs = Needs.DEFAULT;
    private static long profileRevision;
    private static boolean received;

    private ProfileClientCache() {}

    static void receive(CharacterProfile value) {
        profile = value;
        profileRevision++;
        received = true;
    }

    static void receive(Needs value) { needs = value; }

    public static CharacterProfile profile() { return profile; }
    public static Needs needs() { return needs; }
    /** Muda a cada ficha recebida (telas detectam a resposta do servidor). */
    public static long revision() { return profileRevision; }
    /** O servidor ja mandou a ficha nesta sessao. */
    public static boolean received() { return received; }

    public static void clear() {
        profile = CharacterProfile.DEFAULT;
        needs = Needs.DEFAULT;
        profileRevision = 0;
        received = false;
    }
}
