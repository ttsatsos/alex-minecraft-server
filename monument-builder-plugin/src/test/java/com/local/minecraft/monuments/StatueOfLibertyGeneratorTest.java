package com.local.minecraft.monuments;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.IntSummaryStatistics;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class StatueOfLibertyGeneratorTest {
    @Test
    void producesAFullScaleRecognizablePlan() {
        StatueOfLibertyGenerator.BuildPlan plan = StatueOfLibertyGenerator.generate();
        IntSummaryStatistics yBounds = plan.placements().stream()
                .mapToInt(entry -> entry.getKey().y())
                .summaryStatistics();

        assertTrue(plan.size() > 25_000);
        assertEquals(-2, yBounds.getMin());
        assertEquals(94, yBounds.getMax());
        assertTrue(plan.placements().stream().anyMatch(entry -> entry.getValue() == Material.GOLD_BLOCK));
        assertTrue(plan.placements().stream().anyMatch(entry -> entry.getValue() == Material.OXIDIZED_COPPER));
        assertTrue(plan.placements().stream().anyMatch(entry -> entry.getValue() == Material.TINTED_GLASS));
    }
}
