package com.dimrule.mixin;

import com.dimrule.TimeIsolation;
import net.minecraft.network.protocol.game.ClientboundChangeDifficultyPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla sends the shared (global) difficulty to the client, so the HUD kept showing the server
 * value even in dimensions with their own difficulty. Send the dimension's effective value after
 * the vanilla packet, and the offset clock at the same time.
 */
@Mixin(PlayerList.class)
public abstract class PlayerListTimeMixin {
    @Inject(method = "sendLevelInfo", at = @At("TAIL"))
    private void dimrule$sendPerDimensionState(ServerPlayer player, ServerLevel level, CallbackInfo ci) {
        player.connection.send(new ClientboundChangeDifficultyPacket(
                level.getDifficulty(), level.getLevelData().isDifficultyLocked()));
        TimeIsolation.sendTime(player, level);
    }
}