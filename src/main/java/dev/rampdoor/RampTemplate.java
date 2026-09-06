package dev.rampdoor;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

public final class RampTemplate {
    public record Entry(GridPos offset, JsonElement blockState) {
        public BlockState decode() { return BlockState.CODEC.parse(JsonOps.INSTANCE, blockState).getOrThrow(); }
    }
    public List<Entry> blocks = new ArrayList<>();
    public void add(GridPos offset, BlockState state) {
        blocks.add(new Entry(offset, BlockState.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow()));
    }
    public Map<GridPos, BlockState> world(GridPos origin) {
        Map<GridPos, BlockState> result = new LinkedHashMap<>();
        for (Entry e : blocks) {
            if (result.put(e.offset.plus(origin), e.decode()) != null) throw new IllegalStateException("Duplicate template position");
        }
        return result;
    }
    public void rebase(GridPos oldOrigin, GridPos newOrigin) {
        blocks = new ArrayList<>(blocks.stream().map(e -> new Entry(e.offset.plus(oldOrigin).minus(newOrigin), e.blockState)).toList());
    }
}
