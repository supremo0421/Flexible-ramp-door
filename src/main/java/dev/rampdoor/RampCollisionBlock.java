package dev.rampdoor;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.shapes.*;

/**
 * Invisible walking surface for an open ramp. Not an item, never in the creative inventory, never
 * dropped and not placeable by hand: RampDoor is the only thing that writes it.
 *
 * <p>The state describes the sloped line of the walking surface through this block: {@code height}
 * is the surface height at the uphill edge in sixteenths, {@code rise} is how far the surface drops
 * across the block toward downhill, and {@code facing} points uphill. Heights above 16 mean the line
 * leaves through the top of the block (the block above carries that part) and sub-steps whose height
 * falls below the block are simply absent, so a surface crossing a block boundary is described
 * consistently by the two blocks it passes through.
 *
 * <p>The shape is eight sub-steps along the slope, so a 30-35 degree ramp is walked, not jumped: a
 * single step is about 0.08 blocks instead of the 0.625 a one-block-per-column staircase would need,
 * well inside the 0.6 block step a player takes for free.
 */
public final class RampCollisionBlock extends Block {
    public static final MapCodec<RampCollisionBlock> CODEC = simpleCodec(RampCollisionBlock::new);
    public static final IntegerProperty HEIGHT = IntegerProperty.create("height", 1, 31);
    public static final IntegerProperty RISE = IntegerProperty.create("rise", 0, 15);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final int STEPS = 8;

    public RampCollisionBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(HEIGHT, 16).setValue(RISE, 0).setValue(FACING, Direction.NORTH));
    }
    @Override protected MapCodec<? extends Block> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HEIGHT, RISE, FACING);
    }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) { return 1.0f; }
    @Override protected int getLightDampening(BlockState state) { return 0; }
    @Override protected VoxelShape getOcclusionShape(BlockState state) { return Shapes.empty(); }
    @Override protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return Shapes.empty(); }
    @Override protected VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) { return Shapes.empty(); }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return shape(state); }
    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return shape(state); }
    @Override protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) { return shape(state); }
    @Override protected boolean isCollisionShapeFullBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(HEIGHT) >= 16 && state.getValue(RISE) == 0;
    }

    public static VoxelShape shape(BlockState state) {
        return shape(state.getValue(HEIGHT), state.getValue(RISE), state.getValue(FACING));
    }
    /** Eight sub-steps of two sixteenths each, climbing from the downhill edge toward {@code facing}. */
    public static VoxelShape shape(int height, int rise, Direction facing) {
        VoxelShape result = Shapes.empty();
        for (int step = 0; step < STEPS; step++) {
            // Surface height at the middle of this slice; below the block it contributes nothing.
            double top = height - rise * (STEPS - 0.5 - step) / STEPS;
            if (top <= 0.05) continue;
            top = Math.min(16, top);
            // Distance of this slice from the downhill edge, in sixteenths.
            double near = step * 16.0 / STEPS, far = near + 16.0 / STEPS;
            double lo = facing.getAxisDirection() == Direction.AxisDirection.POSITIVE ? near : 16 - far;
            double hi = facing.getAxisDirection() == Direction.AxisDirection.POSITIVE ? far : 16 - near;
            result = Shapes.or(result, facing.getAxis() == Direction.Axis.X
                ? Block.box(lo, 0, 0, hi, top, 16)
                : Block.box(0, 0, lo, 16, top, hi));
        }
        return result;
    }
}
