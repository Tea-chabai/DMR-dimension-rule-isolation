package com.dimrule.command;

import com.dimrule.DimensionRuleIsolation;
import com.dimrule.DimensionRules;
import com.dimrule.TimeIsolation;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;

import java.util.List;
import java.util.regex.Pattern;

/**
 * {@code /dmr} (alias {@code /dimrule}).
 */
public final class DimRuleCommand {
    private static final Pattern GROUP_NAME = Pattern.compile("[a-z0-9_-]{1,32}");
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_DIMENSIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(
                    DimensionRules.known(context.getSource().getServer()).stream()
                            .map(key -> key.identifier().toString())
                            .toList(),
                    builder);
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_GROUPS = (context, builder) ->
            SharedSuggestionProvider.suggest(DimensionRules.groups().keySet(), builder);
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_RULES = (context, builder) -> {
        ServerLevel level = resolve(context.getSource(),
                IdentifierArgument.getId(context, "dimension").toString());
        if (level == null) {
            return builder.buildFuture();
        }
        return SharedSuggestionProvider.suggest(
                level.getGameRules().availableRules().map(GameRule::id).toList(), builder);
    };

    private DimRuleCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        registerRoot(dispatcher, "dmr");
        registerRoot(dispatcher, "dimrule");
    }

    private static void registerRoot(CommandDispatcher<CommandSourceStack> dispatcher, String root) {
        var create = Commands.literal("create")
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(context -> createGroup(context)));
        var add = Commands.literal("add")
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(SUGGEST_GROUPS)
                        .then(Commands.argument("dimension", IdentifierArgument.id())
                                .suggests(SUGGEST_DIMENSIONS)
                                .executes(context -> addToGroup(context))));
        var remove = Commands.literal("remove")
                .then(Commands.argument("dimension", IdentifierArgument.id())
                        .suggests(SUGGEST_DIMENSIONS)
                        .executes(context -> removeFromGroup(context)));
        var delete = Commands.literal("delete")
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(SUGGEST_GROUPS)
                        .executes(context -> deleteGroup(context)));
        var groupList = Commands.literal("list")
                .executes(context -> listGroups(context.getSource()));
        var groupNode = Commands.literal("group")
                .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                .then(groupList)
                .then(create)
                .then(add)
                .then(remove)
                .then(delete);

        var difficultyValue = Commands.argument("value", StringArgumentType.word())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                        java.util.List.of("peaceful", "easy", "normal", "hard"), builder))
                .executes(context -> difficulty(context, true));
        var difficultyNode = Commands.literal("difficulty")
                .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                .then(Commands.argument("dimension", IdentifierArgument.id())
                        .suggests(SUGGEST_DIMENSIONS)
                        .executes(context -> difficulty(context, false))
                        .then(difficultyValue));

        var weatherAction = Commands.literal("weather")
                .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                .then(Commands.argument("dimension", IdentifierArgument.id())
                        .suggests(SUGGEST_DIMENSIONS)
                        .executes(context -> weather(context, false, -1))
                        .then(Commands.argument("action", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        java.util.List.of("clear", "rain", "thunder"), builder))
                                .executes(context -> weather(context, true, -1))
                                .then(Commands.argument("duration",
                                                com.mojang.brigadier.arguments.IntegerArgumentType.integer(0))
                                        .executes(context -> weather(context, true,
                                                com.mojang.brigadier.arguments.IntegerArgumentType
                                                        .getInteger(context, "duration"))))));

        var recipeNode = Commands.literal("recipe")
                .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                .then(Commands.literal("list")
                        .then(Commands.argument("dimension", IdentifierArgument.id())
                                .suggests(SUGGEST_DIMENSIONS)
                                .executes(context -> recipeList(context))))
                .then(Commands.literal("add")
                        .then(Commands.argument("dimension", IdentifierArgument.id())
                                .suggests(SUGGEST_DIMENSIONS)
                                .then(Commands.argument("namespace", StringArgumentType.word())
                                        .executes(context -> recipeEdit(context, true)))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("dimension", IdentifierArgument.id())
                                .suggests(SUGGEST_DIMENSIONS)
                                .then(Commands.argument("namespace", StringArgumentType.word())
                                        .executes(context -> recipeEdit(context, false)))))
                .then(Commands.literal("rebuild")
                        .executes(context -> {
                            com.dimrule.RecipeIsolation.rebuild(context.getSource().getServer());
                            context.getSource().sendSuccess(() -> Component.literal("已重建各维度的配方视图"), true);
                            return 1;
                        }))
                .then(Commands.literal("check")
                        .then(Commands.argument("dimension", IdentifierArgument.id())
                                .suggests(SUGGEST_DIMENSIONS)
                                .then(Commands.argument("id", IdentifierArgument.id())
                                        .executes(context -> recipeCheck(context)))));

        var timeNode = Commands.literal("time")
                .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                .then(Commands.argument("dimension", IdentifierArgument.id())
                        .suggests(SUGGEST_DIMENSIONS)
                        .then(Commands.literal("query").executes(context -> timeQuery(context)))
                        .then(Commands.literal("set")
                                .then(Commands.argument("value", StringArgumentType.word())
                                        .executes(context -> timeSet(context))))
                        .then(Commands.literal("add")
                                .then(Commands.argument("ticks", IntegerArgumentType.integer())
                                        .executes(context -> timeAdd(context))))
                        .then(Commands.literal("reset").executes(context -> timeReset(context))));

        var valueNode = Commands.argument("value", StringArgumentType.word())
                .executes(context -> query(context, true));
        var ruleNode = Commands.argument("rule", StringArgumentType.word())
                .suggests(SUGGEST_RULES)
                .executes(context -> query(context, false))
                .then(valueNode);
        var dimensionNode = Commands.argument("dimension", IdentifierArgument.id())
                .suggests(SUGGEST_DIMENSIONS)
                .then(ruleNode);

        dispatcher.register(Commands.literal(root)
                .then(Commands.literal("list")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(context -> listAll(context.getSource())))
                .then(Commands.literal("reload")
                        .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                        .executes(context -> reload(context.getSource())))
                .then(groupNode)
                .then(difficultyNode)
                .then(weatherAction)
                .then(recipeNode)
                .then(timeNode)
                .then(dimensionNode));
    }

    private static int listAll(CommandSourceStack source) {
        StringBuilder text = new StringBuilder("维度规则隔离");
        var groups = DimensionRules.groups();
        for (var group : groups.values()) {
            text.append("\n组 ").append(group.name()).append(": ");
            boolean first = true;
            for (ResourceKey<Level> member : group.members()) {
                if (!first) {
                    text.append(", ");
                }
                text.append(member.identifier());
                first = false;
            }
            if (group.members().isEmpty()) {
                text.append("(空)");
            }
        }
        if (!DimensionRules.ungrouped().isEmpty()) {
            text.append("\n未分组: ");
            boolean first = true;
            for (ResourceKey<Level> key : DimensionRules.ungrouped()) {
                if (!first) {
                    text.append(", ");
                }
                text.append(key.identifier());
                first = false;
            }
        }
        text.append("\n共 ").append(DimensionRules.count()).append(" 个维度 / ")
                .append(groups.size()).append(" 个组");
        StringBuilder result = text;
        source.sendSuccess(() -> Component.literal(result.toString()), false);
        return groups.size();
    }

    private static int listGroups(CommandSourceStack source) {
        var groups = DimensionRules.groups();
        StringBuilder text = new StringBuilder("分组列表");
        for (var group : groups.values()) {
            text.append("\n").append(group.name()).append(": ");
            text.append(group.members().isEmpty() ? "(空)" : group.members().stream()
                    .map(key -> key.identifier().toString())
                    .reduce((a, b) -> a + ", " + b).orElse(""));
        }
        if (groups.isEmpty()) {
            text.append("\n(还没有分组)");
        }
        StringBuilder result = text;
        source.sendSuccess(() -> Component.literal(result.toString()), false);
        return groups.size();
    }

    private static int createGroup(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "name");
        if (!GROUP_NAME.matcher(name).matches()) {
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument()
                    .createWithContext(new com.mojang.brigadier.StringReader("名字只能用小写字母/数字/_/-"));
        }
        if (!DimensionRules.createGroup(context.getSource().getServer(), name)) {
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument()
                    .createWithContext(new com.mojang.brigadier.StringReader("组已存在"));
        }
        DimensionRules.save();
        context.getSource().sendSuccess(() -> Component.literal("已创建分组 " + name), true);
        return 1;
    }

    private static int addToGroup(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        String name = StringArgumentType.getString(context, "name");
        ResourceKey<Level> key = key(IdentifierArgument.getId(context, "dimension").toString());
        if (key == null || source.getServer().getLevel(key) == null) {
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument()
                    .createWithContext(new com.mojang.brigadier.StringReader("dimension"));
        }
        if (!DimensionRules.addToGroup(source.getServer(), name, key)) {
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument()
                    .createWithContext(new com.mojang.brigadier.StringReader("already in group"));
        }
        DimensionRules.save();
        source.sendSuccess(() -> Component.literal(
                "已把 " + key.identifier() + " 加入分组 " + name), true);
        return 1;
    }

    private static int removeFromGroup(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ResourceKey<Level> key = key(IdentifierArgument.getId(context, "dimension").toString());
        if (key == null || !DimensionRules.removeFromGroup(source.getServer(), key)) {
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument()
                    .createWithContext(new com.mojang.brigadier.StringReader("not in a group"));
        }
        DimensionRules.save();
        source.sendSuccess(() -> Component.literal(
                "已把 " + key.identifier() + " 移出分组，并获得独立副本"), true);
        return 1;
    }

    private static int deleteGroup(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "name");
        if (!DimensionRules.deleteGroup(name)) {
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument()
                    .createWithContext(new com.mojang.brigadier.StringReader("no such group"));
        }
        DimensionRules.save();
        context.getSource().sendSuccess(() -> Component.literal(
                "已删除分组 " + name + "，其成员恢复为独立维度（保留当前规则）"), true);
        return 1;
    }

    private static int reload(CommandSourceStack source) {
        DimensionRules.bootstrap(source.getServer(), DimensionRuleIsolation.configPath(source.getServer()));
        source.sendSuccess(() -> Component.literal("已从磁盘重新加载规则、分组与难度"), true);
        return 1;
    }

    private static int difficulty(CommandContext<CommandSourceStack> context, boolean set)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = resolve(source, IdentifierArgument.getId(context, "dimension").toString());
        if (level == null) {
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument()
                    .createWithContext(new com.mojang.brigadier.StringReader("dimension"));
        }
        var key = level.dimension();
        if (!set) {
            var stored = DimensionRules.getDifficulty(key);
            String shown = stored != null ? stored.getSerializedName()
                    : level.getDifficulty().getSerializedName();
            String suffix = stored != null ? "" : " (vanilla default)";
            source.sendSuccess(() -> Component.literal(
                    key.identifier() + ".difficulty = " + shown + suffix), false);
            return 1;
        }
        String raw = StringArgumentType.getString(context, "value");
        net.minecraft.world.Difficulty difficulty;
        try {
            difficulty = net.minecraft.world.Difficulty.valueOf(raw.toUpperCase(java.util.Locale.ROOT));
        } catch (RuntimeException e) {
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument()
                    .createWithContext(new com.mojang.brigadier.StringReader(raw));
        }
        DimensionRules.setDifficulty(key, difficulty);
        DimensionRules.save();
        source.sendSuccess(() -> Component.literal(
                key.identifier() + ".difficulty = " + difficulty.getSerializedName()), true);
        return 1;
    }

    private static int timeQuery(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel level = resolve(context.getSource(),
                IdentifierArgument.getId(context, "dimension").toString());
        if (level == null) {
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument()
                    .createWithContext(new com.mojang.brigadier.StringReader("dimension"));
        }
        long offset = TimeIsolation.offset(level.dimension());
        long day = Math.floorMod(TimeIsolation.baseTicks(level) + offset, 24000L);
        context.getSource().sendSuccess(() -> Component.literal(
                level.dimension().identifier() + ".time offset=" + offset + " dayTime=" + day), false);
        return 1;
    }

    private static int timeSet(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel level = resolve(context.getSource(),
                IdentifierArgument.getId(context, "dimension").toString());
        if (level == null) {
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument()
                    .createWithContext(new com.mojang.brigadier.StringReader("dimension"));
        }
        String raw = StringArgumentType.getString(context, "value").toLowerCase(java.util.Locale.ROOT);
        long target = switch (raw) {
            case "day" -> 1000L;
            case "noon" -> 6000L;
            case "night" -> 13000L;
            case "midnight" -> 18000L;
            default -> {
                try {
                    long parsed = Long.parseLong(raw);
                    yield Math.floorMod(parsed, 24000L);
                } catch (NumberFormatException e) {
                    throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument()
                            .createWithContext(new com.mojang.brigadier.StringReader(raw));
                }
            }
        };
        long current = Math.floorMod(TimeIsolation.baseTicks(level), 24000L);
        long offset = Math.floorMod(target - current, 24000L);
        TimeIsolation.setOffset(level.dimension(), offset);
        DimensionRules.save();
        for (var player : level.players()) {
            TimeIsolation.sendTime(player, level);
        }
        context.getSource().sendSuccess(() -> Component.literal(
                level.dimension().identifier() + ".time set to " + target + " (offset=" + offset + ")"), true);
        return 1;
    }

    private static int timeAdd(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel level = resolve(context.getSource(),
                IdentifierArgument.getId(context, "dimension").toString());
        if (level == null) {
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument()
                    .createWithContext(new com.mojang.brigadier.StringReader("dimension"));
        }
        int ticks = IntegerArgumentType.getInteger(context, "ticks");
        long offset = TimeIsolation.offset(level.dimension()) + ticks;
        TimeIsolation.setOffset(level.dimension(), offset);
        DimensionRules.save();
        for (var player : level.players()) {
            TimeIsolation.sendTime(player, level);
        }
        context.getSource().sendSuccess(() -> Component.literal(
                level.dimension().identifier() + ".time offset " + (ticks >= 0 ? "+=" : "-=") + Math.abs(ticks)), true);
        return 1;
    }

    private static int timeReset(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel level = resolve(context.getSource(),
                IdentifierArgument.getId(context, "dimension").toString());
        if (level == null) {
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument()
                    .createWithContext(new com.mojang.brigadier.StringReader("dimension"));
        }
        TimeIsolation.setOffset(level.dimension(), 0L);
        DimensionRules.save();
        for (var player : level.players()) {
            TimeIsolation.sendTime(player, level);
        }
        context.getSource().sendSuccess(() -> Component.literal(
                level.dimension().identifier() + ".time reset to global"), true);
        return 1;
    }

    private static int recipeList(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel level = resolve(context.getSource(),
                IdentifierArgument.getId(context, "dimension").toString());
        if (level == null) {
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument()
                    .createWithContext(new com.mojang.brigadier.StringReader("dimension"));
        }
        var namespaces = DimensionRules.recipeNamespaces().get(level.dimension());
        String text = namespaces == null || namespaces.isEmpty()
                ? "无（使用全服配方）" : String.join(", ", namespaces);
        context.getSource().sendSuccess(() -> Component.literal(
                level.dimension().identifier() + ".recipes: " + text), false);
        return 1;
    }

    private static int recipeEdit(CommandContext<CommandSourceStack> context, boolean add)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = resolve(source, IdentifierArgument.getId(context, "dimension").toString());
        if (level == null) {
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument()
                    .createWithContext(new com.mojang.brigadier.StringReader("dimension"));
        }
        String namespace = StringArgumentType.getString(context, "namespace");
        java.util.Set<String> namespaces = new java.util.HashSet<>(
                DimensionRules.recipeNamespaces().getOrDefault(level.dimension(), java.util.Set.of()));
        if (add) {
            namespaces.add(namespace);
        } else {
            namespaces.remove(namespace);
        }
        DimensionRules.setRecipeNamespaces(level.dimension(), namespaces);
        DimensionRules.save();
        try {
            com.dimrule.RecipeIsolation.rebuild(source.getServer());
        } catch (Throwable t) {
            source.sendFailure(Component.literal("rebuild failed: " + t));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                level.dimension().identifier() + ".recipes " + (add ? "+= " : "-= ") + namespace), true);
        return 1;
    }

    private static int recipeCheck(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel level = resolve(context.getSource(),
                IdentifierArgument.getId(context, "dimension").toString());
        if (level == null) {
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument()
                    .createWithContext(new com.mojang.brigadier.StringReader("dimension"));
        }
        String id = IdentifierArgument.getId(context, "id").toString();
        ResourceKey<net.minecraft.world.item.crafting.Recipe<?>> key;
        try {
            key = ResourceKey.create(Registries.RECIPE, Identifier.parse(id));
        } catch (RuntimeException e) {
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument()
                    .createWithContext(new com.mojang.brigadier.StringReader(id));
        }
        boolean present = com.dimrule.RecipeIsolation.has(level, key);
        context.getSource().sendSuccess(() -> Component.literal(
                level.dimension().identifier() + " sees " + id + ": " + present), false);
        return present ? 1 : 0;
    }

    private static int weather(CommandContext<CommandSourceStack> context, boolean set, int duration)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = resolve(source, IdentifierArgument.getId(context, "dimension").toString());
        if (level == null) {
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument()
                    .createWithContext(new com.mojang.brigadier.StringReader("dimension"));
        }
        var key = level.dimension();
        if (!set) {
            var data = DimensionRules.getWeather(key);
            String text = data == null ? "无（使用全服天气）"
                    : "raining=" + data.isRaining() + ", thundering=" + data.isThundering()
                    + ", clear=" + data.getClearWeatherTime() + ", rain=" + data.getRainTime()
                    + ", thunder=" + data.getThunderTime();
            source.sendSuccess(() -> Component.literal(key.identifier() + ".weather: " + text), false);
            return 1;
        }
        String action = StringArgumentType.getString(context, "action");
        int clear = 0;
        int rain = 0;
        boolean raining = false;
        boolean thundering = false;
        switch (action) {
            case "clear" -> clear = duration >= 0 ? duration : ServerLevel.RAIN_DELAY.sample(level.getRandom());
            case "rain" -> {
                rain = duration >= 0 ? duration : ServerLevel.RAIN_DURATION.sample(level.getRandom());
                raining = true;
            }
            case "thunder" -> {
                rain = duration >= 0 ? duration : ServerLevel.THUNDER_DURATION.sample(level.getRandom());
                raining = true;
                thundering = true;
            }
            default -> throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument()
                    .createWithContext(new com.mojang.brigadier.StringReader(action));
        }
        DimensionRules.setWeather(level, clear, rain, raining, thundering);
        DimensionRules.save();
        source.sendSuccess(() -> Component.literal(key.identifier() + ".weather = " + action), true);
        return 1;
    }

    private static int query(CommandContext<CommandSourceStack> context, boolean set) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = resolve(source, IdentifierArgument.getId(context, "dimension").toString());
        if (level == null) {
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument()
                    .createWithContext(new com.mojang.brigadier.StringReader("dimension"));
        }
        GameRules rules = level.getGameRules();
        String ruleId = StringArgumentType.getString(context, "rule");
        GameRule<?> rule = find(rules, ruleId);
        if (rule == null) {
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument()
                    .createWithContext(new com.mojang.brigadier.StringReader(ruleId));
        }
        if (set) {
            String value = StringArgumentType.getString(context, "value");
            if (!apply(rules, rule, value)) {
                throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument()
                        .createWithContext(new com.mojang.brigadier.StringReader(value));
            }
            DimensionRules.save();
        }
        String shown = serialize(rules, rule);
        source.sendSuccess(() -> Component.literal(
                level.dimension().identifier() + "." + rule.id() + " = " + shown), true);
        return 1;
    }

    private static ResourceKey<Level> key(String name) {
        String id = name.contains(":") ? name : "minecraft:" + name;
        try {
            return ResourceKey.create(Registries.DIMENSION, Identifier.parse(id));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static ServerLevel resolve(CommandSourceStack source, String name) {
        ResourceKey<Level> key = key(name);
        return key == null ? null : source.getServer().getLevel(key);
    }

    private static GameRule<?> find(GameRules rules, String id) {
        return rules.availableRules().filter(rule -> rule.id().equals(id)).findFirst().orElse(null);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String serialize(GameRules rules, GameRule rule) {
        return rule.serialize(rules.get(rule));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean apply(GameRules rules, GameRule rule, String value) {
        var parsed = rule.deserialize(value).result();
        if (parsed.isEmpty()) {
            return false;
        }
        rules.set(rule, parsed.get(), null);
        return true;
    }

    private static List<String> groupNames() {
        return List.copyOf(DimensionRules.groups().keySet());
    }
}