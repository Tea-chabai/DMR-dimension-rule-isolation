package com.dimrule.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Fixes an asymmetry that only shows up with per-dimension rules.
 *
 * <p>{@code ServerPlayer.die()} decides whether to drop loot from the rules of the dimension the
 * player died in, but {@code restoreFrom()} decides whether to copy the inventory from the rules
 * of the <em>respawn</em> dimension. Died in a {@code keep_inventory=true} group and respawned in
 * a {@code keep_inventory=false} overworld, the items were neither dropped nor copied — they
 * vanished. Here the copy decision uses the death dimension's rules, which matches the drop
 * decision.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerRespawnMixin {
    @WrapOperation(
            method = "restoreFrom",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/gamerules/GameRules;get(Lnet/minecraft/world/level/gamerules/GameRule;)Ljava/lang/Object;")
    )
    private Object dimrule$useDeathDimensionRules(GameRules rules, GameRule<?> rule,
                                                  Operation<Object> original, ServerPlayer oldPlayer) {
        if (rule == GameRules.KEEP_INVENTORY && oldPlayer != null) {
            GameRules deathRules = oldPlayer.level().getGameRules();
            if (deathRules != null) {
                return deathRules.get(GameRules.KEEP_INVENTORY);
            }
        }
        return original.call(rules, rule);
    }
}