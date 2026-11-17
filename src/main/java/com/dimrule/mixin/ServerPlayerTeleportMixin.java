package com.dimrule.mixin;

import net.minecraft.network.protocol.game.ClientboundChangeDifficultyPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Cross-dimension teleport sends the global difficulty; correct it once the move is done. */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerTeleportMixin {
    @Inject(method = "teleport", at = @At("RETURN"))
    private void dimrule$resendDimensionDifficulty(CallbackInfoReturnable<ServerPlayer> cir) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (player.level() instanceof ServerLevel level) {
            player.connection.send(new ClientboundChangeDifficultyPacket(
                    level.getDifficulty(), level.getLevelData().isDifficultyLocked()));
        }
    }
}