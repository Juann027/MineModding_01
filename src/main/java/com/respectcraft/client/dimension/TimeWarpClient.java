package com.respectcraft.client.dimension;

import com.mojang.blaze3d.shaders.FogShape;
import com.respectcraft.dimension.timewarp.TimeWarp;
import com.respectcraft.dimension.timewarp.TimeWarpRules;
import com.respectcraft.dimension.timewarp.TimeWarpRules.Stage;
import com.respectcraft.network.TimeWarpNetworking;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Time Warp no cliente (Etapa 4.15, so exibicao): neblina densa e palida por estagio, pistas do servidor (sons
 * posicionados, passos, pulso de neblina, silencio) e a limpeza ao sair. O servidor decide tudo; aqui so se toca e
 * desenha. Estado estatico = cache visual, limpo ao sair da dimensao, ao trocar de nivel e no logout.
 *
 * <p><b>Neblina:</b> {@code ViewportEvent.RenderFog} (terreno e ceu) fixa o plano distante em ~34 blocos no estagio
 * estavel ate ~22 no critico (nunca alem de 75% da distancia de renderizacao), independente da distancia de renderizacao e do modo grafico (a neblina e um uniforme do
 * shader em qualquer configuracao). Debaixo d'agua/lava/neve fica o vanilla. A cor perde saturacao com o estagio.
 * Pulsos so com "Efeitos de distorcao" acima de 0 (escala {@code screenEffectScale}) e {@code fogPulses} ligado.
 *
 * <p><b>Audio:</b> categoria Ambiente do vanilla x {@code ambientVolume} desta config. Sons distantes sao trazidos para
 * no maximo {@link #NEAR} blocos na mesma direcao, com volume reduzido (o vanilla corta som alem de 16 blocos).
 */
public final class TimeWarpClient {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue VOLUME;
    public static final ModConfigSpec.BooleanValue FOG_PULSES;
    public static final ModConfigSpec.DoubleValue FOG_SCALE;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("Time Warp atmosphere (client only).").push("time_warp");
        VOLUME = b.comment("Volume of Time Warp happenings (also scaled by the Ambient/Environment slider). 0 = silent.")
                .defineInRange("ambientVolume", 0.8, 0.0, 1.0);
        FOG_PULSES = b.comment("Brief fog pulses (also off when Distortion Effects is 0).").define("fogPulses", true);
        FOG_SCALE = b.comment("Multiplier of the Time Warp fog distance (accessibility; the fog is always dense).")
                .defineInRange("fogDistanceScale", 1.0, 0.8, 1.6);
        b.pop();
        SPEC = b.build();
    }

    static final double NEAR = 12.0;

    private static boolean inWarp;
    private static Stage stage = Stage.STABLE;
    private static float pulseStrength;
    private static int pulseTicks, pulseLength;
    private static int silenceTicks;
    private static final List<Step> STEPS = new ArrayList<>();
    private static final List<SoundInstance> PLAYING = new ArrayList<>();
    private static ClientLevel lastLevel;
    private static final RandomSource RANDOM = RandomSource.create();

    private record Step(ResourceLocation sound, double x, double y, double z, float volume, float pitch, int at) {}

    private static int clock;

    private TimeWarpClient() {}

    // ================================================================================================ estado

    public static void onState(boolean warp, int stageOrdinal) {
        boolean was = inWarp;
        inWarp = warp;
        stage = Stage.byOrdinal(stageOrdinal);
        if (was && !warp) clear();
    }

    static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.dimension() == TimeWarp.LEVEL;
    }

    /** Sai da dimensao/logout: nada do Time Warp continua tocando ou desenhando. */
    public static void clear() {
        Minecraft mc = Minecraft.getInstance();
        for (SoundInstance s : PLAYING) mc.getSoundManager().stop(s);
        PLAYING.clear();
        STEPS.clear();
        pulseTicks = pulseLength = 0;
        pulseStrength = 0;
        silenceTicks = 0;
    }

    public static void onLogout() {
        clear();
        inWarp = false;
        stage = Stage.STABLE;
        lastLevel = null;
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            clear();
            lastLevel = mc.level;
        }
        if (mc.level == null || mc.player == null) return;
        if (!active()) {
            if (!STEPS.isEmpty() || !PLAYING.isEmpty() || silenceTicks > 0) clear();
            return;
        }
        clock++;
        if (pulseTicks > 0) pulseTicks--;
        if (silenceTicks > 0) silenceTicks--;
        PLAYING.removeIf(s -> !mc.getSoundManager().isActive(s));
        for (Iterator<Step> it = STEPS.iterator(); it.hasNext(); ) {
            Step s = it.next();
            if (s.at() > clock) continue;
            it.remove();
            play(s.sound(), s.x(), s.y(), s.z(), s.volume(), s.pitch());
        }
    }

    // ================================================================================================ pistas

    public static void onCue(TimeWarpNetworking.Cue cue) {
        if (!active()) return;
        Minecraft mc = Minecraft.getInstance();
        switch (cue.kind()) {
            case TimeWarpNetworking.CUE_SOUND -> play(cue.sound(), cue.x(), cue.y(), cue.z(), cue.volume(), cue.pitch());
            case TimeWarpNetworking.CUE_STEPS -> {
                int n = Mth.clamp(cue.count(), 1, 16);
                for (int i = 0; i < n; i++) {
                    if (STEPS.size() >= 48) break;
                    STEPS.add(new Step(cue.sound(), cue.x() + cue.dx() * i, cue.y(), cue.z() + cue.dz() * i,
                            cue.volume() * (0.85F + RANDOM.nextFloat() * 0.15F), 0.9F + RANDOM.nextFloat() * 0.15F,
                            clock + 1 + i * Math.max(2, cue.interval())));
                }
            }
            case TimeWarpNetworking.CUE_FOG -> {
                if (!FOG_PULSES.get()) return;
                float scale = mc.options.screenEffectScale().get().floatValue();
                if (scale <= 0F) return;
                pulseStrength = Mth.clamp(cue.strength(), 0F, 0.7F) * scale;
                pulseLength = pulseTicks = Mth.clamp(cue.interval(), 20, 400);
            }
            case TimeWarpNetworking.CUE_SILENCE -> {
                silenceTicks = Mth.clamp(cue.interval(), 20, 600);
                mc.getMusicManager().stopPlaying();
                mc.getSoundManager().stop(null, SoundSource.AMBIENT);
                mc.getSoundManager().stop(null, SoundSource.WEATHER);
            }
            default -> {}
        }
    }

    private static void play(ResourceLocation sound, double x, double y, double z, float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        float v = (float) (volume * VOLUME.get());
        if (v <= 0F || mc.player == null || PLAYING.size() >= 8) return;
        Vec3 ear = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 at = new Vec3(x, y, z);
        double d = at.distanceTo(ear);
        if (d > NEAR) {
            // Mesmo lado, mais perto e mais baixo: parece longe sem sumir no corte de 16 blocos do vanilla.
            at = ear.add(at.subtract(ear).normalize().scale(NEAR));
            v *= (float) Mth.clamp(1.0 - (d - NEAR) / 48.0, 0.3, 1.0);
        }
        SimpleSoundInstance s = new SimpleSoundInstance(sound, SoundSource.AMBIENT, v, pitch, RANDOM, false, 0,
                SoundInstance.Attenuation.LINEAR, at.x, at.y, at.z, false);
        PLAYING.add(s);
        mc.getSoundManager().play(s);
    }

    /** Silencio repentino: nada novo de ambiente, clima ou musica toca (os sons do proprio Time Warp passam). */
    public static void onPlaySound(PlaySoundEvent event) {
        if (silenceTicks <= 0 || !active() || event.getSound() == null) return;
        SoundSource src = event.getSound().getSource();
        if ((src == SoundSource.AMBIENT || src == SoundSource.WEATHER || src == SoundSource.MUSIC) && !PLAYING.contains(event.getSound())) {
            event.setSound(null);
        }
    }

    // ================================================================================================ neblina

    static float pulse() {
        if (pulseTicks <= 0 || pulseLength <= 0) return 0F;
        float t = 1F - pulseTicks / (float) pulseLength;
        return pulseStrength * Mth.sin(t * Mth.PI);
    }

    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (!active() || event.getType() != FogType.NONE) return;
        Camera camera = event.getCamera();
        if (camera.getEntity() instanceof net.minecraft.world.entity.LivingEntity le
                && (le.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS) || le.hasEffect(net.minecraft.world.effect.MobEffects.DARKNESS))) return;
        float end = TimeWarpRules.fogEnd(stage) * FOG_SCALE.get().floatValue() * (1F - pulse());
        // Nunca alem de 75% da distancia de renderizacao: a borda do mundo carregado fica sempre dentro da neblina.
        float render = Minecraft.getInstance().gameRenderer.getRenderDistance();
        end = Math.min(end, render * 0.75F);
        boolean sky = event.getMode() == FogRenderer.FogMode.FOG_SKY;
        event.setNearPlaneDistance(0F);
        event.setFarPlaneDistance(sky ? end * 1.25F : end);
        event.setFogShape(FogShape.SPHERE);
        event.setCanceled(true);
    }

    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (!active()) return;
        if (event.getCamera().getFluidInCamera() != FogType.NONE) return;
        float r = event.getRed(), g = event.getGreen(), b = event.getBlue();
        float gray = (r + g + b) / 3F;
        float k = Mth.clamp(TimeWarpRules.fogGray(stage) + pulse() * 0.4F, 0F, 0.8F);
        event.setRed(r + (gray - r) * k);
        event.setGreen(g + (gray - g) * k);
        event.setBlue(b + (gray - b) * k);
    }

    /** Diagnostico/teste visual: estagio que o cliente acha que esta. */
    public static Stage stage() { return stage; }
}
