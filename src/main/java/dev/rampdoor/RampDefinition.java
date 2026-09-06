package dev.rampdoor;

import java.util.*;

/**
 * One ramp. v0.2 stores a single RAMP_TEMPLATE: the real closed structure. The open shape is not a
 * second capture, it is this template rotated around the hinge.
 */
public final class RampDefinition {
    public String id;
    public String dimension;
    /** Generation tag of the displays currently owned by this ramp; rotated on every respawn. */
    public String entityTag = "rampdoor.gen." + UUID.randomUUID();
    public GridPos origin;
    public double hingeX, hingeY, hingeZ;
    public GridPos controller;
    public RampTemplate template = new RampTemplate();
    /** Opening angle in degrees, 10..90. */
    public double angle = 32;
    /** Hinge axis: the world axis the hinge runs along. */
    public String axis = "x";
    /** While true, capture and hinge changes re-derive {@link #axis} and {@link #extend}. */
    public boolean autoAxis = true;
    /** +1 or -1: which way the deck extends from the hinge along the other horizontal axis. */
    public int extend = 1;
    /** "down" or "up". */
    public String direction = "down";
    public int duration = 40;
    public String easing = "ease_in_out";
    /** "auto", or explicit walkable bounds in blocks when auto guesses badly. */
    public String collisionMode = "auto";
    public int collisionWidth = 0;
    public int collisionLength = 0;
    public boolean redstoneEnabled = true;
    public RampState state = RampState.CLOSED;
    public MotionType motion = MotionType.RAMP;
    /** Collision blocks this ramp currently owns in the world. */
    public List<GridPos> collision = new ArrayList<>();

    public String displayTag() { return "rampdoor:" + id; }
    public boolean ready() { return !template.blocks.isEmpty(); }
    public Map<GridPos, net.minecraft.world.level.block.state.BlockState> blocks() { return template.world(origin); }

    public void hinge(double x, double y, double z) {
        GridPos next = new GridPos((int)Math.floor(x), (int)Math.floor(y), (int)Math.floor(z));
        template.rebase(origin, next);
        origin = next; hingeX = x; hingeY = y; hingeZ = z;
        if (autoAxis) autoOrient();
    }
    /** Picks the hinge axis and the extension sign from the captured structure. */
    public void autoOrient() {
        if (!ready()) return;
        double sumX = 0, sumZ = 0; int count = 0;
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (GridPos p : blocks().keySet()) {
            sumX += p.x() + 0.5 - hingeX; sumZ += p.z() + 0.5 - hingeZ; count++;
            minX = Math.min(minX, p.x()); maxX = Math.max(maxX, p.x());
            minZ = Math.min(minZ, p.z()); maxZ = Math.max(maxZ, p.z());
        }
        double meanX = sumX / count, meanZ = sumZ / count;
        // The deck runs away from the hinge along its longer, more off-centre axis.
        boolean alongZ = Math.abs(meanZ) >= Math.abs(meanX) ? true : false;
        if (Math.abs(meanZ) == Math.abs(meanX)) alongZ = (maxZ - minZ) >= (maxX - minX);
        axis = alongZ ? "x" : "z";
        double mean = alongZ ? meanZ : meanX;
        extend = mean < 0 ? -1 : 1;
    }
}
