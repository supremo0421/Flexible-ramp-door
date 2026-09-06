package dev.rampdoor;

import net.minecraft.core.Direction;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Rigid-body math for one ramp. Every consumer (displays, collision surface, clearance checks)
 * derives its geometry here so the visual plate and the walking surface cannot drift apart.
 *
 * <p>Hinge frame: {@code u} is the horizontal distance from the hinge along the direction the deck
 * extends (non-negative over the deck), {@code v} is the height above the hinge. A positive rotation
 * tips {@code +u} toward {@code -v}, that is, the ramp goes down.
 */
public final class RampGeometry {
    private RampGeometry() {}

    /** Rotation of the whole ramp in the hinge frame; positive lowers the deck. */
    public static double radians(RampDefinition r) {
        return Math.toRadians(r.angle) * ("up".equals(r.direction) ? -1 : 1);
    }
    /** The same rotation expressed around the world axis the hinge runs along. */
    public static double worldDegrees(RampDefinition r) {
        return Math.toDegrees(radians(r)) * r.extend * ("z".equals(r.axis) ? -1 : 1);
    }
    public static Quaternionf rotation(RampDefinition r, double worldDegrees) {
        float radians = (float) Math.toRadians(worldDegrees);
        return "z".equals(r.axis) ? new Quaternionf().rotationZ(radians) : new Quaternionf().rotationX(radians);
    }
    /** Horizontal axis the deck extends along; the hinge runs along the other one. */
    public static Direction.Axis extensionAxis(RampDefinition r) {
        return "z".equals(r.axis) ? Direction.Axis.X : Direction.Axis.Z;
    }
    public static double hingeAlongExtension(RampDefinition r) { return "z".equals(r.axis) ? r.hingeX : r.hingeZ; }
    /** World coordinate on the extension axis of a template position. */
    public static int extensionOf(RampDefinition r, GridPos p) { return "z".equals(r.axis) ? p.x() : p.z(); }
    /** World coordinate on the hinge axis of a template position. */
    public static int hingeOf(RampDefinition r, GridPos p) { return "z".equals(r.axis) ? p.z() : p.x(); }
    public static GridPos position(RampDefinition r, int alongHinge, int alongExtension, int y) {
        return "z".equals(r.axis) ? new GridPos(alongExtension, y, alongHinge) : new GridPos(alongHinge, y, alongExtension);
    }
    public static double u(RampDefinition r, double worldExtension) { return r.extend * (worldExtension - hingeAlongExtension(r)); }
    public static double worldExtension(RampDefinition r, double u) { return hingeAlongExtension(r) + r.extend * u; }

    /** Hinge-frame point after the ramp has rotated by {@code radians}; returns {u, v}. */
    public static double[] rotate(double u, double v, double radians) {
        double c = Math.cos(radians), s = Math.sin(radians);
        return new double[]{u * c + v * s, -u * s + v * c};
    }
    /** Direction of increasing surface height on the open ramp; orients the collision sub-steps. */
    public static Direction uphill(RampDefinition r) {
        boolean positive = -r.extend * (radians(r) >= 0 ? 1 : -1) > 0;
        return Direction.fromAxisAndDirection(extensionAxis(r), positive ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE);
    }
    /** Offset of a block corner from the hinge, in world axes. */
    public static Vector3f offset(RampDefinition r, GridPos p) {
        return new Vector3f((float)(p.x() - r.hingeX), (float)(p.y() - r.hingeY), (float)(p.z() - r.hingeZ));
    }
    public static double eased(String curve, double progress) {
        double t = Math.clamp(progress, 0.0, 1.0);
        return "linear".equals(curve) ? t : t * t * (3 - 2 * t);
    }
}
