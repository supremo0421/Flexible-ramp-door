package dev.rampdoor;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.*;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;
import java.util.*;

public class RampGameTests {
    private static final String EMPTY = "fabric-gametest-api-v1:empty";
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private static void rejected(Runnable action, String message) {
        boolean rejected = false;
        try { action.run(); } catch (IllegalArgumentException expected) { rejected = true; }
        require(rejected, message);
    }
    private static long displays(GameTestHelper h, RampDefinition r) {
        long count = 0;
        for (var e : h.getLevel().getAllEntities()) if (e instanceof Display.BlockDisplay && e.entityTags().contains(r.displayTag())) count++;
        return count;
    }
    /** CLOSED: the captured blocks are real again, nothing else of ours is left in the world. */
    private static void closed(GameTestHelper h, RampDefinition r) {
        require(r.state == RampState.CLOSED, "Wrong endpoint: " + r.state);
        r.blocks().forEach((p, s) -> require(h.getLevel().getBlockState(p.block()).equals(s), "Block loss/duplication/state mismatch at " + p));
        require(displays(h, r) == 0, "Idle display remains while closed");
        require(r.collision.isEmpty(), "Closed ramp still records collision blocks");
        for (var e : h.getLevel().getAllEntities())
            require(!(e instanceof ItemEntity) || e.distanceToSqr(r.hingeX, r.hingeY, r.hingeZ) > 4096, "Unexpected dropped item");
    }
    /** OPEN: no real blocks, one display per captured block, an invisible surface to walk on. */
    private static void open(GameTestHelper h, RampDefinition r, RampManager m) {
        require(r.state == RampState.OPEN, "Wrong endpoint: " + r.state);
        // Near the hinge the walking surface legitimately sits in a cell the deck used to fill.
        for (GridPos p : r.blocks().keySet()) {
            BlockState state = h.getLevel().getBlockState(p.block());
            require(state.isAir() || state.is(RampDoorMod.COLLISION), "Open ramp left a real block at " + p);
        }
        require(displays(h, r) == r.template.blocks.size(), "Open ramp must keep one display per block, found " + displays(h, r));
        require(m.activeCount() == 0, "Open ramp is still running an animation task");
        require(!r.collision.isEmpty(), "Open ramp has no walking surface");
        for (GridPos p : r.collision) require(h.getLevel().getBlockState(p.block()).is(RampDoorMod.COLLISION), "Missing collision block at " + p);
    }

