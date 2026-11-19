package com.respectcraft.client.bodylayer;

import com.respectcraft.character.CharacterAppearance;
import com.respectcraft.client.playergeo.PlayerPart;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Uma peca corporal integrada ao personagem (busto, cabelo, acessorio, camada de roupa). Nao ocupa slot de
 * armadura e nao e uma entidade: e geometria extra presa a uma parte do corpo, desenhada pela mesma camada
 * tanto no jogador vanilla quanto no rig GeckoLib.
 *
 * @param id       identificador estavel
 * @param geometry de onde vem a malha e a que parte(s) do corpo ela se prende
 * @param visible  regra de visibilidade (aparencia sincronizada pelo servidor, estado do jogador...)
 * @param texture  textura deste componente (a skin do jogador ou uma textura propria)
 * @param tint     cor ARGB multiplicada pela textura (-1 = sem tingir)
 */
public record BodyLayerDefinition(ResourceLocation id, Geometry geometry, Predicate<Context> visible,
                                  Function<Context, ResourceLocation> texture, ToIntFunction<Context> tint) {

    /** O que as regras de uma peca podem consultar. */
    public record Context(AbstractClientPlayer player, CharacterAppearance appearance) {}

    public sealed interface Geometry permits GeoGeometry, PartGeometry {}

    /**
     * Modelo exportado do Blockbench (GeckoLib {@code .geo.json}), modelado sobre o rig do jogador, nas mesmas
     * coordenadas. Cada bone listado e desenhado (com seus filhos) preso a parte do corpo indicada.
     */
    public record GeoGeometry(ResourceLocation model, Map<String, PlayerPart> bones) implements Geometry {
        public GeoGeometry {
            bones = Map.copyOf(bones);
        }
    }

    /** Malha montada em codigo, nas coordenadas locais do ModelPart vanilla da parte indicada. */
    public record PartGeometry(PlayerPart attach, ModelPart mesh) implements Geometry {}
}
