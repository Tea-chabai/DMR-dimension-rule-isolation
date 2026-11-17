package com.dimrule.mixin;

import com.dimrule.RecipeIsolation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The crafting grid result bypasses {@code level.recipeAccess()} and asks the server's global
 * manager directly, so route it through the dimension's own view as well.
 */
@Mixin(CraftingMenu.class)
public abstract class CraftingMenuMixin {
    @WrapOperation(
            method = "slotChangedCraftingGrid",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/MinecraftServer;getRecipeManager()Lnet/minecraft/world/item/crafting/RecipeManager;")
    )
    private static RecipeManager dimrule$perDimensionRecipes(MinecraftServer server,
                                                             Operation<RecipeManager> original,
                                                             @Local(argsOnly = true) ServerLevel level) {
        RecipeManager manager = RecipeIsolation.get(level);
        return manager != null ? manager : original.call(server);
    }
}