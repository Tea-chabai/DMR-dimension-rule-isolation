package com.dimrule.mixin;

import com.dimrule.TimeIsolation;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.clock.ClockManager;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The attribute system samples timeline layers (monsters burn, villager activity, sky darkening)
 * from {@code level.clockManager()}. Route that through the offset wrapper so gameplay matches the
 * time the dimension shows.
 */
@Mixin(EnvironmentAttributeSystem.class)
public abstract class EnvironmentAttributeClockMixin {
    @Redirect(
            method = "addDynamicLayers",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;clockManager()Lnet/minecraft/world/clock/ClockManager;")
    )
    private static ClockManager dimrule$offsetClock(Level level) {
        return TimeIsolation.wrap(level);
    }
}