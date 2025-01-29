/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */

package net.dries007.tfc.common.blocks.devices;

import java.util.Optional;

import net.dries007.tfc.common.blockentities.MoldBlockEntity;
import net.dries007.tfc.common.blockentities.TFCBlockEntities;
import net.dries007.tfc.common.blocks.DirectionPropertyBlock;
import net.dries007.tfc.common.blocks.EntityBlockExtension;
import net.dries007.tfc.common.blocks.ExtendedBlock;
import net.dries007.tfc.common.blocks.ExtendedProperties;
import net.dries007.tfc.common.component.mold.IMold;
import net.dries007.tfc.util.Helpers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class MoldBlock extends ExtendedBlock implements EntityBlockExtension, IBellowsConsumer {
    public static final BooleanProperty NORTH = PipeBlock.NORTH;
    public static final BooleanProperty EAST = PipeBlock.EAST;
    public static final BooleanProperty SOUTH = PipeBlock.SOUTH;
    public static final BooleanProperty WEST = PipeBlock.WEST;

    static final VoxelShape SHAPE = box(0, 0, 0, 16, 5, 16);

    private static BlockState updateConnectedSides(LevelAccessor level, BlockPos pos, BlockState state) {
        for (final Direction direction : Direction.Plane.HORIZONTAL) {
            final BlockPos adjacentPos = pos.relative(direction);
            final BlockState adjacentState = level.getBlockState(adjacentPos);
            final boolean adjacentChannel = adjacentState.getBlock() instanceof ChannelBlock;
            state = state.setValue(DirectionPropertyBlock.getProperty(direction), adjacentChannel);
        }

        return state;
    }

    public MoldBlock(ExtendedProperties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(EAST, false).setValue(SOUTH, false)
                .setValue(WEST, false).setValue(NORTH, false));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> pBuilder) {
        pBuilder.add(EAST, SOUTH, WEST, NORTH);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (player instanceof ServerPlayer serverPlayer) {
            Optional<MoldBlockEntity> mold = level.getBlockEntity(pos, TFCBlockEntities.MOLD_TABLE.get());
            if (mold.isPresent()) {
                return mold.get().onRightClick(serverPlayer);
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState adjacentState, LevelAccessor level,
            BlockPos pos, BlockPos adjacentPos) {
        return updateConnectedSides(level, pos, state);
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest,
            FluidState fluid) {
        // On destroy, notify source channel that all flows going through this
        // channel have been broken
        if (!level.isClientSide()) {
            level.getBlockEntity(pos, TFCBlockEntities.MOLD_TABLE.get()).ifPresent(
                    channel -> channel.finishFlow());
        }

        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }

    @Override
    public void onBlockExploded(BlockState state, Level level, BlockPos pos, Explosion explosion) {
        // On destroy, notify source channel that all flows going through this
        // channel have been broken
        if (!level.isClientSide()) {
            level.getBlockEntity(pos, TFCBlockEntities.MOLD_TABLE.get()).ifPresent(
                    channel -> channel.finishFlow());
        }

        super.onBlockExploded(state, level, pos, explosion);
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState pState) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos, TFCBlockEntities.MOLD_TABLE.get()).map(
                mold -> {
                    if (!mold.getOutputStack().isEmpty()) {
                        return 15;
                    }

                    ItemStack moldStack = mold.getMoldStack();
                    IMold moldItem = IMold.get(moldStack);
                    if (moldItem != null) {
                        return 1 + 13 * moldItem.getFluidInTank(0).getAmount() / moldItem.getTankCapacity(0);
                    }

                    return 0;
                }).orElse(0);
    }

    @Override
    public void intakeAir(Level level, BlockPos pos, BlockState state, int amount) {
        level.getBlockEntity(pos, TFCBlockEntities.MOLD_TABLE.get()).ifPresent(mold -> mold.intakeAir(amount));
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (newState.getBlock() != state.getBlock()) {
            level.getBlockEntity(pos, TFCBlockEntities.MOLD_TABLE.get()).ifPresent(
                    mold -> {
                        Helpers.spawnItem(level, pos, mold.getInventory().getStackInSlot(MoldBlockEntity.MOLD_SLOT));
                        Helpers.spawnItem(level, pos, mold.getInventory().getStackInSlot(MoldBlockEntity.OUTPUT_SLOT));
                    });
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
