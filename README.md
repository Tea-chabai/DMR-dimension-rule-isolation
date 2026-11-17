# Dimensional Rule Isolation / 维度规则隔离

> **AI 参与声明**
> 本项目的代码、文档与图标由 **AI 编码代理（DeepSeek Harness）** 生成；
> **QiLin_Z** 提供需求与设计想法，并完成全部游戏内测试与验收。
> 提交到 GitHub / Modrinth 时请保留此声明。

让 **每个维度拥有独立的 `/gamerule`、难度、天气和配方**（Minecraft 26.3，Fabric 服务端模组）。
原版三界（主世界/下界/末地）和任何**数据包定义的维度**都各自一套规则，互不影响；
还可以把维度放进**命名分组**，同一组的维度共享一套规则。

服务端安装即可，原版客户端可直接进服（本模组不新增注册表内容、不发送自定义数据包）。

## 为什么需要它

26.3 把 gamerules 移到了服务器层：`ServerLevel.getGameRules()` 直接返回全服规则，
所以 `/gamerule keep_inventory true` 会同时影响主世界、下界、末地。

本模组在 `getGameRules()` 入口做一次重定向：每个维度（或每个分组）返回自己的 `GameRules` 实例。
原版 `/gamerule` 命令本身就会读写 `source.getLevel().getGameRules()`，因此**不用改命令**，
在哪个维度执行就改哪个维度。

## 安装

1. Fabric Loader ≥ 0.19.5，Fabric API（26.3 对应版本），Java 25
2. 把 `dimension-rule-isolation-0.1.2-beta.jar` 放进服务端 `mods/`
3. 客户端不需要装

## 命令

| 命令 | 权限 | 作用 |
|---|---|---|
| `/dmr list` | 2 | 列出所有分组与未分组维度 |
| `/dmr group list` | 3 | 只看分组 |
| `/dmr group create <名字>` | 3 | 创建一个空分组（小写字母/数字/`_`/`-`） |
| `/dmr group add <名字> <维度>` | 3 | 把维度加入分组（自动创建组；从旧组移出） |
| `/dmr group remove <维度>` | 3 | 移出分组，并获得当前规则的独立副本 |
| `/dmr group delete <名字>` | 3 | 删除分组，成员恢复独立（保留当前规则） |
| `/dmr <维度> <规则> [值]` | 3 | 跨维度读写规则（控制台用） |
| `/dmr difficulty <维度> [值]` | 3 | 查看/设置该维度难度（peaceful/easy/normal/hard） |
| `/dmr weather <维度> [clear\|rain\|thunder] [时长]` | 3 | 查看/设置该维度天气（玩法同 `/weather`） |
| `/dmr recipe <维度> add\|remove <命名空间>` | 3 | 认领/取消该维度的配方命名空间 |
| `/dmr recipe list <维度>` / `rebuild` / `check <维度> <配方id>` | 3 | 查看/重建/检查配方视图 |
| `/dmr time <维度> query` | 3 | 查看该维度时间偏移与当前时刻 |
| `/dmr time <维度> set <day\|noon\|night\|midnight\|0-23999>` | 3 | 把该维度时间设到指定时刻（相对全局偏移） |
| `/dmr time <维度> add <tick>` / `reset` | 3 | 偏移增减 / 取消偏移 |
| `/dmr reload` | 3 | 从磁盘重载规则与分组 |

`/dimrule` 是 `/dmr` 的完整别名。

示例：

```text
/dmr group create pvp
/dmr group add pvp dimtest:room_a
/dmr group add pvp dimtest:room_b
/dmr group list
/dmr group remove dimtest:room_b
/dmr group delete pvp

/gamerule keep_inventory true          # 在玩家所在维度（或所在分组）生效
/dmr minecraft:the_nether keep_inventory true
```

## 分组语义

- 每个维度默认独立；
- 同一组的维度共享**同一个** `GameRules` 实例，组内任何维度改规则，全组同步；
- 一个维度最多属于一个组，`add` 到新组会自动从旧组移出；
- `remove` 时复制组当前值给该维度，之后互不影响；
- `delete` 后成员各自独立，保留删除时的规则值；
- 分组与规则一起持久化，重启后保留。

## 持久化

`<服务端>/config/dimensional-rule-isolation/dimension_rules.json`

```json
{
  "dimensions": {
    "minecraft:overworld": { "keep_inventory": "false", "...": "..." }
  },
  "groups": {
    "pvp": {
      "members": ["dimtest:room_a", "dimtest:room_b"],
      "rules": { "keep_inventory": "true", "...": "..." }
    }
  },
  "difficulty": {
    "dimtest:room_a": "peaceful",
    "dimtest:room_b": "hard"
  },
  "weather": {
    "dimtest:room_a": { "clear": 0, "rain": 5991, "thunder": 5991, "raining": true, "thundering": false }
  },
  "recipes": {
    "dimtest:room_a": ["room_a"],
    "dimtest:room_b": ["room_b"]
  },
  "time": {
    "dimtest:room_a": 7603
  }
}
```

旧版纯 `维度 -> 规则` 映射、以及早期 `links` 数组仍可读取（自动迁移）。

## 实测结果（2026-11-17，runServer + RCON，全新存档）

- 6 个维度初始化：overworld / the_nether / the_end / dimtest:room_a / room_b / room_c；
- 主世界 `mob_griefing=false` → 其它维度仍为 `true`（独立）；
- `group create pvp` + `group add pvp room_a` + `group add pvp room_b` 后，在 room_a 改
  `keep_inventory=true`，room_b 同步为 true，room_c 仍 false；
