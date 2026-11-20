package com.respectcraft.client.character;

import com.respectcraft.character.CharacterAppearance;
import com.respectcraft.network.CharacterAppearanceClientCache;
import com.respectcraft.network.CharacterAppearanceUpdatePayload;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Rascunho do criador de personagem, sem desenho (Etapa 4.8.4-C): Body, Hairstyle e Hair Color (Skin e Face foram
 * removidos; a aparencia salva usa sempre a pele e o rosto da skin da conta). A previa e so local
 * ({@link CharacterAppearanceClientCache#setPreview}); nada e salvo antes de Save, e o criador so termina quando o
 * servidor confirma a aparencia pedida (sincronizacao com {@code revision} maior), com timeout de
 * {@link #TIMEOUT_TICKS}. Uma unica implementacao para a tela antiga (reserva) e a placa holografica.
 */
public final class AppearanceDraft {
    public static final int TIMEOUT_TICKS = 200;

    public enum State { EDITING, SAVING, SAVED, ERROR }

    private int bodyType, hairStyle, hairColor;
    private State state = State.EDITING;
    private int requested;
    private long revisionBefore;
    private int waitTicks;

    public AppearanceDraft() {
        Minecraft mc = Minecraft.getInstance();
        CharacterAppearance current = mc.player == null ? CharacterAppearance.defaults()
                : CharacterAppearanceClientCache.confirmed(mc.player.getUUID());
        bodyType = current.bodyType();
        hairStyle = current.hairStyle();
        hairColor = current.hairColor();
        preview();
    }

    /** Ainda sem aparencia confirmada pelo servidor (primeira criacao). */
    public static boolean firstTime() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null || !CharacterAppearanceClientCache.confirmed(mc.player.getUUID()).created();
    }

    public int bodyType() { return bodyType; }
    public int hairStyle() { return hairStyle; }
    public int hairColor() { return hairColor; }
    public State state() { return state; }
    public boolean saving() { return state == State.SAVING; }

    public void setBodyType(int v) { bodyType = wrap(v, CharacterAppearance.BODY_IDS.length); edited(); }
    public void setHairStyle(int v) { hairStyle = wrap(v, CharacterAppearance.HAIR_IDS.length); edited(); }
    public void setHairColor(int v) { hairColor = wrap(v, CharacterAppearance.COLOR_IDS.length); edited(); }

    public void randomize() {
        RandomSource r = RandomSource.create();
        bodyType = r.nextInt(CharacterAppearance.BODY_IDS.length);
        hairStyle = r.nextInt(CharacterAppearance.HAIR_IDS.length);
        hairColor = r.nextInt(CharacterAppearance.COLOR_IDS.length);
        edited();
    }

    private void edited() {
        if (state == State.ERROR) state = State.EDITING;
        preview();
    }

    private static int wrap(int v, int size) { return Math.floorMod(v, size); }

    /** Aparencia do rascunho (pele e rosto da conta). */
    public CharacterAppearance appearance() {
        return new CharacterAppearance(true, bodyType, hairStyle, hairColor, CharacterAppearance.SKIN_FROM_ACCOUNT,
                CharacterAppearance.FACE_FROM_ACCOUNT);
    }

    /** O modelo 3D do proprio jogador mostra o rascunho (so neste cliente). */
    public void preview() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) CharacterAppearanceClientCache.setPreview(mc.player.getUUID(), appearance());
    }

    /** Envia ao servidor; falso se ja esta esperando a confirmacao. */
    public boolean save() {
        Minecraft mc = Minecraft.getInstance();
        if (state == State.SAVING || mc.player == null) return false;
        requested = appearance().pack();
        revisionBefore = CharacterAppearanceClientCache.revision(mc.player.getUUID());
        waitTicks = 0;
        state = State.SAVING;
        PacketDistributor.sendToServer(new CharacterAppearanceUpdatePayload(requested));
        return true;
    }

    /** Um tick do cliente. Devolve true no tick em que o servidor confirmou a aparencia pedida (a previa e limpa). */
    public boolean tick() {
        if (state != State.SAVING) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            state = State.EDITING;
            return false;
        }
        UUID uuid = mc.player.getUUID();
        if (CharacterAppearanceClientCache.revision(uuid) > revisionBefore) {
            CharacterAppearance confirmed = CharacterAppearanceClientCache.confirmed(uuid);
            if (confirmed.created() && confirmed.pack() == requested) {
                state = State.SAVED;
                CharacterAppearanceClientCache.clearPreview();
                return true;
            }
        }
        if (++waitTicks >= TIMEOUT_TICKS) state = State.ERROR;
        return false;
    }

    /** Sai sem salvar (ou depois de salvar): o modelo volta a aparencia confirmada. */
    public void discard() { CharacterAppearanceClientCache.clearPreview(); }
}
