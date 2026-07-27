package com.local.minecraft.statsteal;

public record PendingStatLoss(String statKey, int newLevel, String source) {
}
