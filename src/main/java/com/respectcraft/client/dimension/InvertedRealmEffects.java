package com.respectcraft.client.dimension;

import com.respectcraft.dimension.InvertedRealm;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import org.joml.Vector3f;

/**
 * Ceu do Inverted Realm (Etapa 4.9, so cliente). A cor do ceu vem do bioma ({@code sky_color}) e a hora fixa da
 * dimensao (12400, crepusculo eterno) deixa o ceu roxo com estrelas fracas. Aqui: sem nuvens vanilla, neblina que
 * mantem o violeta saturado, brilho do horizonte magenta (no lugar do laranja do por do sol) e luz do ceu levemente
 * violeta no lightmap (luz de tocha continua normal). Sem framebuffer nem passada propria.
 */
public final class InvertedRealmEffects extends DimensionSpecialEffects {
    private final float[] horizonGlow = new float[4];

    public InvertedRealmEffects() {
        super(Float.NaN, true, SkyType.NORMAL, false, false);
    }

    public static void register(RegisterDimensionSpecialEffectsEvent event) {
        event.register(InvertedRealm.ID, new InvertedRealmEffects());
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 fogColor, float brightness) {
        float b = brightness * 0.8F + 0.2F;
        return fogColor.multiply(b, b * 0.9F, b);
    }

    @Override
    public boolean isFoggyAt(int x, int y) {
        return false;
    }

    @Override
    public float[] getSunriseColor(float timeOfDay, float partialTicks) {
        float[] vanilla = super.getSunriseColor(timeOfDay, partialTicks);
        if (vanilla == null) return null;
        horizonGlow[0] = 0.98F;
        horizonGlow[1] = 0.28F + vanilla[1] * 0.15F;
        horizonGlow[2] = 0.78F;
        horizonGlow[3] = vanilla[3] * 0.9F;
        return horizonGlow;
    }

    @Override
    public void adjustLightmapColors(ClientLevel level, float partialTicks, float skyDarken, float blockLightRedFlicker,
                                     float skyLight, int pixelX, int pixelY, Vector3f colors) {
        // So a parte da luz do ceu ganha o tom violeta; a luz de bloco (tochas) nao muda.
        float tint = skyLight * 0.12F;
        colors.set(Math.min(1.0F, colors.x + tint * 0.6F), Math.max(0.0F, colors.y - tint * 0.5F),
                Math.min(1.0F, colors.z + tint));
    }
}
