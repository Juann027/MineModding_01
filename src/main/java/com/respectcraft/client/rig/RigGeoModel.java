package com.respectcraft.client.rig;

import com.respectcraft.network.RigVisualClientCache;
import com.respectcraft.rig.CustomRigRegistry;
import com.respectcraft.rig.RigBinding;
import com.respectcraft.rig.RigDefinition;
import com.respectcraft.rig.RigVisuals;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.model.GeoModel;

/**
 * So cliente (Etapa 4.12). Modelo GeckoLib generico de uma entidade associada a uma familia de rig: geo, textura e
 * animacoes vem do manifesto ({@link RigBinding}); as animacoes compartilhadas da familia entram como
 * {@code getAnimationResourceFallbacks} (o GeckoLib procura nelas o que nao achar no arquivo proprio). Antes de cada
 * desenho aplica a visibilidade das partes (bones assados sao compartilhados entre entidades do mesmo modelo, por isso
 * todo quadro, pelo renderer) e liga o rastreio de matriz dos bones {@code socket_*} (posicao animada, so visual).
 */
public class RigGeoModel<T extends Entity & GeoAnimatable> extends GeoModel<T> {
    private final RigBinding binding;
    private final RigDefinition rig;
    private final List<RigDefinition.Part> parts;
    private final ResourceLocation animation;
    private final ResourceLocation[] fallbacks;

    public RigGeoModel(ResourceLocation entityType) {
        this.binding = CustomRigRegistry.GLOBAL.binding(entityType)
                .orElseThrow(() -> new IllegalStateException("No rig binding for " + entityType));
        this.rig = CustomRigRegistry.GLOBAL.rig(binding.rig()).orElseThrow();
        this.parts = CustomRigRegistry.GLOBAL.parts(binding);
        ResourceLocation shared = rig.animationFile();
        this.animation = binding.animationFile() != null ? binding.animationFile() : shared;
        this.fallbacks = animation.equals(shared) ? new ResourceLocation[0] : new ResourceLocation[] {shared};
    }

    public RigBinding binding() { return binding; }

    @Override public ResourceLocation getModelResource(T animatable) { return binding.geo(); }

    @Override public ResourceLocation getTextureResource(T animatable) { return binding.texture(); }

    @Override public ResourceLocation getAnimationResource(T animatable) { return animation; }

    @Override public ResourceLocation[] getAnimationResourceFallbacks(T animatable) { return fallbacks; }

    /**
     * Aplica partes e rastreio de sockets nos bones assados (compartilhados por todas as entidades deste modelo).
     * Chamado pelo {@link RigGeoRenderer#preRender} a CADA desenho: o {@code handleAnimations} do GeckoLib pode pular
     * quadros e deixaria o estado de outra entidade nos bones.
     */
    void applyVisualState(T animatable) {
        Map<String, Boolean> overrides = RigVisualClientCache.get(animatable.getId());
        for (RigDefinition.Part part : parts) {
            boolean visible = RigVisuals.visible(part, overrides);
            for (String bone : part.bones()) getBone(bone).ifPresent(b -> b.setHidden(!visible));
        }
        for (RigDefinition.Socket socket : rig.sockets()) getBone(socket.boneName()).ifPresent(b -> b.setTrackingMatrices(true));
        for (RigDefinition.Socket socket : binding.sockets()) getBone(socket.boneName()).ifPresent(b -> b.setTrackingMatrices(true));
    }
}
