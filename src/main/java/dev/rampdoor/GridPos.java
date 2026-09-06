package dev.rampdoor;

import net.minecraft.core.BlockPos;

public record GridPos(int x, int y, int z) {
    public static GridPos of(BlockPos p) { return new GridPos(p.getX(), p.getY(), p.getZ()); }
    public BlockPos block() { return new BlockPos(x, y, z); }
    public GridPos minus(GridPos p) { return new GridPos(x-p.x, y-p.y, z-p.z); }
    public GridPos plus(GridPos p) { return new GridPos(x+p.x, y+p.y, z+p.z); }
}
