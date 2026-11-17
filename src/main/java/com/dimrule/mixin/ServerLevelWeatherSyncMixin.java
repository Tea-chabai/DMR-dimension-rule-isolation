package com.dimrule.mixin;

import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Vanilla broadcasts weather state changes to every player because weather used to be global.
 * With per-dimension weather the START/STOP and rain-level packets must stay inside the
 * dimension that changed, otherwise players elsewhere hear rain that never falls for them.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelWeatherSyncMixin {
    @Redirect(
            method = "advanceWeatherCycle",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/players/PlayerList;broadcastAll(Lnet/minecraft/network/protocol/Packet;)V")
    )
    private void dimrule$broadcastWeatherToDimension(PlayerList playerList, Packet<?> packet) {
        playerList.broadcastAll(packet, ((ServerLevel) (Object) this).dimension());
    }
}