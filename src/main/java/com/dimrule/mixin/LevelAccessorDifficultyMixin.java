package com.dimrule.mixin;

import com.dimrule.DimensionRules;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 26.3 resolves {@code getDifficulty()} through the shared level data, so every dimension reports
 * the same difficulty. This redirects server levels to their own value when one is configured.
 *
 * <p>{@code ClientLevel} is untouched by the {@code ServerLevel} guard.
 */
@Mixin(LevelAccessor.class)
public interface LevelAccessorDifficultyMixin {
    @Inject(method = "getDifficulty", at = @At("HEAD"), cancellable = true)
    private void dimrule$perDimensionDifficulty(CallbackInfoReturnable<Difficulty> cir) {
        if ((Object) this instanceof ServerLevel level) {
            Difficulty difficulty = DimensionRules.getDifficulty(level.dimension());
            if (difficulty != null) {
                cir.setReturnValue(difficulty);
            }
        }
    }
}