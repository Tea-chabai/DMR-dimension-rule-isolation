package com.dimrule.mixin;

import com.dimrule.DimensionRules;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.WeatherData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Weather lives in one server-wide {@link WeatherData}; this hands each level its own copy. */
@Mixin(ServerLevel.class)
public abstract class ServerLevelWeatherMixin {
    @Inject(method = "getWeatherData", at = @At("HEAD"), cancellable = true)
    private void dimrule$perDimensionWeather(CallbackInfoReturnable<WeatherData> cir) {
        ServerLevel level = (ServerLevel) (Object) this;
        WeatherData data = DimensionRules.getWeather(level);
        if (data != null) {
            cir.setReturnValue(data);
        }
    }
}