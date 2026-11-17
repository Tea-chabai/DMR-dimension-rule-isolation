package com.dimrule;

import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.clock.ClockInstance;
import net.minecraft.world.clock.ClockManager;
import net.minecraft.world.clock.ClockNetworkState;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-dimension time <b>offset</b> (not an independent clock).
 *
 * <p>Displayed time = the server clock + the dimension's offset, so time keeps flowing while each
 * dimension can sit at a different time of day. The offset is applied at the clock-source level
 * ({@link Level#getDefaultClockTime()} and the environment-attribute clock), which keeps burning,
 * villager schedules and sky darkening consistent with the visible time.
 *
 * <p>Offsets live only in the mod's JSON config; removing the mod restores vanilla global time.
 */
public final class TimeIsolation {
    private static final Map<ResourceKey<Level>, Long> OFFSETS = new ConcurrentHashMap<>();

    private TimeIsolation() {
    }

    public static long offset(ResourceKey<Level> key) {
        return OFFSETS.getOrDefault(key, 0L);
    }

    public static void setOffset(ResourceKey<Level> key, long ticks) {
        if (ticks == 0L) {
            OFFSETS.remove(key);
        } else {
            OFFSETS.put(key, ticks);
        }
    }

    public static Map<ResourceKey<Level>, Long> offsets() {
        return java.util.Collections.unmodifiableMap(OFFSETS);
    }

    public static void clear() {
        OFFSETS.clear();
    }

    /** Base clock ticks without our offset (the server-wide clock). */
    public static long baseTicks(Level level) {
        return level.dimensionType().defaultClock()
                .map(clock -> level.clockManager().getInstance(clock).totalTicks())
                .orElse(0L);
    }

    /** The clock manager to expose to a level: wrapped with the offset when one is configured. */
    public static ClockManager wrap(Level level) {
        long offset = offset(level.dimension());
        ClockManager base = level.clockManager();
        return offset == 0L ? base : new OffsetClockManager(base, offset);
    }

    /** Sends the dimension's offset clock to one player (join, respawn, dimension change). */
    public static void sendTime(ServerPlayer player, ServerLevel level) {
        long offset = offset(level.dimension());
        if (offset == 0L) {
            return;
        }
        level.dimensionType().defaultClock().ifPresent(clock -> {
            ClockInstance instance = level.clockManager().getInstance(clock);
            ClockNetworkState state = new ClockNetworkState(
                    instance.totalTicks() + offset, instance.partialTick(), instance.rate());
            player.connection.send(new ClientboundSetTimePacket(level.getGameTime(),
                    Map.of((Holder<WorldClock>) clock, state)));
        });
    }

    private record OffsetClockManager(ClockManager base, long offset) implements ClockManager {
        @Override
        public ClockInstance getInstance(Holder<WorldClock> clock) {
            return new OffsetClockInstance(this.base.getInstance(clock), this.offset);
        }
    }

    private record OffsetClockInstance(ClockInstance base, long offset) implements ClockInstance {
        @Override
        public long totalTicks() {
            return this.base.totalTicks() + this.offset;
        }

        @Override
        public float partialTick() {
            return this.base.partialTick();
        }

        @Override
        public float rate() {
            return this.base.rate();
        }

        @Override
        public boolean isPaused() {
            return this.base.isPaused();
        }
    }
}