    @GameTest(structure = EMPTY, maxTicks = 20000)
    public void acceptance(GameTestHelper h) {
        var m = RampDoorMod.manager(h.getLevel().getServer());
        require(m != null, "Server manager not started");
        BlockPos base = h.absolutePos(new BlockPos(64, 80, 64));
        // Hold the fixture area loaded for the whole test: an open ramp keeps its displays as
        // ordinary entities, and unloaded chunks would hide them from getAllEntities().
        for (int x = (base.getX() - 32) >> 4; x <= (base.getX() + 48) >> 4; x++)
            for (int z = (base.getZ() - 32) >> 4; z <= (base.getZ() + 48) >> 4; z++) {
                h.getLevel().getChunk(x, z);
                h.getLevel().getChunkSource().addTicketWithRadius(RampDoorMod.TICKET, new net.minecraft.world.level.ChunkPos(x, z), 1);
            }
        // Test fixture only touches a previously unused high-altitude area of the disposable test world.
        String testId = "test_" + UUID.randomUUID().toString().replace("-", "");
        RampDemo.create(m, testId, h.getLevel(), base);
        RampDefinition r = m.get(testId);
        require(r.template.blocks.size() == 436, "Fixture must have 436 blocks, has " + r.template.blocks.size());
        require(r.axis.equals("x") && r.extend == 1, "Auto orientation picked " + r.axis + "/" + r.extend);
        closed(h, r);
        rigidBody(r);

        UUID selector = UUID.randomUUID();
        m.select(selector, h.getLevel(), base.offset(-8, -1, 0), true);
        m.select(selector, h.getLevel(), base.offset(7, 0, 21), false);
        require(m.capture(r, selector) == 436, "Capture of the closed structure failed");
        GridPos sample = r.blocks().keySet().iterator().next();
        var original = h.getLevel().getBlockState(sample.block());
        h.getLevel().setBlockAndUpdate(sample.block(), Blocks.CHEST.defaultBlockState());
        rejected(() -> m.capture(r, selector), "Capture accepted a BlockEntity");
        rejected(() -> m.move(r, RampState.OPEN), "Changed structure was accepted");
        h.getLevel().setBlockAndUpdate(sample.block(), original);
        int oldLimit = m.config.max_blocks_per_ramp;
        try { m.config.max_blocks_per_ramp = 435; rejected(() -> m.capture(r, selector), "Capture ignored block limit"); }
        finally { m.config.max_blocks_per_ramp = oldLimit; }
        require(r.template.blocks.size() == 436, "Rejected capture replaced the saved template");
        var before = r.blocks();
        r.hinge(r.hingeX + 0.5, r.hingeY + 0.25, r.hingeZ + 0.5);
        require(before.equals(r.blocks()), "Hinge rebase changed world positions");
        r.hinge(base.getX(), base.getY() + 1.0, base.getZ());

        var plan = m.collisionPlan(r);
        require(!plan.isEmpty(), "A 32 degree ramp must produce a walking surface");
        // Near the hinge the walking surface shares a cell with the deck itself, which is allowed;
        // pick a cell the ramp does not already own.
        GridPos obstruction = plan.keySet().stream().filter(p -> !r.blocks().containsKey(p)).findFirst().orElseThrow();
        h.getLevel().setBlockAndUpdate(obstruction.block(), Blocks.STONE.defaultBlockState());
        rejected(() -> m.move(r, RampState.OPEN), "Obstructed destination was ignored");
        r.blocks().forEach((p, s) -> require(h.getLevel().getBlockState(p.block()).equals(s), "Rejected motion modified the structure"));
        h.getLevel().setBlockAndUpdate(obstruction.block(), Blocks.AIR.defaultBlockState());
        rejected(() -> RampWorldIO.supported(Blocks.CHEST.defaultBlockState(), obstruction), "Chest accepted");
        rejected(() -> RampWorldIO.supported(Blocks.WATER.defaultBlockState(), obstruction), "Water accepted");

        crash(h, m, r, RampState.OPENING);
        crash(h, m, r, RampState.CLOSING);

        r.duration = 40; m.save();
        m.move(r, RampState.OPEN);
        require(displays(h, r) == 436, "Opening did not spawn one display per block");
        h.runAfterDelay(39, () -> require(r.state == RampState.OPENING, "40-tick animation finished early"));
        h.runAfterDelay(45, () -> {
            open(h, r, m);
            walkable(h, r);
            invisible(h, r);
            reload(h, m, r);
            obstructedClose(h, m, r);
            r.duration = 2; m.save();
            cycle(h, m, r, 0);
        });
    }

