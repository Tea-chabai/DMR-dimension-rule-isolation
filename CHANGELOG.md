# Changelog

All notable changes to Dimensional Rule Isolation.

## 0.1.2-beta — 2026-11-17

### Added

- **Per-dimension difficulty** (`/dmr difficulty <dimension> [value]`). Mob damage, spawn
  checks and the client HUD follow the dimension's own value.
- **Per-dimension weather** (`/dmr weather <dimension> [clear|rain|thunder] [duration]`).
  Weather state changes are broadcast only inside the dimension that changed.
- **Per-dimension recipes** (`/dmr recipe <dimension> add|remove|list|rebuild|check`).
  A dimension claims datapack namespaces; crafting tables, furnaces, stonecutters, smithing
  tables and brewing stands only see the claimed namespaces. Dimensions without claims see
  only unclaimed namespaces.
- **Per-dimension time offset** (`/dmr time <dimension> query|set|add|reset`). Time keeps
  flowing; displayed time = global clock + offset. Undead burning, villager schedules and sky
  darkening follow the offset.

### Fixed

- The client difficulty HUD showed the global value even in dimensions with their own
  difficulty. The dimension's value is now sent on join, respawn and dimension change.
- Vanilla weather packets (`START_RAINING` / `STOP_RAINING` / rain level) were broadcast to
  every player; they are now filtered per dimension, so rain sound no longer leaks into
  other worlds.
- Difficulty is preloaded before level creation (`SERVER_STARTING`), so saved hostile mobs in
  a non-peaceful dimension are no longer discarded when the global difficulty is peaceful.

### Known limitations

- Time is an **offset**, not an independent clock: sleeping through the night still moves the
  global clock, and vanilla `/time` still operates globally.
- The recipe book and `/recipe` still list every namespace; crafting validation is per
  dimension.
- Loot tables, advancements and item/block tags remain global.
- The vanilla `/difficulty` command still writes the global difficulty; use `/dmr difficulty`.
- Do not install alongside another mod that also overrides `ServerLevel.getGameRules()`.

### Compatibility

- Minecraft 26.3, Fabric Loader 0.19.5+, Fabric API, Java 25.
- Server-side only; vanilla clients can join.

### Download

`dimension-rule-isolation-0.1.2-beta.jar`