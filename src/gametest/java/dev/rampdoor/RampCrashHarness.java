package dev.rampdoor;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import java.nio.file.*;
import java.util.*;

/** Test source set only. Explicit opt-in aborts ONLY this disposable GameTest JVM without shutdown hooks. */
public final class RampCrashHarness implements ModInitializer {
    private boolean executed;
    @Override public void onInitialize() {
        String phase = System.getProperty("rampdoor.crashPhase", "");
        if (phase.isEmpty()) return;
        if (!Set.of("prepare_open", "verify_open", "prepare_close", "verify_close").contains(phase)) throw new IllegalArgumentException("Unknown crash test phase");
        ServerTickEvents.START_SERVER_TICK.register(server -> {
            if (executed) return;
            executed = true;
            var m = RampDoorMod.manager(server);
            if (m == null) throw new IllegalStateException("Ramp manager unavailable");
            var level = server.overworld();
            boolean opening = phase.endsWith("_open");
            String id = "hard_crash_" + (opening ? "open" : "close");
            try {
                if (phase.startsWith("prepare_")) {
                    BlockPos base = new BlockPos(opening ? 1000 : 1100, 96, 1000);
                    for (int x = (base.getX() - 32) >> 4; x <= (base.getX() + 48) >> 4; x++)
                        for (int z = (base.getZ() - 32) >> 4; z <= (base.getZ() + 48) >> 4; z++) level.getChunk(x, z);
                    RampDemo.create(m, id, level, base);
                    RampDefinition r = m.get(id);
                    if (!opening) { r.duration = 2; m.save(); m.move(r, RampState.OPEN); for (int i = 0; i < 8; i++) m.tick(); }
                    r.duration = 1200; m.save();
                    m.move(r, opening ? RampState.OPEN : RampState.CLOSED);
                    // Persist the physically missing blocks and temporary entities, not just the WAL.
                    server.saveEverything(false, true, true);
                    Files.writeString(Path.of("crash-" + phase + ".txt"), "Prepared " + r.state + "; pid=" + ProcessHandle.current().pid());
                    Runtime.getRuntime().halt(137);
                } else {
                    RampDefinition r = m.get(id);
                    RampState expected = opening ? RampState.CLOSED : RampState.OPEN;
                    if (r.state != expected || m.storage.data.journal.contains(id)) throw new IllegalStateException("Crash endpoint was not recovered: " + r.state);
                    var blocks = r.blocks();
                    for (var e : blocks.entrySet()) {
                        level.getChunk(e.getKey().x() >> 4, e.getKey().z() >> 4);
                        var actual = level.getBlockState(e.getKey().block());
                        // Open: the cells near the hinge legitimately hold the walking surface.
                        boolean ok = expected == RampState.CLOSED ? actual.equals(e.getValue())
                            : actual.isAir() || actual.is(RampDoorMod.COLLISION);
                        if (!ok) throw new IllegalStateException("Crash recovery mismatch at " + e.getKey() + ": " + actual);
                    }
                    long displays = 0;
                    for (var e : level.getAllEntities()) if (e.entityTags().contains(r.displayTag())) displays++;
                    long wanted = expected == RampState.CLOSED ? 0 : r.template.blocks.size();
                    if (displays != wanted) throw new IllegalStateException("Recovered ramp has " + displays + " displays, expected " + wanted);
                    for (GridPos p : r.collision)
                        if (!level.getBlockState(p.block()).is(RampDoorMod.COLLISION)) throw new IllegalStateException("Recovered collision missing at " + p);
                    if (expected == RampState.OPEN && r.collision.isEmpty()) throw new IllegalStateException("Recovered open ramp has no walking surface");
                    Files.writeString(Path.of("crash-" + phase + ".txt"),
                        "PASS: fresh JVM restored " + expected + "; " + r.template.blocks.size() + " blocks; "
                            + displays + " displays; " + r.collision.size() + " collision blocks; journal cleared.");
                    RampDoorMod.LOGGER.info("HARD CRASH TEST PASS: {}", phase);
                    if (expected == RampState.OPEN) { r.duration = 2; m.save(); m.move(r, RampState.CLOSED); for (int i = 0; i < 8; i++) m.tick(); }
                    m.delete(r);
                }
            } catch (Exception e) { throw new IllegalStateException("Hard-crash harness failed at " + phase, e); }
        });
    }
}
