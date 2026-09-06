package dev.rampdoor;

import com.mojang.math.Transformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityTypes;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.*;

/**
 * The visible ramp. One BlockDisplay per captured block, all sharing the hinge, the angle and the
 * interpolation window, so the plate moves as one rigid body instead of a swarm of blocks. Both the
 * position and the block's own orientation are rotated, so a 32 degree ramp shows 32 degree blocks.
 */
public final class RampDisplayManager {
    public static final String TAG = "rampdoor.display";
    private record Part(Display.BlockDisplay entity, Vector3f offset) {}
    private final List<Part> parts = new ArrayList<>();
    private final RampDefinition ramp;
    public double degrees;

    public RampDisplayManager(ServerLevel level, RampDefinition ramp, double degrees) {
        this.ramp = ramp; this.degrees = degrees;
        // A fresh generation tag lets late chunk loads tell live displays from crash leftovers.
        ramp.entityTag = "rampdoor.gen." + UUID.randomUUID();
        try {
            for (var entry : ramp.template.blocks) {
                GridPos p = entry.offset().plus(ramp.origin);
                var display = new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, level);
                display.addTag(TAG); display.addTag(ramp.displayTag()); display.addTag(ramp.entityTag);
                display.setPos(ramp.hingeX, ramp.hingeY, ramp.hingeZ);
                display.setBlockState(entry.decode());
                display.setViewRange(4f);
                Vector3f offset = RampGeometry.offset(ramp, p);
                // Vanilla display culling boxes extend upward from the entity; a downward ramp can
                // leave that box. Zero dimensions explicitly disable frustum culling.
                display.setWidth(0); display.setHeight(0);
                display.setTransformation(transform(offset, degrees));
                parts.add(new Part(display, offset));
                if (!level.addFreshEntity(display)) throw new IllegalStateException("Cannot spawn RampDoor display");
            }
        } catch (RuntimeException e) { close(); throw e; }
    }
    private Transformation transform(Vector3f offset, double degrees) {
        Quaternionf rotation = RampGeometry.rotation(ramp, degrees);
        return new Transformation(rotation.transform(new Vector3f(offset)), rotation, new Vector3f(1), new Quaternionf());
    }
    /** Hands the client one interpolated segment; the server does no per-tick transform work. */
    public void frame(double degrees, int ticks) {
        this.degrees = degrees;
        for (Part p : parts) {
            p.entity.setTransformationInterpolationDuration(ticks);
            p.entity.setTransformationInterpolationDelay(0);
            p.entity.setTransformation(transform(p.offset, degrees));
        }
    }
    /** Freezes the ramp at its endpoint: no interpolation, no further updates. */
    public void settle(double degrees) { frame(degrees, 0); }
    /**
     * False once the ramp's chunks have unloaded: the entities are still in the save file but these
     * instances no longer belong to the world, so they must be re-created before they can be moved.
     */
    public boolean alive() {
        for (Part p : parts) if (p.entity.isRemoved() || p.entity.level().getEntity(p.entity.getId()) != p.entity) return false;
        return !parts.isEmpty();
    }
    public void close() { for (Part p : parts) p.entity.discard(); parts.clear(); }
    public int size() { return parts.size(); }
}