    /** Every block shares one pivot and one angle: the display transform is the collision math. */
    private static void rigidBody(RampDefinition r) {
        double radians = RampGeometry.radians(r);
        var rotation = RampGeometry.rotation(r, RampGeometry.worldDegrees(r));
        for (GridPos p : r.blocks().keySet()) {
            Vector3f moved = rotation.transform(RampGeometry.offset(r, p), new Vector3f());
            double[] frame = RampGeometry.rotate(RampGeometry.u(r, RampGeometry.extensionOf(r, p)), p.y() - r.hingeY, radians);
            double extension = RampGeometry.worldExtension(r, frame[0]) - RampGeometry.hingeAlongExtension(r);
            double actual = "z".equals(r.axis) ? moved.x() : moved.z();
            require(Math.abs(actual - extension) < 1.0e-3 && Math.abs(moved.y() - frame[1]) < 1.0e-3,
                "Display transform and ramp geometry disagree at " + p);
        }
    }
    /** Fine-grained walk over the open surface: no holes, no jumps, and it tracks the visible plate. */
    private static void walkable(GameTestHelper h, RampDefinition r) {
        double slope = Math.tan(Math.toRadians(r.angle));
        double run = RampDemo.LENGTH * Math.cos(Math.toRadians(r.angle));
        double previous = Double.NaN;
        for (double t = 0.2; t < run - 0.2; t += 0.125) {
            double worldZ = r.hingeZ + t, worldX = r.hingeX + 0.5;
            double top = surface(h, worldX, worldZ, r.hingeY + 1, r.hingeY - run * slope - 2);
            require(Double.isFinite(top), "Hole in the open walking surface at z offset " + t);
            require(Math.abs(top - (r.hingeY - t * slope)) < 0.2, "Walking surface drifts from the visible ramp at z offset " + t);
            require(Double.isNaN(previous) || Math.abs(top - previous) <= 0.35, "Unwalkable step at z offset " + t + ": " + Math.abs(top - previous));
            previous = top;
        }
    }
    private static double surface(GameTestHelper h, double x, double z, double from, double to) {
        int blockX = (int) Math.floor(x), blockZ = (int) Math.floor(z);
        double dx = x - blockX, dz = z - blockZ;
        for (int y = (int) Math.floor(from); y >= (int) Math.floor(to); y--) {
            BlockPos p = new BlockPos(blockX, y, blockZ);
            BlockState state = h.getLevel().getBlockState(p);
            if (state.isAir()) continue;
            double top = Double.NEGATIVE_INFINITY;
            for (AABB box : state.getCollisionShape(h.getLevel(), p).toAabbs())
                if (box.minX - 1e-9 <= dx && box.maxX + 1e-9 >= dx && box.minZ - 1e-9 <= dz && box.maxZ + 1e-9 >= dz)
                    top = Math.max(top, y + box.maxY);
            if (Double.isFinite(top)) return top;
        }
        return Double.NaN;
    }
    /** The surface must never be visible, and it must not exist as an item at all. */
    private static void invisible(GameTestHelper h, RampDefinition r) {
        require(!net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(RampDoorMod.id("ramp_collision")),
            "Collision block is obtainable as an item");
        for (GridPos p : r.collision) {
            BlockState state = h.getLevel().getBlockState(p.block());
            require(state.getRenderShape() == RenderShape.INVISIBLE, "Collision block renders at " + p);
            require(state.getOcclusionShape().isEmpty(), "Collision block occludes light at " + p);
            require(!state.getCollisionShape(h.getLevel(), p.block()).isEmpty(), "Collision block has no collision at " + p);
        }
    }
    /** Save, reload the file into a second manager, and the open ramp must come back identical. */
    private static void reload(GameTestHelper h, RampManager m, RampDefinition r) {
        try {
            var reloaded = new RampManager(m.server, m.config);
            var saved = reloaded.get(r.id);
            require(saved.state == RampState.OPEN, "Open state was not durable");
            require(saved.angle == r.angle && saved.axis.equals(r.axis) && saved.extend == r.extend, "Reloaded ramp changed its geometry");
            require(saved.template.blocks.size() == r.template.blocks.size(), "Reloaded template lost blocks");
            reloaded.recover(saved);
            require(saved.state == RampState.OPEN, "Reload recovery did not stay open");
            require(!saved.collision.isEmpty(), "Reload did not restore the walking surface");
            for (GridPos p : saved.collision) require(h.getLevel().getBlockState(p.block()).is(RampDoorMod.COLLISION), "Reload left the surface incomplete at " + p);
            // Only one manager per server owns displays, so hand the rebuilt ramp back to the live one.
            r.entityTag = saved.entityTag; r.collision = saved.collision;
            m.recover(r);
            open(h, r, m);
            walkable(h, r);
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }
    /** A player or mob standing in the ramp's path cancels the close instead of being crushed. */
    private static void obstructedClose(GameTestHelper h, RampManager m, RampDefinition r) {
        var stand = EntityTypes.ARMOR_STAND.create(h.getLevel(), EntitySpawnReason.COMMAND);
        require(stand != null, "Could not create the obstruction entity");
        stand.setPos(r.hingeX + 0.5, r.hingeY - 4, r.hingeZ + 6);
        h.getLevel().addFreshEntity(stand);
        rejected(() -> m.move(r, RampState.CLOSED), "Closed onto an entity standing on the ramp");
        require(r.state == RampState.OPEN, "Refused close changed the state");
        stand.discard();
    }
    /** A hard crash mid-motion falls back to the safe endpoint: OPENING to CLOSED, CLOSING to OPEN. */
    private static void crash(GameTestHelper h, RampManager m, RampDefinition r, RampState interrupted) {
        RampState safe = interrupted.safeEndpoint();
        r.state = interrupted; m.storage.data.journal.add(r.id); m.save();
        // A crash can leave the world half written, so wipe part of the structure first.
        var blocks = r.blocks();
        var partial = new ArrayList<>(blocks.keySet()).subList(0, 73);
        RampWorldIO.clear(h.getLevel(), partial);
        try {
            var reloaded = new RampManager(m.server, m.config);
            var saved = reloaded.get(r.id);
            require(saved.state == interrupted, "Interrupted state not durable");
            reloaded.recover(saved);
            require(saved.state == safe, "Crash recovery picked " + saved.state + " instead of " + safe);
            var again = new RampPersistentState(m.server.getWorldPath(LevelResource.ROOT));
            require(again.data.journal.isEmpty(), "Recovery WAL remains");
            require(again.data.ramps.get(r.id).state == safe, "Recovered state not saved");
            r.entityTag = saved.entityTag; r.collision = saved.collision; r.state = safe;
            if (safe == RampState.OPEN) {
                m.recover(r); open(h, r, m);
                int duration = r.duration; r.duration = 2; m.save();
                m.move(r, RampState.CLOSED);
                for (int i = 0; i < 8; i++) m.tick();
                r.duration = duration; m.save();
            }
            m.recover(r);
            closed(h, r);
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }
    private static void cycle(GameTestHelper h, RampManager m, RampDefinition r, int moves) {
        if (moves == 200) { open(h, r, m); controller(h, m, r); return; }
        RampState target = r.state == RampState.OPEN ? RampState.CLOSED : RampState.OPEN;
        m.move(r, target);
        require(m.activeCount() == 1, "Animation not registered");
        m.toggle(r); require(m.activeCount() == 1, "Duplicate animation started");
        rejected(() -> m.delete(r), "Deleting a moving ramp was allowed");
        GridPos occupied = r.blocks().keySet().iterator().next();
        require(!h.getLevel().setBlockAndUpdate(occupied.block(), Blocks.DIAMOND_BLOCK.defaultBlockState()), "Active footprint was writable");
        h.runAfterDelay(7, () -> {
            if (target == RampState.OPEN) open(h, r, m); else closed(h, r);
            require(m.chunkLeaseCount() == 0, "Chunk ticket leaked");
            cycle(h, m, r, moves + 1);
        });
    }
    private static void controller(GameTestHelper h, RampManager m, RampDefinition r) {
        BlockPos controller = r.origin.block().offset(24, 0, 0);
        h.getLevel().setBlockAndUpdate(controller, RampDoorMod.CONTROLLER.defaultBlockState());
        r.controller = GridPos.of(controller); m.save();
        h.getLevel().setBlockAndUpdate(controller.above(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        require(r.state == RampState.CLOSING, "Redstone rising edge did not toggle");
        h.runAfterDelay(12, () -> {
            closed(h, r);
            h.getLevel().neighborChanged(controller, Blocks.REDSTONE_BLOCK, null);
            require(r.state == RampState.CLOSED, "Held power toggled again");
            h.getLevel().setBlockAndUpdate(controller.above(), Blocks.AIR.defaultBlockState());
            h.getLevel().setBlockAndUpdate(controller.above(), Blocks.REDSTONE_BLOCK.defaultBlockState());
            h.runAfterDelay(12, () -> {
                open(h, r, m);
                rejected(() -> m.delete(r), "Deleting an open ramp was allowed");
                m.move(r, RampState.CLOSED);
                h.runAfterDelay(8, () -> {
                    closed(h, r);
                    m.delete(r); require(!m.storage.data.ramps.containsKey(r.id), "Deletion left data");
                    require(m.activeCount() == 0, "Idle animation task remains");
                    h.succeed();
                });
            });
        });
    }
}
