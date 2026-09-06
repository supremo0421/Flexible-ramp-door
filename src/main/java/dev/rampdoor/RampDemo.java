package dev.rampdoor;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/**
 * The v0.2 test fixture: a 16 wide, 22 long cargo ramp that reads as one machined plate rather than
 * a thin sheet. Armoured centre panel, reinforced deepslate edges, two traction strips, a doubled
 * front lip and two hinge housings, 436 blocks in the palette the design brief fixes.
 *
 * <p>The reference renders are mood only. Nothing here voxelises a GLB.
 */
public final class RampDemo {
    public static final int WIDTH = 16, LENGTH = 22;
    /** Deck and understructure, relative to the ramp origin before the hinge is raised to deck level. */
    public static RampTemplate template() {
        return template(WIDTH, LENGTH);
    }
    public static RampTemplate template(int width, int length) {
        if(width<2 || width>256 || length<2 || length>256) throw new IllegalArgumentException("Width/length must be 2..256.");
        RampTemplate t = new RampTemplate();
        for (int z = 0; z < length; z++) for (int across = 0; across < width; across++) {
            int x=across-width/2;
            t.add(new GridPos(x, 0, z), deck(across, z, width));
            BlockState under = under(across, z, width, length);
            if (under != null) t.add(new GridPos(x, -1, z), under);
        }
        return t;
    }
    private static BlockState deck(int x, int z, int width) {
        if (x == 0 || x == width-1) return Blocks.POLISHED_DEEPSLATE.defaultBlockState();   // reinforced edge
        if (x == 1 || x == width-2) return Blocks.CONCRETE.lightGray().defaultBlockState(); // secondary panel
        if (x == Math.min(3,width/3) || x == width-1-Math.min(3,width/3)) return Blocks.IRON_BLOCK.defaultBlockState();
        if (z % 4 == 0) return Blocks.SMOOTH_QUARTZ.defaultBlockState();               // cross ribs
        return Blocks.CONCRETE.white().defaultBlockState();                            // armoured centre panel
    }
    private static BlockState under(int x, int z, int width, int length) {
        if (z >= length - 2) return Blocks.QUARTZ_BLOCK.defaultBlockState();           // thick front lip
        if (x == 0 || x == width-1) return Blocks.SMOOTH_STONE.defaultBlockState();         // edge beams
        if (z <= 1 && ((x >= 1 && x <= Math.min(3,width/3)) || (x >= width-1-Math.min(3,width/3) && x <= width-2)))
            return Blocks.POLISHED_DEEPSLATE.defaultBlockState();                      // hinge housings
        return null;
    }
    public static RampDefinition create(RampManager m, String id, ServerLevel level, BlockPos origin) {
        RampTemplate template = template();
        return place(m,id,level,origin,template,null);
    }
    public static RampDefinition create(RampManager m, String id, ServerLevel level, RampCoordinates.Layout layout) {
        if ((long)layout.width()*layout.length()>m.config.max_blocks_per_ramp)
            throw new IllegalArgumentException("Deck alone exceeds max_blocks_per_ramp; reduce the coordinates or raise the config limit.");
        RampTemplate mapped=new RampTemplate();
        for(var entry:template(layout.width(),layout.length()).blocks) {
            GridPos local=entry.offset();
            GridPos world=GridPos.of(layout.world(local.x()+layout.width()/2,local.y(),local.z()));
            mapped.add(world.minus(GridPos.of(layout.first())),entry.decode());
        }
        return place(m,id,level,layout.first(),mapped,layout);
    }
    private static RampDefinition place(RampManager m, String id, ServerLevel level, BlockPos origin, RampTemplate template, RampCoordinates.Layout layout) {
        id=m.availableId(id);
        GridPos base = GridPos.of(origin);
        var world = template.world(base);
        for (GridPos p : world.keySet()) {
            RampWorldIO.valid(level, p);
            if (!level.getBlockState(p.block()).isAir()) throw new IllegalArgumentException("Demo needs empty space at " + p);
            for (var r : m.storage.data.ramps.values())
                if (r.dimension.equals(level.dimension().identifier().toString()) && r.blocks().containsKey(p))
                    throw new IllegalArgumentException("Demo overlaps another ramp.");
        }
        if (template.blocks.size() > m.config.max_blocks_per_ramp) throw new IllegalArgumentException("Demo exceeds configured block limit.");
        if (level.noSave()) throw new IllegalArgumentException("Enable world saving first.");
        RampDefinition r = m.create(id, level, origin);
        r.template = template;
        // Hinge on the top face of the deck, so the walking surface pivots exactly around it.
        if(layout==null) r.hinge(origin.getX(), origin.getY() + 1.0, origin.getZ());
        else {
            r.autoAxis=false; r.axis=layout.axis(); r.extend=layout.extend();
            var hinge=layout.hinge(); r.hinge(hinge.x,hinge.y,hinge.z);
        }
        r.state = RampState.OPENING; m.storage.data.journal.add(id); m.save();
        m.recover(r); // Same durable placement path as production, ending CLOSED.
        return r;
    }
}
