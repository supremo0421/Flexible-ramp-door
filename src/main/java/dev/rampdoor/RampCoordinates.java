package dev.rampdoor;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Coordinate inputs use inclusive block corners; hinge positions use continuous world coordinates. */
public final class RampCoordinates {
    private RampCoordinates() {}
    public record Layout(BlockPos first, BlockPos second, BlockPos front, String axis, int extend,
                         int width, int length) {
        public BlockPos world(int across, int yOffset, int forward) {
            return "x".equals(axis)
                ? new BlockPos(Math.min(first.getX(), second.getX()) + across, first.getY() + yOffset, first.getZ() + extend * forward)
                : new BlockPos(first.getX() + extend * forward, first.getY() + yOffset, Math.min(first.getZ(), second.getZ()) + across);
        }
        public Vec3 hinge() {
            double edge = ("x".equals(axis) ? first.getZ() : first.getX()) + (extend < 0 ? 1.0 : 0.0);
            double middle = "x".equals(axis)
                ? (Math.min(first.getX(), second.getX()) + (double)Math.max(first.getX(), second.getX()) + 1) / 2
                : (Math.min(first.getZ(), second.getZ()) + (double)Math.max(first.getZ(), second.getZ()) + 1) / 2;
            return "x".equals(axis) ? new Vec3(middle, first.getY()+1.0, edge) : new Vec3(edge, first.getY()+1.0, middle);
        }
    }
    public static Layout layout(BlockPos first, BlockPos second, BlockPos front) {
        if (first.getY()!=second.getY() || first.getY()!=front.getY())
            throw new IllegalArgumentException("Use the same deck Y for all three creation coordinates; set opening slope with /ramp direction afterwards.");
        long dx=(long)second.getX()-first.getX(), dz=(long)second.getZ()-first.getZ();
        if ((dx==0)==(dz==0)) throw new IllegalArgumentException("Hinge endpoints must differ along exactly one axis (X or Z).");
        String axis=dx!=0?"x":"z";
        long forward="x".equals(axis)?(long)front.getZ()-first.getZ():(long)front.getX()-first.getX();
        long width=Math.abs(dx+dz)+1, length=Math.abs(forward)+1;
        if (width>256 || length>256) throw new IllegalArgumentException("Coordinate ramp width and length must be 2..256 blocks.");
        if (forward==0) throw new IllegalArgumentException("Front coordinate must lie away from the hinge along the other horizontal axis.");
        int cross="x".equals(axis)?front.getX():front.getZ();
        int low="x".equals(axis)?Math.min(first.getX(),second.getX()):Math.min(first.getZ(),second.getZ());
        if (cross<low || cross>=low+width) throw new IllegalArgumentException("Front coordinate must be within the hinge's width.");
        return new Layout(first.immutable(),second.immutable(),front.immutable(),axis,forward>0?1:-1,(int)width,(int)length);
    }
    public record Heading(String axis, int extend, String direction, double angle) {
        public void apply(RampDefinition ramp) {
            ramp.axis=axis; ramp.extend=extend; ramp.direction=direction; ramp.angle=angle; ramp.autoAxis=false;
        }
    }
    /** The target is a direction ray from the hinge, not a resize or a promise of a tip position. */
    public static Heading heading(RampDefinition r, Vec3 target) {
        double dx=target.x-r.hingeX, dy=target.y-r.hingeY, dz=target.z-r.hingeZ;
        if (!Double.isFinite(dx)||!Double.isFinite(dy)||!Double.isFinite(dz)) throw new IllegalArgumentException("Direction coordinates must be finite.");
        if (Math.abs(dx)<1e-7 && Math.abs(dz)<1e-7)
            throw new IllegalArgumentException("Direction point needs horizontal distance from the hinge.");
        if (Math.abs(Math.abs(dx)-Math.abs(dz))<1e-7)
            throw new IllegalArgumentException("Direction is exactly diagonal; choose a point primarily along X or Z.");
        boolean alongX=Math.abs(dx)>Math.abs(dz);
        double forward=alongX?dx:dz;
        double angle=Math.abs(dy)<1e-7?r.angle:Math.toDegrees(Math.atan2(Math.abs(dy),Math.abs(forward)));
        if (angle<10 || angle>90) throw new IllegalArgumentException("Coordinate slope must be 10..90 degrees (or use the same Y to keep the current angle).");
        return new Heading(alongX?"z":"x",forward>0?1:-1,Math.abs(dy)<1e-7?r.direction:dy>0?"up":"down",angle);
    }
}
