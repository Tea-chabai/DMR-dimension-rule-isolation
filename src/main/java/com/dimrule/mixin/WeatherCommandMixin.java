package com.dimrule.mixin;

import com.dimrule.DimensionRules;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.commands.CommandSourceStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * {@code /weather} passes the target state to {@code MinecraftServer.setWeatherParameters}, which
 * is server-wide. Route it to the source level's own weather instead.
 *
 * <p>The three {@code WeatherCommand} helpers are static, so this handler is static as well.
 */
@Mixin(net.minecraft.server.commands.WeatherCommand.class)
public abstract class WeatherCommandMixin {
    @WrapOperation(
            method = {"setClear", "setRain", "setThunder"},
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/MinecraftServer;setWeatherParameters(IIZZ)V")
    )
    private static void dimrule$perDimensionWeather(net.minecraft.server.MinecraftServer server,
                                                    int clearTime, int rainTime,
                                                    boolean raining, boolean thundering,
                                                    Operation<Void> original,
                                                    @Local(argsOnly = true) CommandSourceStack source) {
        DimensionRules.setWeather(source.getLevel(), clearTime, rainTime, raining, thundering);
        DimensionRules.save();
    }
}