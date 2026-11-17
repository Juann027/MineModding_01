package com.tupinicraft.client.playergeo;

import com.tupinicraft.animation.PlayerAnimationState;
import com.tupinicraft.animation.PlayerAnimations;
import com.tupinicraft.client.animation.ClientPlayerAnimations;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;
import software.bernie.geckolib.util.RenderUtil;

/**
 * Estado de animacao GeckoLib de UM jogador neste cliente (um por UUID: nenhum controller e compartilhado).
 * Guarda tambem a pose vanilla lida neste quadro e os valores puros do clip, usados na combinacao.
 */
public final class PlayerRigAnimatable implements GeoAnimatable {
    /** Ticks que o rig continua desenhado depois do fim do clip, enquanto os bones voltam ao repouso. */
    static final double RECOVERY_TICKS = 4;
    private static final int NO_SEQUENCE = Integer.MIN_VALUE;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final UUID uuid;
    private AbstractClientPlayer player;

    // Pose vanilla deste quadro (repouso -> atual, espaco geo), uma matriz por PlayerPart.
    final Matrix4f[] vanillaPose = new Matrix4f[PlayerPart.values().length];
    boolean hasVanillaPose;
    // Valores do clip por bone neste quadro: rotX, rotY, rotZ, posX, posY, posZ.
    final Map<String, float[]> clipValues = new HashMap<>();
    // Quanto cada parte segue so o clip (1) ou a pose vanilla somada ao clip (0); muda aos poucos.
    final float[] overrideWeight = new float[PlayerPart.values().length];

    private ClientPlayerAnimations.Clip currentClip;
    private int lastSequence = NO_SEQUENCE;
    private double lastFrameTick = Double.NaN;
    private double lastClipTick = Double.NEGATIVE_INFINITY;

    public PlayerRigAnimatable(UUID uuid) {
        this.uuid = uuid;
        for (int i = 0; i < vanillaPose.length; i++) vanillaPose[i] = new Matrix4f();
    }

    public UUID uuid() { return uuid; }
    public AbstractClientPlayer player() { return player; }
    public void setPlayer(AbstractClientPlayer player) { this.player = player; }

    /** Clip que o servidor mandou tocar agora, ja validado; null se nao ha (ou se o recurso e invalido). */
    private static ClientPlayerAnimations.Clip serverClip(UUID uuid) {
        PlayerAnimationState state = ClientPlayerAnimations.active(uuid);
        return state == null ? null : ClientPlayerAnimations.clip(state.animation());
    }

    /** Ha um clip do servidor em andamento, ou ele acabou ha pouco e a pose ainda esta voltando ao normal. */
    public boolean wantsRig() {
        if (serverClip(uuid) != null) return true;
        return RenderUtil.getCurrentTick() - lastClipTick < RECOVERY_TICKS;
    }

    /** Uma vez por quadro, antes de desenhar: le a pose vanilla e atualiza os pesos de mistura. */
    public void beginFrame(HumanoidModel<?> model) {
        for (PlayerPart part : PlayerPart.values()) {
            PlayerPoseBridge.readVanilla(part.vanilla(model), part.rest(), vanillaPose[part.ordinal()]);
        }
        hasVanillaPose = true;

        double now = RenderUtil.getCurrentTick();
        float elapsed = Double.isNaN(lastFrameTick) ? 0F : (float) Mth.clamp(now - lastFrameTick, 0, 20);
        lastFrameTick = now;
        ClientPlayerAnimations.Clip clip = serverClip(uuid);
        if (clip != null) lastClipTick = now;
        float step = elapsed / Math.max(1, clip != null ? clip.def().transitionTicks() : 3);
        for (PlayerPart part : PlayerPart.values()) {
            float target = clip != null && clip.overridden().contains(part) ? 1F : 0F;
            int i = part.ordinal();
            overrideWeight[i] = Mth.clamp(target, overrideWeight[i] - step, overrideWeight[i] + step);
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        AnimationController<PlayerRigAnimatable> main = new AnimationController<>(this, "main", 3, state -> {
            PlayerAnimationState server = ClientPlayerAnimations.active(uuid);
            ClientPlayerAnimations.Clip clip = server == null ? null : ClientPlayerAnimations.clip(server.animation());
            if (clip != null) {
                if (server.sequence() != lastSequence || clip != currentClip) {
                    // Novo inicio (inclusive repetir a mesma animacao): recomeca do zero.
                    lastSequence = server.sequence();
                    currentClip = clip;
                    state.getController().forceAnimationReset();
                }
                state.getController().transitionLength(clip.def().transitionTicks());
                return state.setAndContinue(clip.raw(server.loop()));
            }
            lastSequence = NO_SEQUENCE;
            currentClip = null;
            // Modo de teste (F8): sem animacao do servidor, o rig respira com o idle.
            ClientPlayerAnimations.Clip idle = PlayerRigController.debugRig() ? PlayerRigController.idleClipOrNull() : null;
            if (idle == null) return PlayState.STOP;
            state.getController().transitionLength(idle.def().transitionTicks());
            return state.setAndContinue(idle.raw(true));
        });
        // Keyframes de som/particula/instrucao do proprio clip viram eventos do Animation Manager.
        main.setSoundKeyframeHandler(event -> forward(PlayerAnimations.EVENT_CLIP_SOUND,
                event.getKeyframeData().getSound(), event.getAnimationTick()));
        main.setParticleKeyframeHandler(event -> forward(PlayerAnimations.EVENT_CLIP_PARTICLE,
                event.getKeyframeData().getEffect() + "|" + event.getKeyframeData().getLocator() + "|"
                        + event.getKeyframeData().script(), event.getAnimationTick()));
        main.setCustomInstructionKeyframeHandler(event -> forward(PlayerAnimations.EVENT_CLIP_INSTRUCTION,
                event.getKeyframeData().getInstructions(), event.getAnimationTick()));
        controllers.add(main);
    }

    private void forward(net.minecraft.resources.ResourceLocation type, String data, double tick) {
        if (player != null) ClientPlayerAnimations.clipKeyframe(player, type, data, tick);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    @Override
    public double getTick(Object object) { return RenderUtil.getCurrentTick(); }

    @Override
    public double getBoneResetTime() { return RECOVERY_TICKS - 1; }
}
