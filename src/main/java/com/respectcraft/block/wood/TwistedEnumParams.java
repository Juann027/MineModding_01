package com.respectcraft.block.wood;

import java.util.function.Supplier;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.neoforged.fml.common.asm.enumextension.EnumProxy;

/**
 * Parametros da entrada {@code Boat.Type.RESPECTCRAFT_TWISTED} (META-INF/enumextensions.json). Classe separada e sem
 * inicializar outras classes do mod, porque o carregador a le muito cedo; os Suppliers so sao resolvidos depois do
 * registro. Construtor NeoForge 1.21.1: {@code Type(Supplier<Block> planks, String name, Supplier<Item> boatItem,
 * Supplier<Item> chestBoatItem, Supplier<Item> stickItem, boolean raft)}. O nome {@code respectcraft:twisted} escolhe
 * as texturas {@code textures/entity/boat|chest_boat/twisted.png} e as camadas de modelo do BoatRenderer vanilla.
 */
public final class TwistedEnumParams {
    @SuppressWarnings("unchecked")
    public static final EnumProxy<Boat.Type> TWISTED_BOAT = new EnumProxy<>(
            Boat.Type.class,
            (Supplier<Block>) () -> TwistedWoodBlocks.TWISTED_PLANKS.get(),
            "respectcraft:twisted",
            (Supplier<Item>) () -> TwistedWoodBlocks.TWISTED_BOAT.get(),
            (Supplier<Item>) () -> TwistedWoodBlocks.TWISTED_CHEST_BOAT.get(),
            (Supplier<Item>) () -> Items.STICK,
            false);

    private TwistedEnumParams() {}
}
