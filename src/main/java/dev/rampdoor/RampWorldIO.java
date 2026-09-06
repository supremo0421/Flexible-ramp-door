package dev.rampdoor;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** Every world read and write RampDoor performs, with drops and neighbour side effects suppressed. */
public final class RampWorldIO {
    private static final ThreadLocal<Boolean> INTERNAL = ThreadLocal.withInitial(() -> false);
    public static boolean isInternalWrite() { return INTERNAL.get(); }
    private RampWorldIO() {}

    public static void supported(BlockState state, GridPos pos) {
        if (state.hasBlockEntity() || state.is(Blocks.MOVING_PISTON) || !state.getFluidState().isEmpty()
            || state.getBlock() instanceof LiquidBlock || state.is(RampDoorMod.COLLISION))
            throw new IllegalArgumentException("Ramp capture failed: unsupported block " + state + " at " + pos);
    }
    public static void valid(ServerLevel level, GridPos pos) {
        if (!level.isInWorldBounds(pos.block()) || !level.getWorldBorder().isWithinBounds(pos.block()))
            throw new IllegalArgumentException("Position outside build limits / world border: " + pos);
        if (!level.hasChunk(pos.x() >> 4, pos.z() >> 4))
            throw new IllegalArgumentException("Ramp chunk is not loaded at " + pos);
    }
    /** The captured structure must still be exactly where and what it was. */
    public static void intact(ServerLevel level, Map<GridPos, BlockState> blocks) {
        for (var e : blocks.entrySet()) {
            valid(level, e.getKey());
            if (!level.getBlockState(e.getKey().block()).equals(e.getValue()))
                throw new IllegalArgumentException("Ramp structure changed at " + e.getKey() + "; restore or recapture it.");
        }
    }
    /** Cells the ramp needs to own must be free, ignoring the ramp's own blocks. */
    public static void clearance(ServerLevel level, Collection<GridPos> cells, Set<GridPos> owned) {
        for (GridPos p : cells) {
            valid(level, p);
            if (owned.contains(p)) continue;
            BlockState state = level.getBlockState(p.block());
            if (!state.isAir() && !state.is(RampDoorMod.COLLISION))
                throw new IllegalArgumentException("Ramp cannot move: destination obstructed at " + p);
        }
    }
    public static void clear(ServerLevel level, Collection<GridPos> positions) {
        for (GridPos p : positions) write(level, p, Blocks.AIR.defaultBlockState());
    }
    /** Removes only collision blocks, so an unrelated block in a stale record is left alone. */
    public static int clearCollision(ServerLevel level, Collection<GridPos> positions) {
        int removed = 0;
        for (GridPos p : positions) {
            if (!level.hasChunk(p.x() >> 4, p.z() >> 4)) continue;
            if (!level.getBlockState(p.block()).is(RampDoorMod.COLLISION)) continue;
            write(level, p, Blocks.AIR.defaultBlockState()); removed++;
        }
        return removed;
    }
    public static void place(ServerLevel level, Map<GridPos, BlockState> template) {
        template.forEach((p, s) -> write(level, p, s));
    }
    /** Places collision blocks, skipping cells something else has taken since the clearance check. */
    public static List<GridPos> placeCollision(ServerLevel level, Map<GridPos, BlockState> plan) {
        List<GridPos> placed = new ArrayList<>();
        plan.forEach((p, s) -> {
            BlockState existing = level.getBlockState(p.block());
            if (!existing.isAir() && !existing.is(RampDoorMod.COLLISION)) {
                RampDoorMod.LOGGER.warn("Ramp collision cell {} is occupied by {}; skipped", p, existing);
                return;
            }
            write(level, p, s); placed.add(p);
        });
        return placed;
    }
    private static void write(ServerLevel level, GridPos p, BlockState state) {
        // No breakBlock/destroyBlock; suppress drops, neighbor shape changes and onPlace side effects.
        boolean prior = INTERNAL.get(); INTERNAL.set(true);
        try { level.setBlock(p.block(), state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE
            | Block.UPDATE_SUPPRESS_DROPS | Block.UPDATE_SKIP_ON_PLACE); }
        finally { INTERNAL.set(prior); }
        if (!level.getBlockState(p.block()).equals(state)) throw new IllegalStateException("World write failed at " + p);
    }
}
