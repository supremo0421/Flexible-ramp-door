package dev.rampdoor;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** No ticker and no duplicate binding data: the world's RampPersistentState owns bindings. */
public final class RampControllerBlockEntity extends BlockEntity {
    public RampControllerBlockEntity(BlockPos pos, BlockState state) { super(RampDoorMod.CONTROLLER_ENTITY, pos, state); }
}
