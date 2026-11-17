package com.dimrule.mixin;

import com.dimrule.DimensionRules;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gamerules.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The whole mod in one injection: 26.3's {@code ServerLevel.getGameRules()} returns the
 * server-wide rules, so return this dimension's own set instead.
 *
 * <p>Before {@code DimensionRules.bootstrap()} runs (i.e. during world construction) the lookup
 * is empty and vanilla behaviour is preserved.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelRulesMixin {
    @Inject(method = "getGameRules", at = @At("HEAD"), cancellable = true)
    private void dimrule$perDimensionRules(CallbackInfoReturnable<GameRules> cir) {
        ServerLevel level = (ServerLevel) (Object) this;
        GameRules rules = DimensionRules.get(level.dimension());
        if (rules != null) {
            cir.setReturnValue(rules);
        }
    }
}