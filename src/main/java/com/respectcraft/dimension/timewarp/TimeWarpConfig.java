package com.respectcraft.dimension.timewarp;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configuracao do servidor do Time Warp ({@code config/respectcraft-timewarp-server.toml}, por mundo, Etapa 4.15).
 * Antes de carregar valem os padroes.
 */
public final class TimeWarpConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue CLOCK_TRAVEL;
    public static final ModConfigSpec.IntValue CHARGE_SECONDS;
    public static final ModConfigSpec.IntValue COOLDOWN_SECONDS;
    public static final ModConfigSpec.IntValue LAND_SEARCH_RADIUS;
    public static final ModConfigSpec.BooleanValue DRIFT_RECOVERY;
    public static final ModConfigSpec.IntValue STAGE_SECONDS;
    public static final ModConfigSpec.IntValue RECOVERY_RATE;
    public static final ModConfigSpec.BooleanValue EVENTS;
    public static final ModConfigSpec.DoubleValue EVENT_FREQUENCY;
    public static final ModConfigSpec.BooleanValue NOTICES;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("Time Warp: travel with the clock").push("travel");
        CLOCK_TRAVEL = b.comment("Hold right-click with a clock (main hand) to enter the Time Warp from the Overworld and to leave it.")
                .define("clockTravel", true);
        CHARGE_SECONDS = b.comment("Seconds the use button must be held with the clock.").defineInRange("chargeSeconds", 3, 1, 20);
        COOLDOWN_SECONDS = b.comment("Seconds between two clock trips (per player, survives relog).").defineInRange("cooldownSeconds", 30, 0, 3600);
        LAND_SEARCH_RADIUS = b.comment("Blocks around the scaled point searched for dry land before arriving on the water surface.")
                .defineInRange("landSearchRadius", 16, 0, 48);
        DRIFT_RECOVERY = b.comment("A player in the Time Warp without a clock can stay still looking straight up for 15 s to drift back to the point where they entered (no 3.33 scaling, so it is never a free fast trip).")
                .define("driftRecovery", true);
        b.pop();
        b.comment("Time Warp: exposure").push("exposure");
        STAGE_SECONDS = b.comment("Length of each exposure stage in seconds (stable, anomaly, instability; critical after three).")
                .defineInRange("stageSeconds", 60, 10, 600);
        RECOVERY_RATE = b.comment("Exposure ticks removed per tick spent outside the Time Warp (2 = recovers twice as fast as it builds). Only while online.")
                .defineInRange("recoveryRate", 2, 0, 100);
        EVENTS = b.comment("Rare atmospheric happenings (sounds, fog, system messages).").define("events", true);
        EVENT_FREQUENCY = b.comment("Multiplier for how often happenings occur (1.0 = default, 0 = never).")
                .defineInRange("eventFrequency", 1.0, 0.0, 5.0);
        NOTICES = b.comment("Holographic system notices for the Time Warp (entry, stage warnings, signals).").define("notices", true);
        b.pop();
        SPEC = b.build();
    }

    private TimeWarpConfig() {}

    private static boolean loaded() { return SPEC.isLoaded(); }

    public static boolean clockTravel() { return !loaded() || CLOCK_TRAVEL.get(); }
    public static int chargeTicks() { return (loaded() ? CHARGE_SECONDS.get() : 3) * TimeWarpRules.TICKS_PER_SECOND; }
    public static int cooldownTicks() { return (loaded() ? COOLDOWN_SECONDS.get() : 30) * TimeWarpRules.TICKS_PER_SECOND; }
    public static int landSearchRadius() { return loaded() ? LAND_SEARCH_RADIUS.get() : 16; }
    public static boolean driftRecovery() { return !loaded() || DRIFT_RECOVERY.get(); }
    public static int stageSeconds() { return loaded() ? STAGE_SECONDS.get() : 60; }
    public static int recoveryRate() { return loaded() ? RECOVERY_RATE.get() : 2; }
    public static boolean events() { return !loaded() || EVENTS.get(); }
    public static double eventFrequency() { return loaded() ? EVENT_FREQUENCY.get() : 1.0; }
    public static boolean notices() { return !loaded() || NOTICES.get(); }
}
