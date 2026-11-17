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
    private int lastSegment = NO_SEQUENCE;
    // Sincronizacao temporal: transicao usada nesta execucao e avanco pendente do clip (quem entrou no meio).
    private int usedTransition;
    private boolean catchUpPending;
    private PlayerAnimationState syncedState;
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
        if (clip != null && now - lastClipTick >= RECOVERY_TICKS) {
            // O rig volta a ser desenhado depois de ficar parado: os snapshots dos bones guardam a pose de quando
            // ele parou (ex.: quatro apoios de uma sequencia cancelada) e a transicao do clip novo partiria dela,
            // mostrando essa pose por um quadro. Sem snapshots, o GeckoLib recomeca da pose de repouso.
            // (Aqui, antes do processamento do GeckoLib; nunca dentro do handler, que usa o mesmo mapa.)
            cache.getManagerForId(uuid.getLeastSignificantBits()).clearSnapshotCache();
        }
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
        SyncedController main = new SyncedController(this, state -> {
            PlayerAnimationState server = ClientPlayerAnimations.active(uuid);
            ClientPlayerAnimations.Clip clip = server == null ? null : ClientPlayerAnimations.clip(server.animation());
            if (clip != null) {
                if (server.sequence() != lastSequence || server.segment() != lastSegment || clip != currentClip) {
                    // Novo inicio (inclusive repetir a mesma animacao) ou proximo segmento de uma sequencia:
                    // recomeca e alinha ao relogio do servidor, misturando a partir da pose atual (sem passar
                    // pela pose vanilla).
                    lastSequence = server.sequence();
                    lastSegment = server.segment();
                    currentClip = clip;
                    syncedState = server;
                    // Quem comeca a ver no meio pula a transicao e avanca o clip ate o instante certo.
                    usedTransition = serverElapsed(server) > clip.def().transitionTicks() + 2 ? 0 : clip.def().transitionTicks();
                    catchUpPending = true;
                    state.getController().forceAnimationReset();
                }
                state.getController().transitionLength(usedTransition);
                return state.setAndContinue(clip.raw(server));
            }
            lastSequence = NO_SEQUENCE;
            lastSegment = NO_SEQUENCE;
            currentClip = null;
            syncedState = null;
            catchUpPending = false;
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

    /** Ticks (com fracao) desde o inicio decidido pelo servidor, no relogio do mundo deste cliente. */
    private static double serverElapsed(PlayerAnimationState state) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null) return 0;
        double now = mc.level.getGameTime() + mc.getTimer().getGameTimeDeltaPartialTick(false);
        return Math.max(0, now - state.startGameTime());
    }

    /**
     * Quanto o clip deve estar adiantado no instante em que comeca a rodar (fim da transicao): tempo do servidor
     * menos a transicao usada. Em loop, so a fase dentro do ciclo. Consumido uma vez por execucao.
     */
    double consumeCatchUp() {
        if (!catchUpPending || syncedState == null) return 0;
        catchUpPending = false;
        double ahead = serverElapsed(syncedState) - usedTransition;
        if (syncedState.loop()) ahead = ahead % Math.max(1, syncedState.durationTicks());
        else ahead = Math.min(ahead, syncedState.durationTicks() - 1);
        return ahead > 0.5 ? ahead : 0;
    }

    /**
     * Controller que, ao iniciar o clip (fim da transicao), desloca o relogio interno para o instante decidido
     * pelo servidor. Sem isso, quem comecava a ver uma animacao finita no meio a reproduzia do inicio.
     */
    static final class SyncedController extends AnimationController<PlayerRigAnimatable> {
        SyncedController(PlayerRigAnimatable animatable, AnimationStateHandler<PlayerRigAnimatable> handler) {
            super(animatable, "main", 3, handler);
        }

        @Override
        protected double adjustTick(double tick) {
            // O GeckoLib zera o relogio no inicio da transicao (estado TRANSITIONING), no inicio do clip (RUNNING)
            // e a cada volta de um loop (RUNNING); o avanco e consumido so na primeira vez por execucao.
            boolean clipStarting = shouldResetTick && getAnimationState() == State.RUNNING;
            double result = super.adjustTick(tick);
            if (!clipStarting) return result;
            double ahead = animatable.consumeCatchUp();
            if (ahead <= 0) return result;
            double speed = getAnimationSpeed();
            tickOffset -= ahead / Math.max(1e-3, speed);
            return speed * Math.max(tick - tickOffset, 0);
        }
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
