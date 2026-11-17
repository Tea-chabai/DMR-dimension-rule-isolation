package com.dimrule.mixin;

import com.dimrule.RecipeIsolation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hands a dimension its own recipe view when it has claimed datapack namespaces. */
@Mixin(ServerLevel.class)
public abstract class ServerLevelRecipeMixin {
    @Inject(method = "recipeAccess", at = @At("HEAD"), cancellable = true)
    private void dimrule$perDimensionRecipes(CallbackInfoReturnable<RecipeManager> cir) {
        RecipeManager manager = RecipeIsolation.get((ServerLevel) (Object) this);
        if (manager != null) {
            cir.setReturnValue(manager);
        }
    }
}