package com.respectcraft.client.cinematic;

import com.respectcraft.animation.PlayerAnimationDef;
import com.respectcraft.animation.PlayerAnimationEvent;
import com.respectcraft.animation.PlayerAnimationState;
import com.respectcraft.animation.PlayerAnimations;
import com.respectcraft.client.animation.ClientPlayerAnimations;
import com.respectcraft.finisher.CameraTrack;
import com.respectcraft.finisher.FinisherDef;
import com.respectcraft.finisher.Finishers;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Camera dinamica das finalizacoes, so para os PARTICIPANTES (quem assiste continua com a propria camera).
 *
 * <p>Fonte da verdade: o estado de animacao sincronizado pelo servidor. Enquanto o jogador local tem uma
 * animacao de finalizacao ativa, a camera vai para terceira pessoa e segue a {@link CameraTrack} da cena; quando
 * o estado some por qualquer motivo (fim, cancelamento, dano, morte, desconexao, troca de dimensao, logout),
 * a perspectiva anterior e restaurada no mesmo tick. Tremor e FOV vem dos eventos temporizados do clip
 * ({@code camera_shake} = "intensidade,ticks"; {@code camera_fov} = "multiplicador,ticks") e valem para
 * qualquer animacao do jogador local, com ou sem cena.
 *
 * <p>Posicao/rotacao sao aplicadas no {@link ViewportEvent.ComputeFov}, disparado logo depois do
 * {@code Camera#setup} no mesmo quadro (o {@code ComputeCameraAngles} vem antes do posicionamento).
 */
public final class CinematicCamera {
    private static boolean active;
    private static int sequence = Integer.MIN_VALUE;
    private static CameraType savedType;
    private static FinisherDef finisher;
    private static PlayerAnimationState state;
    private static float letterbox, prevLetterbox;
    // Tremor ("trauma" que decai) e pulso de FOV.
    private static float trauma, traumaDecay;
    private static float fovPulse = 1F, fovPulseTarget = 1F;
    private static int fovPulseTicks, fovPulseLength;
    private static long shakeSeed;

    private CinematicCamera() {}

    public static boolean active() { return active; }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        prevLetterbox = letterbox;
        if (mc.player == null || mc.level == null) {
            if (active) end(mc);
            return;
        }
        PlayerAnimationState current = ClientPlayerAnimations.active(mc.player.getUUID());
        FinisherDef def = current == null ? null : Finishers.byAnimation(current.animation());
        boolean wanted = def != null && current.params().contains(Finishers.PARAM_X) && mc.player.isAlive();
        if (wanted) {
            if (!active || current.sequence() != sequence) begin(mc, def, current);
            // F5 durante a cena nao tira a camera do lugar; a perspectiva salva e devolvida no fim.
            if (mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK) mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        } else if (active) {
            end(mc);
        }
        letterbox = Mth.clamp(letterbox + (active ? 0.15F : -0.2F), 0F, 1F);

        trauma = Math.max(0F, trauma - traumaDecay);
        if (fovPulseTicks > 0) {
            fovPulseTicks--;
            float t = 1F - fovPulseTicks / (float) Math.max(1, fovPulseLength);
            // sobe rapido, volta devagar
            fovPulse = t < 0.2F ? Mth.lerp(t / 0.2F, 1F, fovPulseTarget) : Mth.lerp((t - 0.2F) / 0.8F, fovPulseTarget, 1F);
        } else {
            fovPulse = 1F;
        }
    }

    private static void begin(Minecraft mc, FinisherDef def, PlayerAnimationState current) {
        if (!active) savedType = mc.options.getCameraType();
        active = true;
        finisher = def;
        state = current;
        sequence = current.sequence();
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
    }

    private static void end(Minecraft mc) {
        active = false;
        finisher = null;
        state = null;
        sequence = Integer.MIN_VALUE;
        if (savedType != null) mc.options.setCameraType(savedType);
        savedType = null;
    }

    /** Logout/troca de mundo: restaura sem depender de tick. */
    public static void reset() {
        Minecraft mc = Minecraft.getInstance();
        if (active) end(mc);
        trauma = 0;
        fovPulseTicks = 0;
        fovPulse = 1F;
        letterbox = prevLetterbox = 0;
    }

    /** Eventos temporizados do proprio jogador: tremor e pulso de FOV. */
    public static void onTimedEvent(PlayerAnimationEvent.Timed event) {
        Minecraft mc = Minecraft.getInstance();
        if (!event.getPlayer().level().isClientSide() || mc.player == null || event.getPlayer() != mc.player) return;
        PlayerAnimationDef.TimedEvent timed = event.getEvent();
        float[] args = parse(timed.data());
        if (timed.type().equals(PlayerAnimations.EVENT_CAMERA_SHAKE)) {
            float intensity = args.length > 0 ? args[0] : 0.5F;
            int ticks = args.length > 1 ? Math.max(1, (int) args[1]) : 8;
            trauma = Math.min(1.5F, Math.max(trauma, intensity));
            traumaDecay = trauma / ticks;
            shakeSeed++;
        } else if (timed.type().equals(PlayerAnimations.EVENT_CAMERA_FOV)) {
            fovPulseTarget = args.length > 0 ? Mth.clamp(args[0], 0.5F, 1.5F) : 0.85F;
            fovPulseLength = fovPulseTicks = args.length > 1 ? Math.max(2, (int) args[1]) : 10;
        }
    }

    private static float[] parse(String data) {
        String[] parts = data.split(",");
        float[] out = new float[parts.length];
        try {
            for (int i = 0; i < parts.length; i++) out[i] = Float.parseFloat(parts[i].trim());
        } catch (NumberFormatException e) {
            return new float[0];
        }
        return out;
    }

    /** Posiciona a camera (cena) e aplica FOV/tremor. Uma vez por quadro, no FOV do mundo (nao da mao). */
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (!event.usedConfiguredFov()) return;
        Minecraft mc = Minecraft.getInstance();
        float partial = (float) event.getPartialTick();
        double fovMul = fovPulse;
        Camera camera = event.getCamera();
        if (active && state != null && finisher != null && mc.level != null) {
            double elapsed = mc.level.getGameTime() + partial - state.startGameTime();
            CameraTrack.Sample sample = finisher.camera().sample(elapsed);
            var p = state.params();
            Vec3 anchor = new Vec3(p.getDouble(Finishers.PARAM_X), p.getDouble(Finishers.PARAM_Y), p.getDouble(Finishers.PARAM_Z));
            double[] offset = CameraTrack.offset(sample, p.getFloat(Finishers.PARAM_YAW));
            Vec3 look = anchor.add(0, sample.lookHeight(), 0);
            Vec3 desired = anchor.add(offset[0], offset[1], offset[2]);
            // Nao atravessa paredes: encurta ate o primeiro bloco entre o ponto observado e a camera.
            var hit = mc.level.clip(new ClipContext(look, desired, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE,
                    CollisionContext.empty()));
            if (hit.getType() != HitResult.Type.MISS) {
                desired = hit.getLocation().add(look.subtract(hit.getLocation()).normalize().scale(0.25));
            }
            Vec3 d = look.subtract(desired);
            float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90F;
            float pitch = (float) (-(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG));
            float roll = (float) sample.roll();
            float[] shake = shake(partial);
            camera.setPosition(desired);
            camera.setRotation(yaw + shake[0], pitch + shake[1], roll + shake[2]);
            fovMul *= sample.fov();
        } else if (trauma > 0.001F && camera.getEntity() == mc.player) {
            float[] shake = shake(partial);
            camera.setRotation(camera.getYRot() + shake[0], camera.getXRot() + shake[1], shake[2]);
        }
        if (fovMul != 1.0) event.setFOV(event.getFOV() * fovMul);
    }

    /** Ruido suave (somas de senos) escalado pelo trauma ao quadrado. */
    private static float[] shake(float partial) {
        if (trauma <= 0.001F) return new float[3];
        Minecraft mc = Minecraft.getInstance();
        float t = (mc.level == null ? 0 : mc.level.getGameTime()) + partial + shakeSeed * 13.7F;
        float amount = trauma * trauma;
        return new float[] {
                amount * 4F * (Mth.sin(t * 2.1F) + 0.5F * Mth.sin(t * 5.3F)),
                amount * 3F * (Mth.sin(t * 2.7F + 1.3F) + 0.5F * Mth.sin(t * 6.1F)),
                amount * 2.5F * Mth.sin(t * 3.3F + 2.1F)};
    }

    /** Faixas de cinema (camada de HUD), deslizando para dentro/fora. */
    public static void renderLetterbox(GuiGraphics g, float partial) {
        float amount = Mth.lerp(partial, prevLetterbox, letterbox);
        if (amount <= 0.001F) return;
        int bar = Math.round(g.guiHeight() * 0.11F * amount);
        g.fill(0, 0, g.guiWidth(), bar, 0xFF000000);
        g.fill(0, g.guiHeight() - bar, g.guiWidth(), g.guiHeight(), 0xFF000000);
    }
}
