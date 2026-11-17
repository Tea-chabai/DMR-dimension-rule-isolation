package com.dimrule.command;

import com.dimrule.DimensionRuleIsolation;
import com.dimrule.DimensionRules;
import com.mojang.brigadier.CommandDispatcher;
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
        source.sendSuccess(() -> Component.literal("已从磁盘重新加载规则与分组"), true);
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