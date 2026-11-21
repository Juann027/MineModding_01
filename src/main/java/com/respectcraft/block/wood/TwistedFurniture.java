package com.respectcraft.block.wood;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CeilingHangingSignBlock;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.WallHangingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.HangingSignBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WoodType;

/**
 * Placas, placas penduradas e bancada da Madeira Retorcida. Os block entities vanilla de placa so aceitam os blocos
 * vanilla, entao cada placa usa um tipo proprio. A placa pendurada estende {@link HangingSignBlockEntity} (o cliente
 * escolhe a tela de edicao pela classe) e troca so o {@code getType()}, que o NeoForge 1.21.1 usa na validacao.
 */
public final class TwistedFurniture {
    public static class SignBE extends SignBlockEntity {
        public SignBE(BlockPos pos, BlockState state) {
            super(TwistedWoodBlocks.SIGN_BE.get(), pos, state);
        }
    }

    public static class HangingSignBE extends HangingSignBlockEntity {
        public HangingSignBE(BlockPos pos, BlockState state) {
            super(pos, state);
        }

        @Override
        public BlockEntityType<?> getType() {
            return TwistedWoodBlocks.HANGING_SIGN_BE.get();
        }
    }

    public static class StandingSign extends StandingSignBlock {
        public StandingSign(WoodType type, Properties props) {
            super(type, props);
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new SignBE(pos, state);
        }

        @Override
        public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
            return createTickerHelper(type, TwistedWoodBlocks.SIGN_BE.get(), SignBlockEntity::tick);
        }
    }

    public static class WallSign extends WallSignBlock {
        public WallSign(WoodType type, Properties props) {
            super(type, props);
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new SignBE(pos, state);
        }

        @Override
        public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
            return createTickerHelper(type, TwistedWoodBlocks.SIGN_BE.get(), SignBlockEntity::tick);
        }
    }

    public static class CeilingHangingSign extends CeilingHangingSignBlock {
        public CeilingHangingSign(WoodType type, Properties props) {
            super(type, props);
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new HangingSignBE(pos, state);
        }

        @Override
        public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
            return createTickerHelper(type, TwistedWoodBlocks.HANGING_SIGN_BE.get(), SignBlockEntity::tick);
        }
    }

    public static class WallHangingSign extends WallHangingSignBlock {
        public WallHangingSign(WoodType type, Properties props) {
            super(type, props);
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new HangingSignBE(pos, state);
        }

        @Override
        public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
            return createTickerHelper(type, TwistedWoodBlocks.HANGING_SIGN_BE.get(), SignBlockEntity::tick);
        }
    }

    /** O CraftingMenu vanilla fecha se o bloco nao for a bancada vanilla; aqui a validacao aceita a nossa. */
    public static class CraftingTable extends CraftingTableBlock {
        private static final Component TITLE = Component.translatable("container.crafting");

        public CraftingTable(Properties props) {
            super(props);
        }

        @Override
        protected MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
            ContainerLevelAccess access = ContainerLevelAccess.create(level, pos);
            return new SimpleMenuProvider((id, inventory, player) -> new CraftingMenu(id, inventory, access) {
                @Override
                public boolean stillValid(Player p) {
                    return AbstractContainerMenu.stillValid(access, p, TwistedWoodBlocks.TWISTED_CRAFTING_TABLE.get());
                }
            }, TITLE);
        }
    }

    private TwistedFurniture() {}
}
