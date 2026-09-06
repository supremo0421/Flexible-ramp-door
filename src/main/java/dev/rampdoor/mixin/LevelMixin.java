package dev.rampdoor.mixin;

import dev.rampdoor.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class LevelMixin {
    @Inject(method="setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at=@At("HEAD"), cancellable=true)
    private void rampdoor$reserve(BlockPos pos, BlockState state, int flags, int recursion, CallbackInfoReturnable<Boolean> cir) {
        if (RampWorldIO.isInternalWrite()) return;
        if ((Object)this instanceof ServerLevel level) {
            var manager=RampDoorMod.manager(level.getServer());
            if (manager != null && manager.reserved(level,pos)) cir.setReturnValue(false);
        }
    }
}
