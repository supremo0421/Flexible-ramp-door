package dev.rampdoor;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/**
 * One running open or close. The server only hands the client a handful of eased keyframes; the
 * interpolation between them runs client side, so a 400 block ramp costs no per-tick transform work.
 */
public final class RampAnimator {
    /** Ticks between spawning the displays and starting to move them, so spawn metadata lands first. */
    private static final int SETTLE = 2;
    public final RampDefinition ramp;
    public final ServerLevel level;
    public final RampState from, to;
    /** Real blocks of the closed ramp: removed when opening, restored when closing. */
    public final Map<GridPos, BlockState> blocks;
    /** Invisible walking surface of the open ramp. */
    public final Map<GridPos, BlockState> collision;
    public final Set<GridPos> footprint = new HashSet<>();
    public final double openDegrees;
    private final int segments;
    public RampDisplayManager displays;
    public int elapsed;
    private int segment;

    public RampAnimator(ServerLevel level, RampDefinition ramp, RampState to, Map<GridPos, BlockState> collision) {
        this.level = level; this.ramp = ramp; this.from = ramp.state; this.to = to;
        this.blocks = ramp.blocks(); this.collision = collision;
        this.openDegrees = RampGeometry.worldDegrees(ramp);
        this.segments = (int) Math.clamp(ramp.duration / 5L, 4, 16);
        footprint.addAll(blocks.keySet()); footprint.addAll(collision.keySet());
    }
    public double degreesAt(double progress) {
        double eased = RampGeometry.eased(ramp.easing, progress);
        return openDegrees * (to == RampState.OPEN ? eased : 1 - eased);
    }
    public boolean tick() {
        if (elapsed >= SETTLE && segment < segments
            && elapsed >= SETTLE + Math.round((double) ramp.duration * segment / segments)) {
            int start = SETTLE + (int) Math.round((double) ramp.duration * segment / segments);
            int end = SETTLE + (int) Math.round((double) ramp.duration * (segment + 1) / segments);
            displays.frame(degreesAt((segment + 1.0) / segments), Math.max(1, end - start));
            segment++;
        }
        return ++elapsed > ramp.duration + SETTLE;
    }
}
