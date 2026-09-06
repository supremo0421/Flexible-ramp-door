package dev.rampdoor;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import java.util.*;

/**
 * Turns the rotated deck into invisible walking blocks. The top face of every captured column is
 * rotated around the hinge and the resulting sloped segment is written as {@code ramp_collision}
 * states, so the surface a player walks matches the surface a player sees.
 */
public final class RampCollisionSurface {
    private RampCollisionSurface() {}
    /** Below this cosine the ramp stands too steep for a walking surface to mean anything. */
    private static final double MIN_COSINE = 0.15;
    private record Column(int alongHinge, int alongExtension) {}
    private static final class Span {
        double low = Double.POSITIVE_INFINITY, high = Double.NEGATIVE_INFINITY;
        void add(double a, double b) { low = Math.min(low, Math.min(a, b)); high = Math.max(high, Math.max(a, b)); }
    }

    public static boolean walkable(RampDefinition r) { return Math.abs(Math.cos(RampGeometry.radians(r))) >= MIN_COSINE; }

    /** The collision blocks an open ramp should own. Empty for ramps too steep to walk. */
    public static Map<GridPos, BlockState> plan(RampDefinition r, int limit) {
        Map<GridPos, BlockState> result = new LinkedHashMap<>();
        if (!r.ready() || !walkable(r)) return result;
        double radians = RampGeometry.radians(r);
        Direction uphill = RampGeometry.uphill(r);

        // Top face of every captured column, in world Y.
        Map<Column, Double> tops = new LinkedHashMap<>();
        int minHinge = Integer.MAX_VALUE, maxHinge = Integer.MIN_VALUE;
        for (GridPos p : r.blocks().keySet()) {
            Column column = new Column(RampGeometry.hingeOf(r, p), RampGeometry.extensionOf(r, p));
            tops.merge(column, (double) p.y() + 1, Math::max);
            minHinge = Math.min(minHinge, column.alongHinge()); maxHinge = Math.max(maxHinge, column.alongHinge());
        }
        // Optional manual bounds: a narrower centred deck, and a shorter run measured from the hinge.
        int hingeLow = minHinge, hingeHigh = maxHinge;
        if (r.collisionWidth > 0 && r.collisionWidth < maxHinge - minHinge + 1) {
            int margin = (maxHinge - minHinge + 1 - r.collisionWidth) / 2;
            hingeLow = minHinge + margin; hingeHigh = hingeLow + r.collisionWidth - 1;
        }

        Map<Column, Span> surface = new LinkedHashMap<>();
        for (var entry : tops.entrySet()) {
            Column column = entry.getKey();
            if (column.alongHinge() < hingeLow || column.alongHinge() > hingeHigh) continue;
            double uA = RampGeometry.u(r, column.alongExtension()), uB = RampGeometry.u(r, column.alongExtension() + 1);
            double uNear = Math.min(uA, uB), uFar = Math.max(uA, uB);
            if (uFar <= 0) continue; // behind the hinge: not part of the walking run
            if (r.collisionLength > 0 && uNear >= r.collisionLength) continue;
            double top = entry.getValue() - r.hingeY;
            double[] near = RampGeometry.rotate(uNear, top, radians), far = RampGeometry.rotate(uFar, top, radians);
            double eNear = RampGeometry.worldExtension(r, near[0]), eFar = RampGeometry.worldExtension(r, far[0]);
            double yNear = r.hingeY + near[1], yFar = r.hingeY + far[1];
            if (eNear > eFar) {
                double swapE = eNear; eNear = eFar; eFar = swapE;
                double swapY = yNear; yNear = yFar; yFar = swapY;
            }
            if (eFar - eNear < 1.0e-9) continue;
            for (int cell = (int) Math.floor(eNear); cell <= (int) Math.floor(eFar - 1.0e-9); cell++) {
                double from = Math.max(eNear, cell), to = Math.min(eFar, cell + 1);
                if (to - from < 1.0e-9) continue;
                double yFrom = yNear + (yFar - yNear) * (from - eNear) / (eFar - eNear);
                double yTo = yNear + (yFar - yNear) * (to - eNear) / (eFar - eNear);
                surface.computeIfAbsent(new Column(column.alongHinge(), cell), ignored -> new Span()).add(yFrom, yTo);
            }
        }
        for (var entry : surface.entrySet()) {
            Span span = entry.getValue();
            for (int y = (int) Math.floor(span.low); y <= (int) Math.floor(span.high - 1.0e-9); y++) {
                // The same sloped line is handed to every block it passes through; each one keeps
                // the part that falls inside it, so the two blocks of a boundary-crossing cell agree.
                int height = (int) Math.clamp(Math.round((span.high - y) * 16), 1, 31);
                int rise = (int) Math.clamp(Math.round((span.high - span.low) * 16), 0, 15);
                result.put(RampGeometry.position(r, entry.getKey().alongHinge(), entry.getKey().alongExtension(), y),
                    RampDoorMod.COLLISION.defaultBlockState()
                        .setValue(RampCollisionBlock.HEIGHT, height)
                        .setValue(RampCollisionBlock.RISE, rise)
                        .setValue(RampCollisionBlock.FACING, uphill));
                if (result.size() > limit) throw new IllegalArgumentException("Ramp collision surface exceeds max_collision_blocks_per_ramp (" + limit + ").");
            }
        }
        return result;
    }

    /** World box the ramp occupies at one rotation; used for clearance and entity checks. */
    public static AABB bounds(RampDefinition r, double radians) {
        double minU = Double.POSITIVE_INFINITY, maxU = Double.NEGATIVE_INFINITY;
        double minV = Double.POSITIVE_INFINITY, maxV = Double.NEGATIVE_INFINITY;
        double minHinge = Double.POSITIVE_INFINITY, maxHinge = Double.NEGATIVE_INFINITY;
        for (GridPos p : r.blocks().keySet()) {
            int extension = RampGeometry.extensionOf(r, p), alongHinge = RampGeometry.hingeOf(r, p);
            minHinge = Math.min(minHinge, alongHinge); maxHinge = Math.max(maxHinge, alongHinge + 1.0);
            for (double u : new double[]{RampGeometry.u(r, extension), RampGeometry.u(r, extension + 1)})
                for (double v : new double[]{p.y() - r.hingeY, p.y() + 1 - r.hingeY}) {
                    double[] rotated = RampGeometry.rotate(u, v, radians);
                    minU = Math.min(minU, rotated[0]); maxU = Math.max(maxU, rotated[0]);
                    minV = Math.min(minV, rotated[1]); maxV = Math.max(maxV, rotated[1]);
                }
        }
        if (minU > maxU) return new AABB(r.hingeX, r.hingeY, r.hingeZ, r.hingeX, r.hingeY, r.hingeZ);
        double e1 = RampGeometry.worldExtension(r, minU), e2 = RampGeometry.worldExtension(r, maxU);
        double eLow = Math.min(e1, e2), eHigh = Math.max(e1, e2);
        double yLow = r.hingeY + minV, yHigh = r.hingeY + maxV;
        return "z".equals(r.axis)
            ? new AABB(eLow, yLow, minHinge, eHigh, yHigh, maxHinge)
            : new AABB(minHinge, yLow, eLow, maxHinge, yHigh, eHigh);
    }
    /** Union of the boxes the ramp passes through while it moves, sampled along the rotation. */
    public static AABB sweptBounds(RampDefinition r) {
        double open = RampGeometry.radians(r);
        AABB result = null;
        for (int step = 0; step <= 8; step++) {
            AABB box = bounds(r, open * step / 8.0);
            result = result == null ? box : result.minmax(box);
        }
        return result;
    }
}
