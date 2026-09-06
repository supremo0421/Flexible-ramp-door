package dev.rampdoor;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import java.util.*;
import java.io.IOException;

/**
 * Owns the ramp state machine.
 *
 * <pre>
 * CLOSED   real blocks, no displays, no collision
 * OPENING  displays rotating, real blocks removed
 * OPEN     displays frozen at the open angle + invisible collision, no tick work
 * CLOSING  collision removed, displays rotating back
 * </pre>
 */
public final class RampManager {
    public final MinecraftServer server;
    public final RampConfig config;
    public final RampPersistentState storage;
    private final Map<String, RampAnimator> active = new LinkedHashMap<>();
    /** Displays held by ramps that are standing open; static, never ticked. */
    private final Map<String, RampDisplayManager> presentations = new LinkedHashMap<>();
    private record Lease(ServerLevel level, ChunkPos chunk) {}
    private final Map<Lease, Integer> leases = new HashMap<>();
    public final Map<UUID, Selection> selections = new HashMap<>();
    public final Map<UUID, String> pendingBindings = new HashMap<>();
    public static final class Selection { public String dimension; public BlockPos first, second; }

    public RampManager(MinecraftServer server, RampConfig config) throws IOException {
        this.server = server; this.config = config;
        storage = new RampPersistentState(server.getWorldPath(LevelResource.ROOT));
    }
    public RampDefinition get(String id) {
        RampDefinition r = storage.data.ramps.get(id);
        if (r == null) throw new IllegalArgumentException("Unknown ramp '" + id + "'.");
        return r;
    }
    public ServerLevel level(RampDefinition r) {
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(r.dimension)));
        if (level == null) throw new IllegalArgumentException("Ramp dimension is unavailable: " + r.dimension);
        return level;
    }
    public void editable(RampDefinition r) {
        if (r.state.moving() || storage.data.journal.contains(r.id))
            throw new IllegalArgumentException("Ramp is moving or requires /ramp cleanup " + r.id);
    }
    public void save() { storage.save(); }
    public Map<GridPos, BlockState> collisionPlan(RampDefinition r) {
        return RampCollisionSurface.plan(r, config.max_collision_blocks_per_ramp);
    }

    public RampDefinition create(String id, ServerLevel level, BlockPos origin) {
        id=availableId(id);
        RampDefinition r = new RampDefinition(); r.id = id; r.dimension = level.dimension().identifier().toString();
        r.origin = GridPos.of(origin); r.hingeX = origin.getX(); r.hingeY = origin.getY(); r.hingeZ = origin.getZ();
        r.duration = config.default_duration_ticks; r.angle = config.default_open_angle;
        storage.data.ramps.put(id, r); save(); return r;
    }
    public String availableId(String requested) {
        if (!requested.matches("[a-z0-9_-]{1,64}")) throw new IllegalArgumentException("ID must be 1-64 lowercase letters, digits, _ or -.");
        if (!storage.data.ramps.containsKey(requested)) return requested;
        for (int n=2; n<Integer.MAX_VALUE; n++) {
            String suffix="_"+n;
            String candidate=requested.substring(0,Math.min(requested.length(),64-suffix.length()))+suffix;
            if (!storage.data.ramps.containsKey(candidate)) return candidate;
        }
        throw new IllegalArgumentException("No free ramp ID.");
    }
    /** Includes virtual closed footprints: opening one ramp must not steal a neighbour's home. */
    private Set<GridPos> ownedByOthers(RampDefinition r) {
        Set<GridPos> occupied=new HashSet<>();
        for (var other:storage.data.ramps.values()) {
            if (other==r || other.id.equals(r.id) || !other.dimension.equals(r.dimension)) continue;
            occupied.addAll(other.blocks().keySet()); occupied.addAll(other.collision);
            var animation=active.get(other.id);
            if(animation!=null) occupied.addAll(animation.footprint);
        }
        return occupied;
    }
    private void exclusive(RampDefinition r, Collection<GridPos> cells) {
        Set<GridPos> others=ownedByOthers(r);
        for(GridPos p:cells) if(others.contains(p))
            throw new IllegalArgumentException("Ramp '"+r.id+"' overlaps another ramp's reserved blocks/collision at "+p);
    }
    public void select(UUID player, ServerLevel level, BlockPos pos, boolean first) {
        Selection s = selections.computeIfAbsent(player, ignored -> new Selection());
        String dim = level.dimension().identifier().toString();
        if (!dim.equals(s.dimension)) { s.first = null; s.second = null; s.dimension = dim; }
        if (first) s.first = pos.immutable(); else s.second = pos.immutable();
    }
    /** Captures the single RAMP_TEMPLATE: the closed structure exactly as it stands in the world. */
    public int capture(RampDefinition r, UUID player) {
        editable(r);
        if (r.state != RampState.CLOSED) throw new IllegalArgumentException("Close the ramp before capturing.");
        Selection s = selections.get(player);
        if (s == null || s.first == null || s.second == null || !r.dimension.equals(s.dimension))
            throw new IllegalArgumentException("Select pos1 and pos2 in the ramp dimension first.");
        return capture(r, s.first, s.second);
    }
    /** Direct coordinates also work from a command block or server console; no player selection needed. */
    public int capture(RampDefinition r, BlockPos first, BlockPos second) {
        editable(r);
        if (r.state != RampState.CLOSED) throw new IllegalArgumentException("Close the ramp before capturing.");
        long volume = 1;
        for (long extent : new long[]{Math.abs((long) first.getX() - second.getX()) + 1,
            Math.abs((long) first.getY() - second.getY()) + 1, Math.abs((long) first.getZ() - second.getZ()) + 1}) {
            if (extent > config.max_selection_volume / volume) throw new IllegalArgumentException("Selection exceeds max_selection_volume.");
            volume *= extent;
        }
        RampTemplate result = new RampTemplate();
        ServerLevel level = level(r);
        for (BlockPos bp : BlockPos.betweenClosed(first, second)) {
            GridPos p = GridPos.of(bp); RampWorldIO.valid(level, p);
            var state = level.getBlockState(bp);
            if (state.isAir()) continue;
            RampWorldIO.supported(state, p);
            for (RampDefinition other : storage.data.ramps.values()) {
                if (other == r || !other.dimension.equals(r.dimension)) continue;
                if (other.blocks().containsKey(p)) throw new IllegalArgumentException("Selection overlaps ramp '" + other.id + "' at " + p);
            }
            result.add(p.minus(r.origin), state);
            if (result.blocks.size() > config.max_blocks_per_ramp) throw new IllegalArgumentException("Ramp exceeds max_blocks_per_ramp.");
        }
        if (result.blocks.isEmpty()) throw new IllegalArgumentException("Selection contains no blocks.");
        r.template = result;
        if (r.autoAxis) r.autoOrient();
        save(); return result.blocks.size();
    }

    private Set<Lease> needed(RampDefinition r) {
        ServerLevel level = level(r); Set<Lease> result = new HashSet<>();
        var positions = new HashSet<>(r.blocks().keySet());
        positions.addAll(r.collision);
        try { positions.addAll(collisionPlan(r).keySet()); } catch (RuntimeException ignored) { /* limits are reported by the caller */ }
        positions.add(GridPos.of(BlockPos.containing(r.hingeX, r.hingeY, r.hingeZ)));
        for (GridPos p : positions) result.add(new Lease(level, new ChunkPos(p.x() >> 4, p.z() >> 4)));
        return result;
    }
    private void acquire(RampDefinition r, boolean load) {
        Set<Lease> needed = needed(r);
        if (!load) for (Lease l : needed) if (!l.level.hasChunk(l.chunk.x(), l.chunk.z()))
            throw new IllegalArgumentException("Required ramp chunk is not loaded: " + l.chunk);
        for (Lease l : needed) {
            if (leases.merge(l, 1, Integer::sum) == 1) l.level.getChunkSource().addTicketWithRadius(RampDoorMod.TICKET, l.chunk, 2);
            if (load) l.level.getChunk(l.chunk.x(), l.chunk.z());
        }
    }
    private void release(RampDefinition r) {
        for (Lease l : needed(r)) {
            Integer count = leases.get(l);
            if (count == null) continue;
            if (count == 1) { leases.remove(l); l.level.getChunkSource().removeTicketWithRadius(RampDoorMod.TICKET, l.chunk, 2); }
            else leases.put(l, count - 1);
        }
    }

    public String move(RampDefinition r, RampState target) {
        if (r.state.moving()) return "Ramp is already moving; request ignored.";
        editable(r);
        if (!r.ready()) throw new IllegalArgumentException("Capture the ramp template first: /ramp capture " + r.id);
        if (r.state == target) return "Ramp is already " + target + ".";
        if (target != RampState.OPEN && target != RampState.CLOSED) throw new IllegalArgumentException("Only OPEN and CLOSED can be requested.");
        if (active.size() >= config.max_simultaneous_animations) throw new IllegalArgumentException("Too many simultaneous animations.");
        ServerLevel level = level(r);
        if (level.noSave()) throw new IllegalArgumentException("Ramp cannot move while world saving is disabled.");
        RampAnimator animation = new RampAnimator(level, r, target, collisionPlan(r));
        exclusive(r,animation.footprint);
        for (RampAnimator other : active.values()) if (other.level == level && !Collections.disjoint(other.footprint, animation.footprint))
            throw new IllegalArgumentException("Ramp overlaps an active animation.");
        if (target == RampState.OPEN) {
            RampWorldIO.intact(level, animation.blocks);
            RampWorldIO.clearance(level, animation.collision.keySet(), animation.blocks.keySet());
        } else {
            blockedByEntity(level, r);
            RampWorldIO.clearance(level, animation.blocks.keySet(), Set.of());
        }
        acquire(r, false);
        try {
            r.state = target == RampState.OPEN ? RampState.OPENING : RampState.CLOSING;
            storage.data.journal.add(r.id);
            save(); // WAL barrier: nothing in the world has changed yet.
            active.put(r.id, animation);
            if (target == RampState.OPEN) {
                animation.displays = new RampDisplayManager(level, r, 0);
                RampWorldIO.clear(level, animation.blocks.keySet());
            } else {
                // Collision goes first so nobody is standing on a surface that is about to move.
                RampWorldIO.clearCollision(level, collisionCells(r, animation.collision));
                r.collision = new ArrayList<>();
                // An open ramp's chunks are free to unload, which leaves the cached entities stale.
                RampDisplayManager displays = presentations.remove(r.id);
                if (displays != null && !displays.alive()) { displays.close(); displays = null; }
                if (displays == null) {
                    cleanup(level, r.displayTag());
                    displays = new RampDisplayManager(level, r, animation.openDegrees);
                }
                animation.displays = displays;
            }
            save();
        } catch (RuntimeException error) {
            if (animation.displays != null) animation.displays.close();
            active.remove(r.id);
            try { recover(r); } catch (RuntimeException recovery) { error.addSuppressed(recovery); }
            release(r); throw error;
        }
        return (target == RampState.OPEN ? "Opening " : "Closing ") + r.id + "...";
    }
    public String toggle(RampDefinition r) { return move(r, r.state == RampState.OPEN ? RampState.CLOSED : RampState.OPEN); }

    /** Refuses to close on top of a player or mob anywhere in the volume the ramp sweeps through. */
    private void blockedByEntity(ServerLevel level, RampDefinition r) {
        var box = RampCollisionSurface.sweptBounds(r).inflate(0.05);
        var blockers = level.getEntitiesOfClass(LivingEntity.class, box, e -> !e.isSpectator() && e.isAlive());
        if (!blockers.isEmpty())
            throw new IllegalArgumentException("Ramp cannot close: entity obstructing movement (" + blockers.size() + " in the ramp path).");
    }
    private Set<GridPos> collisionCells(RampDefinition r, Map<GridPos, BlockState> plan) {
        Set<GridPos> cells = new LinkedHashSet<>(r.collision);
        cells.addAll(plan.keySet());
        // Collision blocks have no block entity owner. Do not treat every invisible block as ours.
        cells.removeAll(ownedByOthers(r));
        return cells;
    }
    public boolean reserved(ServerLevel level, BlockPos p) {
        for (RampAnimator a : active.values()) if (a.level == level && a.footprint.contains(GridPos.of(p))) return true;
        return false;
    }
    public void tick() {
        if (active.isEmpty()) return;
        for (RampAnimator a : List.copyOf(active.values())) {
            try { if (a.tick()) finish(a); }
            catch (RuntimeException e) {
                RampDoorMod.LOGGER.error("Ramp {} halted; recovery journal retained", a.ramp.id, e);
                a.displays.close(); active.remove(a.ramp.id);
                try { recover(a.ramp); } catch (RuntimeException recovery) { RampDoorMod.LOGGER.error("Manual /ramp cleanup required", recovery); }
                release(a.ramp);
            }
        }
    }
    private void finish(RampAnimator a) {
        RampDefinition r = a.ramp;
        if (a.to == RampState.OPEN) {
            a.displays.settle(a.openDegrees);
            r.collision = RampWorldIO.placeCollision(a.level, a.collision);
            presentations.put(r.id, a.displays);
        } else {
            // Also catches direct chunk edits by tools that bypass Level.setBlock.
            for (GridPos p : a.blocks.keySet()) {
                var state = a.level.getBlockState(p.block());
                if (!state.isAir() && !state.is(RampDoorMod.COLLISION))
                    throw new IllegalStateException("Closing footprint was externally modified at " + p);
            }
            RampWorldIO.place(a.level, a.blocks);
            a.displays.close();
            r.collision = new ArrayList<>();
        }
        // Flush chunk storage BEFORE clearing the WAL. A crash at any earlier point rolls back.
        a.level.getChunkSource().save(true);
        r.state = a.to; storage.data.journal.remove(r.id);
        try { save(); } catch (RuntimeException e) {
            r.state = a.to == RampState.OPEN ? RampState.OPENING : RampState.CLOSING;
            storage.data.journal.add(r.id); throw e;
        }
        active.remove(r.id); release(r);
    }

    /**
     * Reconciles the world with the ramp's safe endpoint: OPENING falls back to CLOSED, CLOSING to
     * OPEN. Also rebuilds an open ramp's displays and collision after a restart.
     */
    public void recover(RampDefinition r) {
        if (active.containsKey(r.id)) throw new IllegalArgumentException("Wait for the active animation.");
        acquire(r, true);
        try {
            ServerLevel level = level(r);
            if (level.noSave()) throw new IllegalArgumentException("Enable saving before recovery.");
            RampState safe = r.state.safeEndpoint();
            var blocks = r.blocks();
            exclusive(r,blocks.keySet());
            for (var e : blocks.entrySet()) {
                var actual = level.getBlockState(e.getKey().block());
                if (!actual.isAir() && !actual.equals(e.getValue()) && !actual.is(RampDoorMod.COLLISION))
                    throw new IllegalArgumentException("Recovery blocked by unrelated block at " + e.getKey() + "; remove it, then /ramp cleanup " + r.id);
            }
            var plan = collisionPlan(r);
            if(safe==RampState.OPEN) exclusive(r,plan.keySet());
            RampDisplayManager stale = presentations.remove(r.id);
            if (stale != null) stale.close();
            cleanup(level, r.displayTag());
            // Both the recorded cells and the cells this ramp would use: a stale record cannot orphan one.
            RampWorldIO.clearCollision(level, collisionCells(r, plan));
            r.collision = new ArrayList<>();
            RampWorldIO.clear(level, blocks.keySet());
            if (safe == RampState.OPEN) {
                presentations.put(r.id, new RampDisplayManager(level, r, RampGeometry.worldDegrees(r)));
                r.collision = RampWorldIO.placeCollision(level, plan);
            } else {
                RampWorldIO.place(level, blocks);
            }
            level.getChunkSource().save(true);
            RampState prior = r.state; r.state = safe; storage.data.journal.remove(r.id);
            try { save(); } catch (RuntimeException e) { r.state = prior; storage.data.journal.add(r.id); throw e; }
        } finally { release(r); }
    }
    public void recoverAll() {
        for (ServerLevel level : server.getAllLevels()) cleanup(level, RampDisplayManager.TAG);
        for (RampDefinition r : storage.data.ramps.values()) {
            if (!storage.data.journal.contains(r.id) && !r.state.moving() && r.state != RampState.OPEN) continue;
            try { recover(r); } catch (RuntimeException e) { RampDoorMod.LOGGER.error("Ramp {} recovery blocked", r.id, e); }
        }
    }
    public static void cleanup(ServerLevel level, String tag) {
        var leftovers = new ArrayList<net.minecraft.world.entity.Entity>();
        for (var e : level.getAllEntities()) if (e.entityTags().contains(tag)) leftovers.add(e);
        leftovers.forEach(net.minecraft.world.entity.Entity::discard);
    }
    public void delete(RampDefinition r) {
        editable(r);
        if (r.state != RampState.CLOSED)
            throw new IllegalArgumentException("Close the ramp before deleting it; while open its blocks only exist as displays.");
        ServerLevel level = level(r);
        RampDisplayManager displays = presentations.remove(r.id);
        if (displays != null) displays.close();
        cleanup(level, r.displayTag());
        RampWorldIO.clearCollision(level, collisionCells(r,Map.of()));
        storage.data.ramps.remove(r.id); storage.data.journal.remove(r.id);
        pendingBindings.values().removeIf(r.id::equals); save();
    }
    public void stop() {
        for (RampAnimator a : List.copyOf(active.values())) {
            a.displays.close(); active.remove(a.ramp.id);
            try { recover(a.ramp); }
            catch (RuntimeException e) { RampDoorMod.LOGGER.error("Shutdown recovery deferred for {}", a.ramp.id, e); }
            finally { release(a.ramp); }
        }
        save();
    }
    /**
     * Displays only survive while their ramp still owns them; every other tagged display is a
     * leftover. The generation tag is rotated whenever displays are spawned, so an older generation
     * arriving with a late chunk load is never mistaken for the live one. This is checked against the
     * definitions rather than the live maps because entities are validated while they spawn, before
     * the animation or presentation is registered.
     */
    public boolean ownsDisplay(String tag) {
        for (RampDefinition r : storage.data.ramps.values())
            if (r.state != RampState.CLOSED && r.entityTag.equals(tag)) return true;
        return false;
    }
    public int activeCount() { return active.size(); }
    public int displayCount(RampDefinition r) {
        RampAnimator a = active.get(r.id);
        if (a != null && a.displays != null) return a.displays.size();
        RampDisplayManager displays = presentations.get(r.id);
        return displays == null ? 0 : displays.size();
    }
    public int chunkLeaseCount() { return leases.size(); }
    public String debug(RampDefinition r) {
        ServerLevel level = level(r);
        int collisionInWorld = 0;
        for (GridPos p : r.collision) if (level.hasChunk(p.x() >> 4, p.z() >> 4) && level.getBlockState(p.block()).is(RampDoorMod.COLLISION)) collisionInWorld++;
        var box = RampCollisionSurface.bounds(r, RampGeometry.radians(r));
        return String.join("\n",
            "Ramp " + r.id + " [" + r.dimension + "]",
            "  state=" + r.state + " blocks=" + r.template.blocks.size() + " displays=" + displayCount(r)
                + " collision=" + collisionInWorld + "/" + r.collision.size(),
            "  hinge=" + r.hingeX + "," + r.hingeY + "," + r.hingeZ + " axis=" + r.axis + " extend=" + r.extend
                + " direction=" + r.direction,
            "  angle=" + r.angle + " duration=" + r.duration + "t easing=" + r.easing
                + " collisionMode=" + r.collisionMode + " width=" + r.collisionWidth + " length=" + r.collisionLength,
            "  controller=" + r.controller + " journal=" + storage.data.journal.contains(r.id) + " walkable=" + RampCollisionSurface.walkable(r),
            "  open bounds=" + String.format("%.1f,%.1f,%.1f .. %.1f,%.1f,%.1f", box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ),
            "  chunks=" + new ChunkPos((int) Math.floor(box.minX) >> 4, (int) Math.floor(box.minZ) >> 4)
                + " .. " + new ChunkPos((int) Math.floor(box.maxX) >> 4, (int) Math.floor(box.maxZ) >> 4));
    }
}
