package dev.rampdoor;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.redstone.Orientation;

public final class RampControllerBlock extends BaseEntityBlock {
    public static final MapCodec<RampControllerBlock> CODEC = simpleCodec(RampControllerBlock::new);
    public RampControllerBlock(Properties properties) { super(properties); registerDefaultState(stateDefinition.any().setValue(BlockStateProperties.POWERED, false)); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(BlockStateProperties.POWERED); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new RampControllerBlockEntity(pos, state); }
    @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, Orientation orientation, boolean moved) {
        if (!(level instanceof ServerLevel sl)) return;
        boolean powered = level.hasNeighborSignal(pos);
        boolean previous = state.getValue(BlockStateProperties.POWERED);
        if (powered == previous) return;
        level.setBlock(pos, state.setValue(BlockStateProperties.POWERED, powered), Block.UPDATE_CLIENTS);
        RampManager manager = RampDoorMod.manager(sl.getServer());
        if (powered && manager != null) {
            for (RampDefinition r : manager.storage.data.ramps.values()) {
                if (r.redstoneEnabled && GridPos.of(pos).equals(r.controller) && r.dimension.equals(sl.dimension().identifier().toString())) {
                    try { manager.toggle(r); } catch (RuntimeException e) { RampDoorMod.LOGGER.warn("Controller: {}", e.getMessage()); }
                    break;
                }
            }
        }
    }
    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (!old.is(this) && level instanceof ServerLevel)
            level.setBlock(pos, state.setValue(BlockStateProperties.POWERED, level.hasNeighborSignal(pos)), Block.UPDATE_CLIENTS);
    }
    @Override protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean moved) {
        super.affectNeighborsAfterRemoval(state, level, pos, moved);
        if (level.getBlockState(pos).is(this)) return;
        var manager = RampDoorMod.manager(level.getServer());
        if (manager == null) return;
        boolean changed = false;
        for (var r : manager.storage.data.ramps.values()) if (GridPos.of(pos).equals(r.controller) && r.dimension.equals(level.dimension().identifier().toString())) {
            r.controller = null; changed = true;
        }
        if (changed) manager.save();
    }
}
