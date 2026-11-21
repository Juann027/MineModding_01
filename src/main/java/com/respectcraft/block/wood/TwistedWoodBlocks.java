package com.respectcraft.block.wood;

import com.respectcraft.registry.ModBlocks;
import com.respectcraft.registry.ModItems;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.HangingSignItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SignItem;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Madeira Retorcida (Etapa 4.9, vinda do pacote "O Avesso — Fase 2, Etapa A"): 20 blocos, 21 itens e 2 block
 * entities, registrados em {@link ModBlocks} e {@link ModItems}. {@code twisted_log} e provisorio: o tronco da Fase 1
 * nao chegou, entao usa a casca de {@code twisted_wood} com um topo derivado ({@code twisted_log_top}); o id ja e o
 * definitivo, a arte da Fase 1 pode substituir so os arquivos de recurso.
 */
public final class TwistedWoodBlocks {
    private static final MapColor BARK = MapColor.TERRACOTTA_PURPLE;
    private static final MapColor STRIPPED = MapColor.COLOR_PURPLE;
    private static final MapColor CORE = MapColor.CRIMSON_STEM; // familia das tabuas: carmesim-roxo

    // ---------------------------------------------------------------- troncos e madeira
    public static final DeferredBlock<RotatedPillarBlock> TWISTED_LOG = ModBlocks.BLOCKS.register("twisted_log",
            () -> new RotatedPillarBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_LOG).mapColor(BARK)));
    public static final DeferredBlock<RotatedPillarBlock> TWISTED_WOOD = ModBlocks.BLOCKS.register("twisted_wood",
            () -> new RotatedPillarBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_WOOD).mapColor(BARK)));
    public static final DeferredBlock<RotatedPillarBlock> STRIPPED_TWISTED_LOG = ModBlocks.BLOCKS.register("stripped_twisted_log",
            () -> new RotatedPillarBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.STRIPPED_OAK_LOG).mapColor(STRIPPED)));
    public static final DeferredBlock<RotatedPillarBlock> STRIPPED_TWISTED_WOOD = ModBlocks.BLOCKS.register("stripped_twisted_wood",
            () -> new RotatedPillarBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.STRIPPED_OAK_WOOD).mapColor(STRIPPED)));

    // ---------------------------------------------------------------- tabuas e conjunto de construcao
    public static final DeferredBlock<Block> TWISTED_PLANKS = ModBlocks.BLOCKS.register("twisted_planks",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS).mapColor(CORE)));
    public static final DeferredBlock<StairBlock> TWISTED_STAIRS = ModBlocks.BLOCKS.register("twisted_stairs",
            () -> new StairBlock(TWISTED_PLANKS.get().defaultBlockState(),
                    BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_STAIRS).mapColor(CORE)));
    public static final DeferredBlock<SlabBlock> TWISTED_SLAB = ModBlocks.BLOCKS.register("twisted_slab",
            () -> new SlabBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_SLAB).mapColor(CORE)));
    public static final DeferredBlock<FenceBlock> TWISTED_FENCE = ModBlocks.BLOCKS.register("twisted_fence",
            () -> new FenceBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_FENCE).mapColor(CORE)));
    public static final DeferredBlock<FenceGateBlock> TWISTED_FENCE_GATE = ModBlocks.BLOCKS.register("twisted_fence_gate",
            () -> new FenceGateBlock(TwistedWoodTypes.WOOD, BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_FENCE_GATE).mapColor(CORE)));
    public static final DeferredBlock<DoorBlock> TWISTED_DOOR = ModBlocks.BLOCKS.register("twisted_door",
            () -> new DoorBlock(TwistedWoodTypes.BLOCK_SET, BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_DOOR).mapColor(CORE)));
    public static final DeferredBlock<TrapDoorBlock> TWISTED_TRAPDOOR = ModBlocks.BLOCKS.register("twisted_trapdoor",
            () -> new TrapDoorBlock(TwistedWoodTypes.BLOCK_SET, BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_TRAPDOOR).mapColor(CORE)));
    public static final DeferredBlock<ButtonBlock> TWISTED_BUTTON = ModBlocks.BLOCKS.register("twisted_button",
            () -> new ButtonBlock(TwistedWoodTypes.BLOCK_SET, 30, BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_BUTTON)));
    public static final DeferredBlock<PressurePlateBlock> TWISTED_PRESSURE_PLATE = ModBlocks.BLOCKS.register("twisted_pressure_plate",
            () -> new PressurePlateBlock(TwistedWoodTypes.BLOCK_SET,
                    BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PRESSURE_PLATE).mapColor(CORE)));
    public static final DeferredBlock<LadderBlock> TWISTED_LADDER = ModBlocks.BLOCKS.register("twisted_ladder",
            () -> new LadderBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.LADDER).mapColor(CORE)));

    // ---------------------------------------------------------------- placas
    public static final DeferredBlock<TwistedFurniture.StandingSign> TWISTED_SIGN = ModBlocks.BLOCKS.register("twisted_sign",
            () -> new TwistedFurniture.StandingSign(TwistedWoodTypes.WOOD,
                    BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_SIGN).mapColor(CORE)));
    public static final DeferredBlock<TwistedFurniture.WallSign> TWISTED_WALL_SIGN = ModBlocks.BLOCKS.register("twisted_wall_sign",
            () -> new TwistedFurniture.WallSign(TwistedWoodTypes.WOOD,
                    BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_WALL_SIGN).mapColor(CORE).lootFrom(TWISTED_SIGN)));
    public static final DeferredBlock<TwistedFurniture.CeilingHangingSign> TWISTED_HANGING_SIGN = ModBlocks.BLOCKS.register(
            "twisted_hanging_sign", () -> new TwistedFurniture.CeilingHangingSign(TwistedWoodTypes.WOOD,
                    BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_HANGING_SIGN).mapColor(CORE)));
    public static final DeferredBlock<TwistedFurniture.WallHangingSign> TWISTED_WALL_HANGING_SIGN = ModBlocks.BLOCKS.register(
            "twisted_wall_hanging_sign", () -> new TwistedFurniture.WallHangingSign(TwistedWoodTypes.WOOD,
                    BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_WALL_HANGING_SIGN).mapColor(CORE).lootFrom(TWISTED_HANGING_SIGN)));

    // ---------------------------------------------------------------- moveis
    public static final DeferredBlock<Block> TWISTED_BOOKSHELF = ModBlocks.BLOCKS.register("twisted_bookshelf",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.BOOKSHELF).mapColor(CORE)));
    public static final DeferredBlock<TwistedFurniture.CraftingTable> TWISTED_CRAFTING_TABLE = ModBlocks.BLOCKS.register(
            "twisted_crafting_table",
            () -> new TwistedFurniture.CraftingTable(BlockBehaviour.Properties.ofFullCopy(Blocks.CRAFTING_TABLE).mapColor(CORE)));

    // ---------------------------------------------------------------- block entities
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TwistedFurniture.SignBE>> SIGN_BE =
            ModBlocks.BLOCK_ENTITY_TYPES.register("twisted_sign", () -> BlockEntityType.Builder.of(TwistedFurniture.SignBE::new,
                    TWISTED_SIGN.get(), TWISTED_WALL_SIGN.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TwistedFurniture.HangingSignBE>> HANGING_SIGN_BE =
            ModBlocks.BLOCK_ENTITY_TYPES.register("twisted_hanging_sign", () -> BlockEntityType.Builder.of(
                    TwistedFurniture.HangingSignBE::new, TWISTED_HANGING_SIGN.get(), TWISTED_WALL_HANGING_SIGN.get()).build(null));

    // ---------------------------------------------------------------- itens
    public static final DeferredItem<BlockItem> TWISTED_LOG_ITEM = ModItems.ITEMS.registerSimpleBlockItem(TWISTED_LOG);
    public static final DeferredItem<BlockItem> TWISTED_WOOD_ITEM = ModItems.ITEMS.registerSimpleBlockItem(TWISTED_WOOD);
    public static final DeferredItem<BlockItem> STRIPPED_TWISTED_LOG_ITEM = ModItems.ITEMS.registerSimpleBlockItem(STRIPPED_TWISTED_LOG);
    public static final DeferredItem<BlockItem> STRIPPED_TWISTED_WOOD_ITEM = ModItems.ITEMS.registerSimpleBlockItem(STRIPPED_TWISTED_WOOD);
    public static final DeferredItem<BlockItem> TWISTED_PLANKS_ITEM = ModItems.ITEMS.registerSimpleBlockItem(TWISTED_PLANKS);
    public static final DeferredItem<BlockItem> TWISTED_STAIRS_ITEM = ModItems.ITEMS.registerSimpleBlockItem(TWISTED_STAIRS);
    public static final DeferredItem<BlockItem> TWISTED_SLAB_ITEM = ModItems.ITEMS.registerSimpleBlockItem(TWISTED_SLAB);
    public static final DeferredItem<BlockItem> TWISTED_FENCE_ITEM = ModItems.ITEMS.registerSimpleBlockItem(TWISTED_FENCE);
    public static final DeferredItem<BlockItem> TWISTED_FENCE_GATE_ITEM = ModItems.ITEMS.registerSimpleBlockItem(TWISTED_FENCE_GATE);
    public static final DeferredItem<DoubleHighBlockItem> TWISTED_DOOR_ITEM = ModItems.ITEMS.register("twisted_door",
            () -> new DoubleHighBlockItem(TWISTED_DOOR.get(), new Item.Properties()));
    public static final DeferredItem<BlockItem> TWISTED_TRAPDOOR_ITEM = ModItems.ITEMS.registerSimpleBlockItem(TWISTED_TRAPDOOR);
    public static final DeferredItem<BlockItem> TWISTED_BUTTON_ITEM = ModItems.ITEMS.registerSimpleBlockItem(TWISTED_BUTTON);
    public static final DeferredItem<BlockItem> TWISTED_PRESSURE_PLATE_ITEM = ModItems.ITEMS.registerSimpleBlockItem(TWISTED_PRESSURE_PLATE);
    public static final DeferredItem<BlockItem> TWISTED_LADDER_ITEM = ModItems.ITEMS.registerSimpleBlockItem(TWISTED_LADDER);
    public static final DeferredItem<BlockItem> TWISTED_BOOKSHELF_ITEM = ModItems.ITEMS.registerSimpleBlockItem(TWISTED_BOOKSHELF);
    public static final DeferredItem<BlockItem> TWISTED_CRAFTING_TABLE_ITEM = ModItems.ITEMS.registerSimpleBlockItem(TWISTED_CRAFTING_TABLE);
    public static final DeferredItem<SignItem> TWISTED_SIGN_ITEM = ModItems.ITEMS.register("twisted_sign",
            () -> new SignItem(new Item.Properties().stacksTo(16), TWISTED_SIGN.get(), TWISTED_WALL_SIGN.get()));
    public static final DeferredItem<HangingSignItem> TWISTED_HANGING_SIGN_ITEM = ModItems.ITEMS.register("twisted_hanging_sign",
            () -> new HangingSignItem(TWISTED_HANGING_SIGN.get(), TWISTED_WALL_HANGING_SIGN.get(), new Item.Properties().stacksTo(16)));
    public static final DeferredItem<BoatItem> TWISTED_BOAT = ModItems.ITEMS.register("twisted_boat",
            () -> new BoatItem(false, TwistedEnumParams.TWISTED_BOAT.getValue(), new Item.Properties().stacksTo(1)));
    public static final DeferredItem<BoatItem> TWISTED_CHEST_BOAT = ModItems.ITEMS.register("twisted_chest_boat",
            () -> new BoatItem(true, TwistedEnumParams.TWISTED_BOAT.getValue(), new Item.Properties().stacksTo(1)));

    /** Ordem da aba criativa (aba do RespectCraft e "Building Blocks"). */
    public static final List<Supplier<? extends ItemLike>> CREATIVE_ORDER = List.of(
            TWISTED_LOG_ITEM, TWISTED_WOOD_ITEM, STRIPPED_TWISTED_LOG_ITEM, STRIPPED_TWISTED_WOOD_ITEM, TWISTED_PLANKS_ITEM,
            TWISTED_STAIRS_ITEM, TWISTED_SLAB_ITEM, TWISTED_FENCE_ITEM, TWISTED_FENCE_GATE_ITEM,
            TWISTED_DOOR_ITEM, TWISTED_TRAPDOOR_ITEM, TWISTED_PRESSURE_PLATE_ITEM, TWISTED_BUTTON_ITEM,
            TWISTED_LADDER_ITEM, TWISTED_SIGN_ITEM, TWISTED_HANGING_SIGN_ITEM, TWISTED_BOOKSHELF_ITEM,
            TWISTED_CRAFTING_TABLE_ITEM, TWISTED_BOAT, TWISTED_CHEST_BOAT);

    /** Carrega a classe (e com ela as entradas dos DeferredRegisters) antes dos eventos de registro. */
    public static void bootstrap() {}

    private TwistedWoodBlocks() {}
}
