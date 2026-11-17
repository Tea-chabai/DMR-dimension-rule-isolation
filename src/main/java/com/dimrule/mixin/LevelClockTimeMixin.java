package com.dimrule.mixin;

import com.dimrule.TimeIsolation;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies the dimension's time offset wherever the default clock time is read. */
@Mixin(Level.class)
public abstract class LevelClockTimeMixin {
    @Inject(method = "getDefaultClockTime", at = @At("HEAD"), cancellable = true)
    private void dimrule$offsetDefaultClockTime(CallbackInfoReturnable<Long> cir) {
        Level level = (Level) (Object) this;
        long offset = TimeIsolation.offset(level.dimension());
        if (offset != 0L) {
            cir.setReturnValue(TimeIsolation.baseTicks(level) + offset);
        }
    }
}