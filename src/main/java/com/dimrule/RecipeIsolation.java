package com.dimrule;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Per-dimension recipe isolation by <b>namespace ownership</b>.
 *
 * <p>Each dimension may claim one or more datapack namespaces (e.g. {@code room_a} owns
 * {@code room_a:*}). A recipe whose namespace is claimed by some dimension is visible only in
 * that dimension; recipes from unclaimed namespaces ({@code minecraft}, mods, shared packs) stay
 * visible everywhere.
 *
 * <p>Every dimension with claims gets its own {@link RecipeManager} built from a filtered
 * registry view, and {@code ServerLevel.recipeAccess()} is redirected to it.
 */
public final class RecipeIsolation {
    private static final Map<ResourceKey<Level>, RecipeManager> MANAGERS = new ConcurrentHashMap<>();
    private static final Map<ResourceKey<Level>, Set<String>> CLAIMS = new ConcurrentHashMap<>();

    private RecipeIsolation() {
    }

    /** Rebuilds every per-dimension manager from the current server registries. */
    public static void rebuild(MinecraftServer server) {
        MANAGERS.clear();
        CLAIMS.clear();
        CLAIMS.putAll(DimensionRules.recipeNamespaces());
        if (CLAIMS.isEmpty()) {
            return;
        }
        Set<String> owned = new HashSet<>();
        CLAIMS.values().forEach(owned::addAll);
        // This provider already concatenates the static context with the reloadable layer
        // (recipes, loot, ...), so it must not be merged with registryAccess() again.
        HolderLookup.Provider base = server.reloadableRegistries().lookup();

        // Namespaces nobody claimed stay visible everywhere.
        Set<String> unowned = new HashSet<>();
        base.lookupOrThrow(Registries.RECIPE).listElementIds().forEach(id -> {
            String namespace = id.identifier().getNamespace();
            if (!owned.contains(namespace)) {
                unowned.add(namespace);
            }
        });

        // Every dimension gets a view, including ones without claims: without this they would
        // fall back to the global manager and see recipes claimed by other dimensions.
        for (ServerLevel level : server.getAllLevels()) {
            Set<String> claims = CLAIMS.getOrDefault(level.dimension(), Set.of());
            Set<String> visible = new HashSet<>(unowned);
            visible.addAll(claims);
            try {
                HolderLookup.Provider filtered = new FilteredProvider(base, visible);
                RecipeManager manager = new RecipeManager(filtered);
                manager.finalizeRecipeLoading(server.getWorldData().enabledFeatures());
                MANAGERS.put(level.dimension(), manager);
            } catch (Exception e) {
                DimensionRuleIsolation.LOG.error("Failed to build recipe manager for {}", level.dimension(), e);
            }
        }
        DimensionRuleIsolation.LOG.info("Recipe isolation built for {} dimension(s)", MANAGERS.size());
    }

    /** The dimension's own manager, or {@code null} when it has no recipe claims. */
    public static RecipeManager get(ServerLevel level) {
        return MANAGERS.get(level.dimension());
    }

    public static boolean has(ServerLevel level, ResourceKey<Recipe<?>> id) {
        RecipeManager manager = MANAGERS.get(level.dimension());
        return manager != null && manager.byKey(id).isPresent();
    }

    /** Visible namespaces for a dimension, or {@code null} when it has no claims. */
    public static Set<String> claims(ResourceKey<Level> dimension) {
        return CLAIMS.get(dimension);
    }

    private record FilteredProvider(HolderLookup.Provider base, Set<String> visible)
            implements HolderLookup.Provider {
        @Override
        public Stream<ResourceKey<? extends Registry<?>>> listRegistryKeys() {
            return this.base.listRegistryKeys();
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        @Override
        public <T> Optional<? extends HolderLookup.RegistryLookup<T>> lookup(
                ResourceKey<? extends Registry<? extends T>> key) {
            Optional<? extends HolderLookup.RegistryLookup<T>> found = this.base.lookup(key);
            if (!key.equals(Registries.RECIPE)) {
                return found;
            }
            return found.map(lookup -> (HolderLookup.RegistryLookup<T>) new FilteredRecipeLookup(
                    (HolderLookup.RegistryLookup<Recipe<?>>) lookup, this.visible));
        }
    }

    /** Filters a recipe lookup by the id namespace of each entry. */
    private record FilteredRecipeLookup(HolderLookup.RegistryLookup<Recipe<?>> base, Set<String> visible)
            implements HolderLookup.RegistryLookup<Recipe<?>> {
        @Override
        public ResourceKey<? extends Registry<? extends Recipe<?>>> key() {
            return this.base.key();
        }

        @Override
        public com.mojang.serialization.Lifecycle registryLifecycle() {
            return this.base.registryLifecycle();
        }

        @Override
        public Stream<Holder.Reference<Recipe<?>>> listElements() {
            return this.base.listElements()
                    .filter(holder -> this.visible.contains(holder.key().identifier().getNamespace()));
        }

        @Override
        public Optional<Holder.Reference<Recipe<?>>> get(ResourceKey<Recipe<?>> id) {
            return this.visible.contains(id.identifier().getNamespace())
                    ? this.base.get(id)
                    : Optional.empty();
        }

        @Override
        public Optional<HolderSet.Named<Recipe<?>>> get(TagKey<Recipe<?>> tag) {
            return this.base.get(tag);
        }

        @Override
        public Stream<HolderSet.Named<Recipe<?>>> listTags() {
            return this.base.listTags();
        }
    }
}