package dev.rampdoor;

import com.google.gson.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;

/** Write-ahead records are forced to disk BEFORE world edits. Never silently reset corrupt data. */
public final class RampPersistentState {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final int FORMAT = 2;
    public static final class Data {
        public int format = FORMAT;
        public Map<String, RampDefinition> ramps = new LinkedHashMap<>();
        public Set<String> journal = new HashSet<>();
    }
    private final Path file;
    public final Data data;
    public RampPersistentState(Path world) throws IOException {
        file = world.resolve("data/rampdoor.json");
        JsonObject root = Files.exists(file) ? JsonParser.parseString(Files.readString(file)).getAsJsonObject() : null;
        if (root != null && root.has("format") && root.get("format").getAsInt() == 1) root = migrate(root);
        data = root == null ? new Data() : GSON.fromJson(root, Data.class);
        if (data == null || data.format != FORMAT || data.ramps == null || data.journal == null)
            throw new IOException("Invalid RampDoor save: " + file);
        for (var entry : data.ramps.entrySet()) {
            RampDefinition r = entry.getValue();
            if (r.id == null || r.origin == null || r.state == null || r.dimension == null || r.template == null
                || r.collision == null || r.easing == null || r.collisionMode == null || r.direction == null
                || !Double.isFinite(r.angle) || r.angle < 10 || r.angle > 90 || r.duration < 2 || r.duration > 1200
                || !Set.of("x", "z").contains(r.axis) || Math.abs(r.extend) != 1
                || !Set.of("down", "up").contains(r.direction) || !Set.of("linear", "ease_in_out").contains(r.easing)
                || r.collisionWidth < 0 || r.collisionLength < 0
                || !Double.isFinite(r.hingeX) || !Double.isFinite(r.hingeY) || !Double.isFinite(r.hingeZ))
                throw new IOException("Invalid ramp definition: " + entry.getKey());
            r.template.world(r.origin);
        }
        if (!data.ramps.keySet().containsAll(data.journal)) throw new IOException("Orphan RampDoor journal entry");
    }
    /**
     * v0.1 saved a CLOSED_TEMPLATE and a stair-stepped OPEN_TEMPLATE. v0.2 keeps only the closed
     * structure and rotates it, so the open capture is dropped and every ramp is journalled: the
     * next /ramp cleanup puts the real closed blocks back where the old open shape may still be.
     */
    private static JsonObject migrate(JsonObject old) throws IOException {
        JsonObject result = new JsonObject();
        result.addProperty("format", FORMAT);
        JsonObject ramps = new JsonObject();
        JsonArray journal = new JsonArray();
        JsonObject source = old.getAsJsonObject("ramps");
        if (source == null) throw new IOException("Invalid RampDoor v0.1 save");
        for (String id : source.keySet()) {
            JsonObject r = source.getAsJsonObject(id).deepCopy();
            if (r.has("closed")) { r.add("template", r.get("closed")); r.remove("closed"); }
            r.remove("open");
            double angle = r.has("angle") ? Math.abs(r.get("angle").getAsDouble()) : 32;
            r.addProperty("angle", angle >= 10 && angle <= 90 ? angle : 32);
            r.addProperty("state", RampState.CLOSED.name());
            r.add("collision", new JsonArray());
            ramps.add(id, r);
            journal.add(id);
            RampDoorMod.LOGGER.warn("Migrated ramp '{}' from the v0.1 two-template format; run /ramp cleanup {}", id, id);
        }
        result.add("ramps", ramps);
        result.add("journal", journal);
        return result;
    }
    public void save() {
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling("rampdoor.json.tmp");
            byte[] bytes = GSON.toJson(data).getBytes(StandardCharsets.UTF_8);
            try (var channel = FileChannel.open(temp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            // Atomic replace is required: if unavailable, refuse to move the structure.
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) { throw new IllegalStateException("RampDoor durable save failed; movement stopped", e); }
    }
}
