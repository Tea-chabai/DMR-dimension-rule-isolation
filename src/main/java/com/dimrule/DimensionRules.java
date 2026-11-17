package com.dimrule;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-dimension game rules with named <em>groups</em>.
 *
 * <p>Every dimension has its own rule set unless it is added to a group; all members of a group
 * share one {@link GameRules} instance, so a change made anywhere in the group is seen everywhere.
 * A dimension belongs to at most one group.
 *
 * <p>Persisted to {@code config/dimensional-rule-isolation/dimension_rules.json}:
 * <pre>{@code
 * {
 *   "dimensions": { "<id>": { "<rule>": "<value>" } },
 *   "groups": { "pvp": { "members": ["dimtest:room_a", "dimtest:room_b"], "rules": { ... } } }
 * }
 * }</pre>
 * The legacy flat format and the old {@code links} array are still read.
 */
public final class DimensionRules {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Map<String, Group> GROUPS = new ConcurrentHashMap<>();
    private static final Map<ResourceKey<Level>, String> MEMBER_OF = new ConcurrentHashMap<>();
    private static final Map<ResourceKey<Level>, GameRules> OWN = new ConcurrentHashMap<>();
    private static final Map<ResourceKey<Level>, net.minecraft.world.Difficulty> DIFFICULTY = new ConcurrentHashMap<>();
    private static final Map<ResourceKey<Level>, net.minecraft.world.level.saveddata.WeatherData> WEATHER =
            new ConcurrentHashMap<>();
    private static final Map<ResourceKey<Level>, java.util.Set<String>> RECIPE_NAMESPACES = new ConcurrentHashMap<>();
    private static final Map<ResourceKey<Level>, Long> TIME = new ConcurrentHashMap<>();
    private static Path configFile;

    private DimensionRules() {
    }

    public static final class Group {
        private final String name;
        private final Set<ResourceKey<Level>> members = new LinkedHashSet<>();
        private final GameRules rules;

        private Group(String name, GameRules rules) {
            this.name = name;
            this.rules = rules;
        }

        public String name() {
            return this.name;
        }

        public Set<ResourceKey<Level>> members() {
            return java.util.Collections.unmodifiableSet(this.members);
        }

        public GameRules rules() {
            return this.rules;
        }
    }

