package com.respectcraft.client.fruit;

import com.respectcraft.entity.fruit.FruitMob;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Musica das frutas humanoides (Etapa 4.13.3), so no cliente: um loop espacializado POR fruta ({@link FruitMob}), preso a
 * entidade (acompanha o movimento, atenuacao linear do {@code sounds.json}), nunca reiniciado por movimento ou IA. Uma
 * instancia por entidade; no maximo {@link #MAX_SOURCES} ao mesmo tempo, as mais proximas primeiro. Categoria
 * "Jukebox/Note Blocks" do vanilla, mais o volume proprio ({@code respectcraft-fruits-client.toml}).
 *
 * <p>Corte seco: o servidor liga {@link FruitMob#musicSilenced()} no impacto decisivo de uma finalizacao (dado
 * sincronizado); aqui o som para no mesmo tick, sem fade. Restaurado o flag (cancelamento, fruta viva), o loop volta do
 * comeco. Limpeza: morte (morrendo = sem musica), fim do rastreio/despawn/chunk (a entidade sai do nivel), troca de
 * dimensao/nivel e logout ({@link #clear}).
 */
public final class FruitMusicClient {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.DoubleValue VOLUME;
    public static final ModConfigSpec.IntValue MAX_SOURCES;
    /** Alcance da selecao (blocos); a atenuacao do som termina antes (sounds.json, 20 blocos). */
    static final double RANGE = 24.0;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("Humanoid fruit songs (strawberry and future fruits). Client only.").push("fruits");
        ENABLED = b.comment("Play the looping song of nearby fruits.").define("music", true);
        VOLUME = b.comment("Volume of the fruit songs (also scaled by the Jukebox/Note Blocks slider).")
                .defineInRange("musicVolume", 0.8, 0.0, 1.0);
        MAX_SOURCES = b.comment("Maximum fruits singing at the same time (the nearest ones win).")
                .defineInRange("maxMusicSources", 4, 1, 16);
        b.pop();
        SPEC = b.build();
    }

    private static final Map<Integer, Song> PLAYING = new HashMap<>();
    private static ClientLevel lastLevel;

    private FruitMusicClient() {}

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level != lastLevel) {
            clear();
            lastLevel = level;
        }
        if (level == null || mc.player == null) return;
        if (!ENABLED.get() || VOLUME.get() <= 0) {
            clear();
            lastLevel = level;
            return;
        }
        List<FruitMob> near = new ArrayList<>();
        for (Entity e : level.getEntities(mc.player, mc.player.getBoundingBox().inflate(RANGE))) {
            if (e instanceof FruitMob fruit && sings(fruit) && fruit.distanceToSqr(mc.player) <= RANGE * RANGE) near.add(fruit);
        }
        near.sort(Comparator.comparingDouble(f -> f.distanceToSqr(mc.player)));
        Set<Integer> wanted = new HashSet<>();
        for (int i = 0; i < Math.min(MAX_SOURCES.get(), near.size()); i++) wanted.add(near.get(i).getId());
        // Para o que saiu da selecao (longe, limite, silenciada, morta) ou ja parou.
        PLAYING.entrySet().removeIf(e -> {
            if (wanted.contains(e.getKey()) && !e.getValue().isStopped()) return false;
            e.getValue().halt(e.getValue().isStopped() ? "silenced/died" : "out of selection");
            return true;
        });
        for (FruitMob fruit : near) {
            if (!wanted.contains(fruit.getId()) || PLAYING.containsKey(fruit.getId())) continue;
            Song song = new Song(fruit);
            PLAYING.put(fruit.getId(), song);
            mc.getSoundManager().play(song);
            com.respectcraft.RespectCraft.LOGGER.debug("Fruit music: start {} ({} playing)", fruit.getId(), PLAYING.size());
        }
    }

    /** Pode cantar agora: viva, no nivel, sem o corte da finalizacao. */
    static boolean sings(FruitMob fruit) {
        return fruit.isAlive() && !fruit.isRemoved() && !fruit.isDeadOrDying() && !fruit.musicSilenced() && fruit.musicSound() != null;
    }

    /** A entidade saiu do nivel do cliente (despawn, fim do rastreio, chunk, morte): para a musica dela. */
    public static void forget(int entityId) {
        Song song = PLAYING.remove(entityId);
        if (song != null) song.halt("left the level");
    }

    public static void clear() {
        for (Song song : PLAYING.values()) song.halt("cleared");
        PLAYING.clear();
        lastLevel = null;
    }

    /** Diagnostico: fontes tocando agora. */
    public static int playing() { return PLAYING.size(); }

    /** Loop preso a uma fruta. */
    static final class Song extends AbstractTickableSoundInstance {
        private final FruitMob fruit;

        Song(FruitMob fruit) {
            super(fruit.musicSound(), SoundSource.RECORDS, SoundInstance.createUnseededRandom());
            this.fruit = fruit;
            this.looping = true;
            this.delay = 0;
            this.attenuation = SoundInstance.Attenuation.LINEAR;
            this.volume = VOLUME.get().floatValue();
            follow();
        }

        private void follow() {
            this.x = fruit.getX();
            this.y = fruit.getY() + fruit.getBbHeight() * 0.6;
            this.z = fruit.getZ();
        }

        @Override
        public void tick() {
            if (!sings(fruit)) {
                // Corte seco no tick em que o flag chega: o SoundEngine para o canal ainda nesta passada (sem fade).
                // Aqui so stop(): o motor esta percorrendo os sons e para os marcados logo depois.
                if (isStopped()) return;
                stop();
                com.respectcraft.RespectCraft.LOGGER.debug("Fruit music: stop {} ({})", fruit.getId(),
                        fruit.musicSilenced() ? "silenced" : "died/removed");
                return;
            }
            this.volume = VOLUME.get().floatValue();
            follow();
        }

        /** Corte seco (sem fade): sai do motor de som agora. */
        void halt(String reason) {
            if (isStopped()) return;
            stop();
            Minecraft.getInstance().getSoundManager().stop(this);
            com.respectcraft.RespectCraft.LOGGER.debug("Fruit music: stop {} ({})", fruit.getId(), reason);
        }

        @Override
        public boolean canStartSilent() { return true; }
    }
}
