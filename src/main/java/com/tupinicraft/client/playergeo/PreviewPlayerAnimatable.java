package com.tupinicraft.client.playergeo;

import net.minecraft.client.player.AbstractClientPlayer;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/** Cliente apenas: GeckoLib reproduz idle / wave para uma UNICA pre-visualizacao local. */
public final class PreviewPlayerAnimatable implements GeoAnimatable {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.tupinicraft.player.idle");
    private static final RawAnimation WAVE = RawAnimation.begin().thenPlay("animation.tupinicraft.player.wave");

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private AbstractClientPlayer player;
    private int waveEndsAt = Integer.MIN_VALUE;
    private boolean wasWaving = false;

    public void setPlayer(AbstractClientPlayer player) {
        this.player = player;
    }

    public AbstractClientPlayer player() {
        return player;
    }

    public void wave() {
        if (player == null) return;
        waveEndsAt = player.tickCount + 40; // dois segundos
        wasWaving = false;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "player_preview", 0, state -> {
            boolean waving = player != null && player.tickCount < waveEndsAt;
            if (waving && !wasWaving) state.getController().forceAnimationReset();
            wasWaving = waving;
            return state.setAndContinue(waving ? WAVE : IDLE);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }

    @Override
    public double getTick(Object object) {
        return player == null ? 0 : player.tickCount;
    }
}