- `group remove room_b` 后，room_a 改 false，room_b 保持 true（副本独立）；
- `group delete pvp` 后 `dmr list` 显示 `共 6 个维度 / 0 个组`；
- `/dmr difficulty dimtest:room_a peaceful` 后，在该维度 `summon zombie` 被拒绝
  （`Monsters cannot be summoned in Peaceful difficulty`），room_b 设为 normal 后可正常召唤；
- `/dmr weather dimtest:room_a rain 6000` 后 room_a 下雨、room_b 保持晴天；
  `execute in dimtest:room_b run weather rain 6000` 只让 room_b 下雨，room_a 用 `clear` 后不影响 room_b；
- 难度与天气都写入 `dimension_rules.json` 的 `difficulty` / `weather` 段，重启后恢复；
- 配方隔离：`room_a` 认领命名空间 `room_a`、`room_b` 认领 `room_b` 后，
  `room_a` 只看到 `room_a:special`，看不到 `room_b:special`（反向亦然），
  未认领的 `minecraft:*` 配方在所有维度都可见；
- **未认领任何命名空间的维度（主世界/下界/末地）也会得到过滤视图**：只看到没人认领的配方，
  看不到任何维度认领的命名空间；
- 时间是**偏移版**：显示时间 = 全局时钟 + 该维度偏移，`getDefaultClockTime()` 与环境属性
  （亡灵燃烧、村民作息、天空变暗）都按偏移后的时间计算；睡眠跳夜仍走全局时钟；
- 难度在 `SERVER_STARTING` 阶段**先于关卡创建**预载（日志 `Preloaded difficulty ... before level creation`
  出现在 `Preparing level` 之前），因此区块加载时逐维度难度已经生效——全局难度为 peaceful 时，
  normal 维度里保存的敌对生物不会被误清（实测 `Skipping Entity` 从 9 条降为 0）；
- 天气状态变更包按维度过滤（`START_RAINING`/`STOP_RAINING` 不再全服广播），其他维度不会听到雨声；
- 分维度时间（偏移版）：`/dmr time dimtest:room_a set night` 后 `query` 返回 `offset=7603 dayTime=13021`，
  `add 1200` 后 `dayTime=14281`，`reset` 回到全局；主世界 `offset=0` 不受影响；
- 分维度难度：`dimtest:room_b` 设为 `hard` 后可在其中召唤僵尸，主世界（peaceful）拒绝召唤；
- 日志：当前版本启动与运行 **0 ERROR / 0 Exception**；

## 删除模组是否安全？

**安全，不会破坏存档。** 2026-11-17 实测：用 Fabric 官方启动器起了一个**零模组**的
26.3 服务端，把本模组跑过的世界原样拷过去：

- 启动 `Done (0.291s)`，6 个维度（含 3 个数据包维度 `dimtest:room_a/b/c`）全部加载；
- `/dmr` 变成未知命令（符合预期）；
- 所有维度的 `keep_inventory` 回到原版全局值（分组里的 `true` 不再生效）；
- 在 `dimtest:room_a` 里 `forceload` + `setblock` + `if block` 全部成功；
- 无 crash-report、无 ERROR。完整日志见 `docs/removal-test.log`。

原因：模组**不向世界写入任何数据**——没有方块/物品/实体/维度注册项，也不修改 `level.dat`。
唯一的持久化是 `config/dimensional-rule-isolation/dimension_rules.json`，它在存档之外。

删除后会发生什么：

- 世界正常加载，区块、玩家数据、数据包维度都不受影响；
- 所有维度回退到原版全局 gamerule（`level.dat` 里那份）；
- 分组/逐维度差异消失——这是功能失效，不是存档损坏；
- 如果有命令方块调用了 `/dmr`，会报未知命令，需要手动清理；
- 重新装回模组后，只要 `config` 里的 JSON 还在，规则与分组会自动恢复。

## 已知限制

- **时间是偏移版，不是独立时钟**：显示时间 = 全局时钟 + 偏移；睡眠跳夜仍会推进全局时钟，
  原版 `/time` 命令仍操作全局，逐维度时间请用 `/dmr time`。
- **原版 `/difficulty` 仍是全服**：逐维度难度用 `/dmr difficulty` 设置；客户端 HUD 已按维度同步。
- **配方隔离覆盖工作台/熔炉/石切机/锻造台/酿造台**；配方书与 `/recipe` 仍列出全部命名空间，
  但实际合成校验按维度。
- **战利品表、进度、物品/方块标签仍是全局**（这些查询没有维度上下文可路由）。
- 原版改规则时会向所有玩家广播该规则的新值（客户端预测用），不影响服务端各维度实际取值。
- 与「MultiWorld Core」类模组同时安装会重复接管 `getGameRules()`，**不要同时装**。

## 跨维度死亡（已修复）

原版死亡与复活用了**两套不同的规则来源**：

- `ServerPlayer.die()` 用**死亡维度**的规则决定是否掉落；
- `ServerPlayer.restoreFrom()` 用**复活目标维度**的规则决定是否复制物品栏。

单维度时两者相同，逐维度规则下就会出现：在 `keep_inventory=true` 的组里死亡、复活回
`keep_inventory=false` 的主世界 → 既不掉落也不复制，物品直接消失。

模组通过 `ServerPlayerRespawnMixin` 让复活复制**以死亡维度的规则为准**，与掉落判断一致。

## 更新日志

详见 [CHANGELOG.md](CHANGELOG.md)。

### 0.1.2-beta

- 新增逐维度难度、天气、配方（命名空间认领）、时间偏移；
- 修复客户端难度 HUD 显示全局值、天气包全服广播、难度晚于区块加载导致 mob 被清；
- 难度数据改为关卡创建前预载。

## 构建

```bash
./gradlew build
# 产物: build/libs/dimension-rule-isolation-0.1.2-beta.jar
```