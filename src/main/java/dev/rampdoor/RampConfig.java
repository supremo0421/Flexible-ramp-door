package dev.rampdoor;

import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.*;
import java.io.IOException;

public final class RampConfig {
    public int max_blocks_per_ramp = 512;
    public int max_collision_blocks_per_ramp = 2048;
    public int max_simultaneous_animations = 4;
    public int default_duration_ticks = 40;
    public double default_open_angle = 32;
    public int max_selection_volume = 262144;
    public static RampConfig load() {
        var gson = new GsonBuilder().setPrettyPrinting().create();
        Path file = FabricLoader.getInstance().getConfigDir().resolve("rampdoor.json");
        try {
            RampConfig c = Files.exists(file) ? gson.fromJson(Files.readString(file), RampConfig.class) : new RampConfig();
            if (c == null || c.max_blocks_per_ramp < 1 || c.max_blocks_per_ramp > 16384
                || c.max_collision_blocks_per_ramp < 1 || c.max_collision_blocks_per_ramp > 65536
                || c.max_simultaneous_animations < 1 || c.max_simultaneous_animations > 64
                || c.default_duration_ticks < 2 || c.default_duration_ticks > 1200
                || !Double.isFinite(c.default_open_angle) || c.default_open_angle < 10 || c.default_open_angle > 90
                || c.max_selection_volume < 1 || c.max_selection_volume > 16777216)
                throw new IOException("Invalid rampdoor.json limits");
            if (!Files.exists(file)) { Files.createDirectories(file.getParent()); Files.writeString(file, gson.toJson(c)); }
            return c;
        } catch (IOException | RuntimeException e) { throw new IllegalStateException("Cannot read RampDoor config", e); }
    }
}
