package com.local.minecraft.statsteal;

public record ConfiguredStat(
        StealableStat stat,
        boolean enabled,
        double stealAmount,
        double minValue,
        double maxValue,
        String displayName) {
}
