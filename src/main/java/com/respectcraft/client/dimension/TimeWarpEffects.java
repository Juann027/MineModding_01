package com.respectcraft.client.dimension;

import com.respectcraft.dimension.timewarp.TimeWarp;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import org.joml.Vector3f;

/**
 * Ceu do Time Warp (Etapa 4.15.2, so cliente): ceu normal com ciclo de dia, nuvens baixas na altura das versoes antigas
 * (y 108) e a luz do ceu levemente dessaturada no lightmap (a luz de tocha fica igual). A neblina densa e a cor dela
 * ficam em {@link TimeWarpClient} (eventos de neblina), que valem em qualquer configuracao grafica. Nada aqui afeta
 * outra dimensao: o vanilla so usa estes efeitos quando o {@code effects} do tipo de dimensao e este id.
 */
public final class TimeWarpEffects extends DimensionSpecialEffects {
    public static final float CLOUD_HEIGHT = 108.0F;

    public TimeWarpEffects() {
        super(CLOUD_HEIGHT, true, SkyType.NORMAL, false, false);
    }

    public static void register(RegisterDimensionSpecialEffectsEvent event) {
        event.register(TimeWarp.ID, new TimeWarpEffects());
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 fogColor, float brightness) {
        return fogColor.multiply(brightness * 0.94F + 0.06F, brightness * 0.94F + 0.06F, brightness * 0.91F + 0.09F);
    }

    @Override
    public boolean isFoggyAt(int x, int y) {
        return false;
    }

    /** Sem o leque de cor do nascer/por do sol: ele e desenhado sem neblina e revelaria o horizonte. */
    @Override
    public float[] getSunriseColor(float timeOfDay, float partialTicks) {
        return null;
    }

    @Override
    public void adjustLightmapColors(ClientLevel level, float partialTicks, float skyDarken, float blockLightRedFlicker,
                                     float skyLight, int pixelX, int pixelY, Vector3f colors) {
        // So a parte da luz do ceu perde um pouco de cor (o "dia palido" das versoes antigas).
        float gray = (colors.x + colors.y + colors.z) / 3F;
        float k = 0.14F * Math.min(1F, skyLight);
        colors.set(colors.x + (gray - colors.x) * k, colors.y + (gray - colors.y) * k, colors.z + (gray - colors.z) * k);
    }
}