    /**
     * Reads the parts of the config that must be known <b>before</b> any level exists, so the
     * difficulty redirect already answers with the per-dimension value while chunks load.
     * Rules and weather still need a live level list and are handled by {@link #bootstrap}.
     */
    public static void preload(Path file) {
        DIFFICULTY.clear();
        RECIPE_NAMESPACES.clear();
        JsonObject stored = asObject(read(file));
        if (stored.has("difficulty") && stored.get("difficulty").isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : stored.getAsJsonObject("difficulty").entrySet()) {
                ResourceKey<Level> key = parse(entry.getKey());
                if (key == null) {
                    continue;
                }
                try {
                    DIFFICULTY.put(key, net.minecraft.world.Difficulty.valueOf(
                            entry.getValue().getAsString().toUpperCase(java.util.Locale.ROOT)));
                } catch (RuntimeException ignored) {
                    // unknown value: keep vanilla behaviour for that dimension
                }
            }
        }
        if (stored.has("recipes") && stored.get("recipes").isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : stored.getAsJsonObject("recipes").entrySet()) {
                ResourceKey<Level> key = parse(entry.getKey());
                if (key == null || !entry.getValue().isJsonArray()) {
                    continue;
                }
                java.util.Set<String> namespaces = ConcurrentHashMap.newKeySet();
                entry.getValue().getAsJsonArray().forEach(value -> namespaces.add(value.getAsString()));
                if (!namespaces.isEmpty()) {
                    RECIPE_NAMESPACES.put(key, namespaces);
                }
            }
        }
        DimensionRuleIsolation.LOG.info("Preloaded difficulty for {} dimension(s) before level creation",
                DIFFICULTY.size());
    }

    public static void bootstrap(MinecraftServer server, Path file) {
        configFile = file;
        GROUPS.clear();
        MEMBER_OF.clear();
        OWN.clear();
        DIFFICULTY.clear();
        WEATHER.clear();
        RECIPE_NAMESPACES.clear();
        TIME.clear();
        com.dimrule.TimeIsolation.clear();
        JsonObject stored = asObject(read(file));

        JsonObject dimensions = stored.has("dimensions") && stored.get("dimensions").isJsonObject()
                ? stored.getAsJsonObject("dimensions")
                : stored;
        for (Map.Entry<String, JsonElement> entry : dimensions.entrySet()) {
            ResourceKey<Level> key = parse(entry.getKey());
            if (key == null) {
                continue;
            }
            GameRules rules = new GameRules(server.getWorldData().enabledFeatures());
            apply(rules, entry.getValue().isJsonObject()
                    ? GSON.fromJson(entry.getValue(), new TypeToken<Map<String, String>>() {
                    }.getType())
                    : null);
            OWN.put(key, rules);
        }

        if (stored.has("groups") && stored.get("groups").isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : stored.getAsJsonObject("groups").entrySet()) {
                if (!entry.getValue().isJsonObject()) {
                    continue;
                }
                JsonObject groupJson = entry.getValue().getAsJsonObject();
                GameRules rules = new GameRules(server.getWorldData().enabledFeatures());
                apply(rules, groupJson.has("rules") && groupJson.get("rules").isJsonObject()
                        ? GSON.fromJson(groupJson.get("rules"), new TypeToken<Map<String, String>>() {
                        }.getType())
                        : null);
                Group group = new Group(entry.getKey(), rules);
                if (groupJson.has("members") && groupJson.get("members").isJsonArray()) {
                    for (JsonElement member : groupJson.getAsJsonArray("members")) {
                        ResourceKey<Level> key = parse(member.getAsString());
                        if (key != null) {
                            group.members.add(key);
                            MEMBER_OF.put(key, entry.getKey());
                            OWN.remove(key);
                        }
                    }
                }
                GROUPS.put(entry.getKey(), group);
            }
        } else if (stored.has("links") && stored.get("links").isJsonArray()) {
            // Legacy: ["member", "root"] pairs become one group per pair.
            int index = 0;
            for (JsonElement element : stored.getAsJsonArray("links")) {
                if (!element.isJsonArray() || element.getAsJsonArray().size() != 2) {
                    continue;
                }
                ResourceKey<Level> member = parse(element.getAsJsonArray().get(0).getAsString());
                ResourceKey<Level> root = parse(element.getAsJsonArray().get(1).getAsString());
                if (member == null || root == null) {
                    continue;
                }
                GameRules rules = OWN.remove(root);
                if (rules == null) {
                    rules = new GameRules(server.getWorldData().enabledFeatures());
                }
                Group group = new Group("link_" + (++index), rules);
                group.members.add(root);
                MEMBER_OF.put(root, group.name);
                group.members.add(member);
                MEMBER_OF.put(member, group.name);
                OWN.remove(member);
                GROUPS.put(group.name, group);
            }
        }

        if (stored.has("difficulty") && stored.get("difficulty").isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : stored.getAsJsonObject("difficulty").entrySet()) {
                ResourceKey<Level> key = parse(entry.getKey());
                if (key == null) {
                    continue;
                }
                try {
                    DIFFICULTY.put(key, net.minecraft.world.Difficulty.valueOf(
                            entry.getValue().getAsString().toUpperCase(java.util.Locale.ROOT)));
                } catch (RuntimeException ignored) {
                    // unknown value: keep vanilla behaviour for that dimension
                }
            }
        }

        for (ServerLevel level : server.getAllLevels()) {
            if (!MEMBER_OF.containsKey(level.dimension())) {
                OWN.computeIfAbsent(level.dimension(),
                        key -> new GameRules(server.getWorldData().enabledFeatures()));
            }
            net.minecraft.world.level.saveddata.WeatherData global = server.getWeatherData();
            WEATHER.put(level.dimension(), new net.minecraft.world.level.saveddata.WeatherData(
                    global.getClearWeatherTime(), global.getRainTime(), global.getThunderTime(),
                    global.isRaining(), global.isThundering()));
        }

        if (stored.has("weather") && stored.get("weather").isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : stored.getAsJsonObject("weather").entrySet()) {
                ResourceKey<Level> key = parse(entry.getKey());
                if (key == null || !entry.getValue().isJsonObject()) {
                    continue;
                }
                JsonObject w = entry.getValue().getAsJsonObject();
                WEATHER.put(key, new net.minecraft.world.level.saveddata.WeatherData(
                        w.get("clear").getAsInt(), w.get("rain").getAsInt(),
                        w.get("thunder").getAsInt(), w.get("raining").getAsBoolean(),
                        w.get("thundering").getAsBoolean()));
            }
        }

        if (stored.has("recipes") && stored.get("recipes").isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : stored.getAsJsonObject("recipes").entrySet()) {
                ResourceKey<Level> key = parse(entry.getKey());
                if (key == null || !entry.getValue().isJsonArray()) {
                    continue;
                }
                java.util.Set<String> namespaces = ConcurrentHashMap.newKeySet();
                entry.getValue().getAsJsonArray().forEach(value -> namespaces.add(value.getAsString()));
                if (!namespaces.isEmpty()) {
                    RECIPE_NAMESPACES.put(key, namespaces);
                }
            }
        }

        if (stored.has("time") && stored.get("time").isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : stored.getAsJsonObject("time").entrySet()) {
                ResourceKey<Level> key = parse(entry.getKey());
                if (key == null) {
                    continue;
                }
                try {
                    long ticks = entry.getValue().getAsLong();
                    TIME.put(key, ticks);
                    com.dimrule.TimeIsolation.setOffset(key, ticks);
                } catch (RuntimeException ignored) {
                    // malformed entry: keep vanilla time for that dimension
                }
            }
        }

        DimensionRuleIsolation.LOG.info("Initialised {} dimension(s), {} group(s) from {}",
                count(), GROUPS.size(), file);
    }

    public static GameRules get(ResourceKey<Level> key) {
        String groupName = MEMBER_OF.get(key);
        if (groupName != null) {
            Group group = GROUPS.get(groupName);
            if (group != null) {
                return group.rules;
            }
        }
        return OWN.get(key);
    }

    public static int count() {
        int total = OWN.size();
        for (Group group : GROUPS.values()) {
            total += group.members.size();
        }
        return total;
    }

    public static net.minecraft.world.Difficulty getDifficulty(ResourceKey<Level> key) {
        return DIFFICULTY.get(key);
    }

    public static void setDifficulty(ResourceKey<Level> key, net.minecraft.world.Difficulty difficulty) {
        DIFFICULTY.put(key, difficulty);
    }

    public static net.minecraft.world.level.saveddata.WeatherData getWeather(ResourceKey<Level> key) {
        return WEATHER.get(key);
    }

    public static net.minecraft.world.level.saveddata.WeatherData getWeather(ServerLevel level) {
        return WEATHER.get(level.dimension());
    }

    public static void setWeather(ServerLevel level, int clearTime, int rainTime,
                                  boolean raining, boolean thundering) {
        net.minecraft.world.level.saveddata.WeatherData data =
                WEATHER.computeIfAbsent(level.dimension(),
                        key -> new net.minecraft.world.level.saveddata.WeatherData());
        data.setClearWeatherTime(clearTime);
        data.setRainTime(rainTime);
        data.setThunderTime(rainTime);
        data.setRaining(raining);
        data.setThundering(thundering);
    }

    public static Map<ResourceKey<Level>, java.util.Set<String>> recipeNamespaces() {
        return java.util.Collections.unmodifiableMap(RECIPE_NAMESPACES);
    }

    public static void setRecipeNamespaces(ResourceKey<Level> key, java.util.Set<String> namespaces) {
        if (namespaces.isEmpty()) {
            RECIPE_NAMESPACES.remove(key);
        } else {
            java.util.Set<String> copy = ConcurrentHashMap.newKeySet();
            copy.addAll(namespaces);
            RECIPE_NAMESPACES.put(key, copy);
        }
    }

    public static Map<String, Group> groups() {
        return java.util.Collections.unmodifiableMap(GROUPS);
    }

    public static Set<ResourceKey<Level>> ungrouped() {
        return java.util.Collections.unmodifiableSet(OWN.keySet());
    }

    public static String groupOf(ResourceKey<Level> key) {
        return MEMBER_OF.get(key);
    }

    public static boolean createGroup(MinecraftServer server, String name) {
        if (GROUPS.containsKey(name)) {
            return false;
        }
        GROUPS.put(name, new Group(name, new GameRules(server.getWorldData().enabledFeatures())));
        return true;
    }

    /** Adds a dimension to a group, creating the group when needed and moving it out of any other. */
    public static boolean addToGroup(MinecraftServer server, String name, ResourceKey<Level> key) {
        Group target = GROUPS.get(name);
        if (target == null) {
            target = new Group(name, new GameRules(server.getWorldData().enabledFeatures()));
            GROUPS.put(name, target);
        }
        if (target.members.contains(key)) {
            return false;
        }
        String previous = MEMBER_OF.get(key);
        if (previous != null) {
            Group old = GROUPS.get(previous);
            if (old != null) {
                old.members.remove(key);
                if (old.members.isEmpty()) {
                    GROUPS.remove(previous);
                }
            }
        } else {
            GameRules own = OWN.remove(key);
            // seed the group with the dimension's current values the first time
            if (own != null && target.members.isEmpty()) {
                apply(target.rules, snapshot(own));
            }
        }
        target.members.add(key);
        MEMBER_OF.put(key, name);
        return true;
    }

    /** Detaches a dimension with a private copy of the group's current values. */
    public static boolean removeFromGroup(MinecraftServer server, ResourceKey<Level> key) {
        String name = MEMBER_OF.remove(key);
        if (name == null) {
            return false;
        }
        Group group = GROUPS.get(name);
        if (group == null) {
            return false;
        }
        GameRules copy = new GameRules(server.getWorldData().enabledFeatures());
        apply(copy, snapshot(group.rules));
        OWN.put(key, copy);
        group.members.remove(key);
        if (group.members.isEmpty()) {
            GROUPS.remove(name);
        }
        return true;
    }

    public static boolean deleteGroup(String name) {
        Group group = GROUPS.remove(name);
        if (group == null) {
            return false;
        }
        for (ResourceKey<Level> member : group.members) {
            MEMBER_OF.remove(member);
            OWN.put(member, group.rules);
        }
        group.members.clear();
        return true;
    }

    public static void save() {
        if (configFile == null) {
            return;
        }
        JsonObject out = new JsonObject();
        JsonObject dimensions = new JsonObject();
        OWN.forEach((key, rules) -> dimensions.add(key.identifier().toString(), GSON.toJsonTree(snapshot(rules))));
        out.add("dimensions", dimensions);
        JsonObject groups = new JsonObject();
        GROUPS.forEach((name, group) -> {
            JsonObject json = new JsonObject();
            JsonArray members = new JsonArray();
            group.members.forEach(member -> members.add(member.identifier().toString()));
            json.add("members", members);
            json.add("rules", GSON.toJsonTree(snapshot(group.rules)));
            groups.add(name, json);
        });
        out.add("groups", groups);
        JsonObject difficulty = new JsonObject();
        DIFFICULTY.forEach((key, value) ->
                difficulty.addProperty(key.identifier().toString(), value.getSerializedName()));
        out.add("difficulty", difficulty);
        JsonObject weather = new JsonObject();
        WEATHER.forEach((key, data) -> {
            JsonObject w = new JsonObject();
            w.addProperty("clear", data.getClearWeatherTime());
            w.addProperty("rain", data.getRainTime());
            w.addProperty("thunder", data.getThunderTime());
            w.addProperty("raining", data.isRaining());
            w.addProperty("thundering", data.isThundering());
            weather.add(key.identifier().toString(), w);
        });
        out.add("weather", weather);
        JsonObject recipes = new JsonObject();
        RECIPE_NAMESPACES.forEach((key, namespaces) -> {
            var array = new com.google.gson.JsonArray();
            namespaces.forEach(array::add);
            recipes.add(key.identifier().toString(), array);
        });
        out.add("recipes", recipes);
        JsonObject time = new JsonObject();
        com.dimrule.TimeIsolation.offsets().forEach((key, ticks) ->
                time.addProperty(key.identifier().toString(), ticks));
        out.add("time", time);
        try {
            Files.createDirectories(configFile.getParent());
            try (Writer writer = Files.newBufferedWriter(configFile, StandardCharsets.UTF_8)) {
                GSON.toJson(out, writer);
            }
        } catch (Exception e) {
            DimensionRuleIsolation.LOG.error("Failed to save {}", configFile, e);
        }
    }

    public static Map<String, String> snapshot(GameRules rules) {
        Map<String, String> out = new LinkedHashMap<>();
        rules.availableRules().forEach(rule -> out.put(rule.id(), serialize(rules, rule)));
        return out;
    }

    private static ResourceKey<Level> parse(String id) {
        try {
            return ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                    net.minecraft.resources.Identifier.parse(id));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static JsonObject asObject(JsonElement element) {
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
    }

    private static JsonElement read(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, JsonElement.class);
        } catch (Exception e) {
            DimensionRuleIsolation.LOG.error("Failed to read {}", file, e);
            return null;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String serialize(GameRules rules, GameRule rule) {
        return rule.serialize(rules.get(rule));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void apply(GameRules rules, Map<String, String> values) {
        if (values == null) {
            return;
        }
        for (Map.Entry<String, String> entry : values.entrySet()) {
            GameRule<?> found = rules.availableRules()
                    .filter(rule -> rule.id().equals(entry.getKey()))
                    .findFirst()
                    .orElse(null);
            if (found != null) {
                GameRule raw = (GameRule) found;
                raw.deserialize(entry.getValue()).result()
                        .ifPresent(parsed -> rules.set(raw, parsed, null));
            }
        }
    }

    public static List<ResourceKey<Level>> known(MinecraftServer server) {
        List<ResourceKey<Level>> keys = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            keys.add(level.dimension());
        }
        return keys;
    }
}