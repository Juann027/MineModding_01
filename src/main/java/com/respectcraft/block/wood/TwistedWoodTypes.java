package com.respectcraft.block.wood;

import com.respectcraft.RespectCraft;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.WoodType;

/**
 * Tipo de madeira e conjunto de blocos da Madeira Retorcida ({@code respectcraft:twisted}).
 * BlockSetType define sons e comportamento de porta, alcapao, botao e placa de pressao; WoodType e usado pelo
 * portao e pelas placas (texturas {@code entity/signs/twisted.png} e {@code entity/signs/hanging/twisted.png}).
 */
public final class TwistedWoodTypes {
    public static final BlockSetType BLOCK_SET = BlockSetType.register(new BlockSetType(RespectCraft.MOD_ID + ":twisted"));
    public static final WoodType WOOD = WoodType.register(new WoodType(RespectCraft.MOD_ID + ":twisted", BLOCK_SET));

    private TwistedWoodTypes() {}
}
