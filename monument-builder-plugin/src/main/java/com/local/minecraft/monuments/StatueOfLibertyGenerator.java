package com.local.minecraft.monuments;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;

final class StatueOfLibertyGenerator {
    private static final Material[] PATINA = {
        Material.OXIDIZED_COPPER,
        Material.OXIDIZED_COPPER,
        Material.OXIDIZED_COPPER,
        Material.WAXED_OXIDIZED_COPPER,
        Material.PRISMARINE,
        Material.WEATHERED_COPPER
    };

    private StatueOfLibertyGenerator() {
    }

    static BuildPlan generate() {
        BuildPlan plan = new BuildPlan();
        buildFortWood(plan);
        buildPedestal(plan);
        buildFigure(plan);
        buildViewingPier(plan);
        return plan;
    }

    private static void buildFortWood(BuildPlan plan) {
        for (int y = -2; y <= 2; y++) {
            for (int x = -36; x <= 36; x++) {
                for (int z = -36; z <= 36; z++) {
                    if (!insideStar(x, z, 36.0, 28.0, 11)) {
                        continue;
                    }
                    boolean edge = !insideStar(x + 1, z, 36.0, 28.0, 11)
                            || !insideStar(x - 1, z, 36.0, 28.0, 11)
                            || !insideStar(x, z + 1, 36.0, 28.0, 11)
                            || !insideStar(x, z - 1, 36.0, 28.0, 11);
                    Material material;
                    if (edge) {
                        material = Material.DEEPSLATE_BRICKS;
                    } else if (y == 2) {
                        material = hash(x, y, z, 9) == 0 ? Material.MOSSY_STONE_BRICKS : Material.STONE_BRICKS;
                    } else {
                        material = hash(x, y, z, 5) == 0 ? Material.ANDESITE : Material.STONE;
                    }
                    plan.set(x, y, z, material);
                }
            }
        }

        for (int x = -30; x <= 30; x += 6) {
            plan.set(x, 3, 27, Material.SEA_LANTERN);
            plan.set(x, 3, -27, Material.SEA_LANTERN);
        }
    }

    private static void buildPedestal(BuildPlan plan) {
        fillSquare(plan, 22, 3, 5, Material.POLISHED_ANDESITE);
        fillSquare(plan, 20, 6, 7, Material.SMOOTH_STONE);
        fillSquare(plan, 18, 8, 10, Material.STONE_BRICKS);
        fillSquare(plan, 16, 11, 13, Material.POLISHED_TUFF);

        for (int y = 14; y <= 34; y++) {
            int half = 15 - Math.min(2, (y - 14) / 8);
            squareShell(plan, half, y, 2, (x, yy, z) -> pedestalStone(x, yy, z));
            if (y == 14 || y == 24 || y == 34) {
                fillSquareLayer(plan, half - 2, y, Material.TUFF_BRICKS);
            }
        }

        fillSquare(plan, 14, 35, 37, Material.POLISHED_ANDESITE);
        fillSquare(plan, 12, 38, 40, Material.STONE_BRICKS);
        fillSquare(plan, 10, 41, 43, Material.SMOOTH_STONE);
        fillSquare(plan, 8, 44, 46, Material.POLISHED_ANDESITE);

        addPedestalWindows(plan);
        carveEntrance(plan);
    }

    private static void addPedestalWindows(BuildPlan plan) {
        int[] offsets = {-7, 0, 7};
        for (int offset : offsets) {
            for (int y = 25; y <= 29; y++) {
                for (int width = -1; width <= 1; width++) {
                    plan.set(offset + width, y, 13, Material.TINTED_GLASS);
                    plan.set(offset + width, y, -13, Material.TINTED_GLASS);
                    plan.set(13, y, offset + width, Material.TINTED_GLASS);
                    plan.set(-13, y, offset + width, Material.TINTED_GLASS);
                }
            }
        }
        for (int offset : new int[] {-10, -4, 4, 10}) {
            for (int y = 23; y <= 32; y++) {
                plan.set(offset, y, 14, Material.POLISHED_DIORITE);
                plan.set(offset, y, -14, Material.POLISHED_DIORITE);
                plan.set(14, y, offset, Material.POLISHED_DIORITE);
                plan.set(-14, y, offset, Material.POLISHED_DIORITE);
            }
        }
    }

    private static void carveEntrance(BuildPlan plan) {
        for (int y = 14; y <= 21; y++) {
            int halfWidth = y >= 20 ? 1 : 2;
            for (int x = -halfWidth; x <= halfWidth; x++) {
                for (int z = 13; z <= 16; z++) {
                    plan.set(x, y, z, Material.AIR);
                }
            }
        }
        for (int y = 15; y <= 22; y++) {
            plan.set(-3, y, 15, Material.POLISHED_BLACKSTONE_BRICKS);
            plan.set(3, y, 15, Material.POLISHED_BLACKSTONE_BRICKS);
        }
    }

    private static void buildFigure(BuildPlan plan) {
        buildRobe(plan);
        buildHeadAndCrown(plan);
        buildRaisedArmAndTorch(plan);
        buildTabletAndLeftArm(plan);
        buildRobeFolds(plan);
        buildFootDetails(plan);
    }

    private static void buildRobe(BuildPlan plan) {
        for (int y = 47; y <= 66; y++) {
            int rx;
            int rz;
            if (y <= 51) {
                rx = 7;
                rz = 5;
            } else if (y <= 58) {
                rx = 8 - ((y - 52) / 4);
                rz = 5;
            } else {
                rx = 6 - ((y - 59) / 5);
                rz = 4;
            }
            fillEllipseLayer(plan, 0, y, 0, rx, rz, StatueOfLibertyGenerator::patina);
        }
        fillEllipsoid(plan, 0, 65, 0, 7, 3, 4, StatueOfLibertyGenerator::patina);
        fillEllipsoid(plan, 0, 68, 0, 2, 2, 2, StatueOfLibertyGenerator::patina);
    }

    private static void buildHeadAndCrown(BuildPlan plan) {
        fillEllipsoid(plan, 0, 72, 0, 3, 4, 3, StatueOfLibertyGenerator::patina);
        plan.set(-1, 73, 3, Material.DARK_PRISMARINE);
        plan.set(1, 73, 3, Material.DARK_PRISMARINE);
        plan.set(0, 72, 4, Material.WEATHERED_COPPER);
        plan.set(0, 70, 3, Material.DARK_PRISMARINE);

        for (int y = 75; y <= 77; y++) {
            ellipseRing(plan, 0, y, 0, 4, 4, Material.OXIDIZED_CUT_COPPER);
        }
        for (int index = 0; index < 7; index++) {
            double angle = Math.toRadians(index * 30.0);
            int startX = (int) Math.round(Math.cos(angle) * 4.0);
            int startY = 76 + (int) Math.round(Math.sin(angle) * 4.0);
            int endX = (int) Math.round(Math.cos(angle) * 10.0);
            int endY = 76 + (int) Math.round(Math.sin(angle) * 10.0);
            thickLine(plan, startX, startY, -1, endX, endY, -1, 0.65, Material.OXIDIZED_COPPER);
        }
    }

    private static void buildRaisedArmAndTorch(BuildPlan plan) {
        thickLine(plan, -5, 65, 0, -8, 74, 0, 2.35, Material.OXIDIZED_COPPER);
        thickLine(plan, -8, 74, 0, -8, 84, 0, 1.85, Material.OXIDIZED_COPPER);
        fillEllipsoid(plan, -8, 85, 0, 2, 2, 2, StatueOfLibertyGenerator::patina);
        thickLine(plan, -8, 85, 0, -8, 88, 0, 0.9, Material.WEATHERED_COPPER);
        ellipseRing(plan, -8, 87, 0, 3, 3, Material.OXIDIZED_CUT_COPPER);
        ellipseRing(plan, -8, 88, 0, 3, 3, Material.GOLD_BLOCK);

        fillEllipsoid(plan, -8, 91, 0, 2, 3, 2, (x, y, z) -> {
            int choice = hash(x, y, z, 4);
            return switch (choice) {
                case 0 -> Material.ORANGE_STAINED_GLASS;
                case 1 -> Material.YELLOW_STAINED_GLASS;
                case 2 -> Material.GOLD_BLOCK;
                default -> Material.SHROOMLIGHT;
            };
        });
        plan.set(-8, 94, 0, Material.YELLOW_STAINED_GLASS);
    }

    private static void buildTabletAndLeftArm(BuildPlan plan) {
        thickLine(plan, 5, 65, 0, 7, 60, 2, 2.0, Material.OXIDIZED_COPPER);
        thickLine(plan, 7, 60, 2, 5, 59, 4, 1.6, Material.OXIDIZED_COPPER);

        for (int x = 4; x <= 9; x++) {
            for (int y = 56; y <= 67; y++) {
                Material material = x == 4 || x == 9 || y == 56 || y == 67
                        ? Material.DARK_PRISMARINE
                        : Material.OXIDIZED_CUT_COPPER;
                plan.set(x, y, 4, material);
            }
        }
        for (int y : new int[] {59, 62, 65}) {
            for (int x = 6; x <= 8; x++) {
                plan.set(x, y, 5, Material.WAXED_WEATHERED_COPPER);
            }
        }
    }

    private static void buildRobeFolds(BuildPlan plan) {
        thickLine(plan, -5, 63, 4, 2, 48, 5, 0.65, Material.WEATHERED_COPPER);
        thickLine(plan, 3, 65, 4, -2, 50, 5, 0.65, Material.PRISMARINE_BRICKS);
        thickLine(plan, 6, 58, 4, 4, 48, 5, 0.65, Material.DARK_PRISMARINE);
        thickLine(plan, -1, 65, 4, -6, 53, 5, 0.65, Material.WAXED_OXIDIZED_COPPER);
    }

    private static void buildFootDetails(BuildPlan plan) {
        fillEllipsoid(plan, -3, 47, 3, 3, 1, 2, StatueOfLibertyGenerator::patina);
        fillEllipsoid(plan, 3, 47, 3, 3, 1, 2, StatueOfLibertyGenerator::patina);
        for (int x = -7; x <= -2; x++) {
            plan.set(x, 47, 6, Material.CHAIN);
        }
        plan.set(-7, 48, 6, Material.IRON_BARS);
        plan.set(-2, 48, 6, Material.IRON_BARS);
    }

    private static void buildViewingPier(BuildPlan plan) {
        for (int z = 29; z <= 43; z++) {
            for (int x = -3; x <= 3; x++) {
                plan.set(x, 2, z, Material.STONE_BRICKS);
                plan.set(x, 3, z, Material.SMOOTH_STONE);
            }
        }
        for (int x = -6; x <= 6; x++) {
            for (int z = 40; z <= 48; z++) {
                plan.set(x, 2, z, Material.STONE_BRICKS);
                plan.set(x, 3, z, Material.POLISHED_ANDESITE);
            }
        }
        for (int x = -6; x <= 6; x += 3) {
            plan.set(x, 4, 48, Material.SEA_LANTERN);
        }
    }

    private static Material pedestalStone(int x, int y, int z) {
        return switch (hash(x, y, z, 8)) {
            case 0 -> Material.MOSSY_STONE_BRICKS;
            case 1 -> Material.CRACKED_STONE_BRICKS;
            case 2 -> Material.TUFF_BRICKS;
            default -> Material.STONE_BRICKS;
        };
    }

    private static Material patina(int x, int y, int z) {
        return PATINA[hash(x, y, z, PATINA.length)];
    }

    private static int hash(int x, int y, int z, int bound) {
        int value = x * 73428767 ^ y * 912931 ^ z * 438289;
        value ^= value >>> 16;
        return Math.floorMod(value, bound);
    }

    private static void fillSquare(BuildPlan plan, int half, int minY, int maxY, Material material) {
        for (int y = minY; y <= maxY; y++) {
            fillSquareLayer(plan, half, y, material);
        }
    }

    private static void fillSquareLayer(BuildPlan plan, int half, int y, Material material) {
        for (int x = -half; x <= half; x++) {
            for (int z = -half; z <= half; z++) {
                plan.set(x, y, z, material);
            }
        }
    }

    private static void squareShell(BuildPlan plan, int half, int y, int thickness, MaterialPicker picker) {
        for (int x = -half; x <= half; x++) {
            for (int z = -half; z <= half; z++) {
                if (Math.abs(x) > half - thickness || Math.abs(z) > half - thickness) {
                    plan.set(x, y, z, picker.pick(x, y, z));
                }
            }
        }
    }

    private static void fillEllipseLayer(BuildPlan plan, int cx, int y, int cz, int rx, int rz,
            MaterialPicker picker) {
        for (int x = cx - rx; x <= cx + rx; x++) {
            for (int z = cz - rz; z <= cz + rz; z++) {
                double dx = (x - cx) / (rx + 0.35);
                double dz = (z - cz) / (rz + 0.35);
                if (dx * dx + dz * dz <= 1.0) {
                    plan.set(x, y, z, picker.pick(x, y, z));
                }
            }
        }
    }

    private static void fillEllipsoid(BuildPlan plan, int cx, int cy, int cz, int rx, int ry, int rz,
            MaterialPicker picker) {
        for (int x = cx - rx; x <= cx + rx; x++) {
            for (int y = cy - ry; y <= cy + ry; y++) {
                for (int z = cz - rz; z <= cz + rz; z++) {
                    double dx = (x - cx) / (rx + 0.35);
                    double dy = (y - cy) / (ry + 0.35);
                    double dz = (z - cz) / (rz + 0.35);
                    if (dx * dx + dy * dy + dz * dz <= 1.0) {
                        plan.set(x, y, z, picker.pick(x, y, z));
                    }
                }
            }
        }
    }

    private static void ellipseRing(BuildPlan plan, int cx, int y, int cz, int rx, int rz, Material material) {
        for (int x = cx - rx; x <= cx + rx; x++) {
            for (int z = cz - rz; z <= cz + rz; z++) {
                double outerX = (x - cx) / (rx + 0.35);
                double outerZ = (z - cz) / (rz + 0.35);
                double innerX = (x - cx) / Math.max(0.5, rx - 1.0);
                double innerZ = (z - cz) / Math.max(0.5, rz - 1.0);
                if (outerX * outerX + outerZ * outerZ <= 1.0
                        && innerX * innerX + innerZ * innerZ >= 1.0) {
                    plan.set(x, y, z, material);
                }
            }
        }
    }

    private static void thickLine(BuildPlan plan, int x1, int y1, int z1, int x2, int y2, int z2,
            double radius, Material material) {
        int steps = Math.max(Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1)), Math.abs(z2 - z1)) * 2;
        for (int step = 0; step <= Math.max(1, steps); step++) {
            double progress = step / (double) Math.max(1, steps);
            double cx = x1 + ((x2 - x1) * progress);
            double cy = y1 + ((y2 - y1) * progress);
            double cz = z1 + ((z2 - z1) * progress);
            int limit = (int) Math.ceil(radius);
            for (int x = (int) Math.floor(cx) - limit; x <= (int) Math.ceil(cx) + limit; x++) {
                for (int y = (int) Math.floor(cy) - limit; y <= (int) Math.ceil(cy) + limit; y++) {
                    for (int z = (int) Math.floor(cz) - limit; z <= (int) Math.ceil(cz) + limit; z++) {
                        double dx = x - cx;
                        double dy = y - cy;
                        double dz = z - cz;
                        if (dx * dx + dy * dy + dz * dz <= radius * radius) {
                            plan.set(x, y, z, material);
                        }
                    }
                }
            }
        }
    }

    private static boolean insideStar(double x, double z, double outerRadius, double innerRadius, int points) {
        List<double[]> vertices = new ArrayList<>(points * 2);
        for (int index = 0; index < points * 2; index++) {
            double angle = (-Math.PI / 2.0) + (Math.PI * index / points);
            double radius = index % 2 == 0 ? outerRadius : innerRadius;
            vertices.add(new double[] {Math.cos(angle) * radius, Math.sin(angle) * radius});
        }
        boolean inside = false;
        for (int current = 0, previous = vertices.size() - 1; current < vertices.size(); previous = current++) {
            double[] a = vertices.get(current);
            double[] b = vertices.get(previous);
            boolean crosses = ((a[1] > z) != (b[1] > z))
                    && (x < (b[0] - a[0]) * (z - a[1]) / (b[1] - a[1]) + a[0]);
            if (crosses) {
                inside = !inside;
            }
        }
        return inside;
    }

    @FunctionalInterface
    private interface MaterialPicker {
        Material pick(int x, int y, int z);
    }

    record Point(int x, int y, int z) {
    }

    static final class BuildPlan {
        private final Map<Point, Material> blocks = new LinkedHashMap<>();

        void set(int x, int y, int z, Material material) {
            blocks.put(new Point(x, y, z), material);
        }

        List<Map.Entry<Point, Material>> placements() {
            return List.copyOf(blocks.entrySet());
        }

        int size() {
            return blocks.size();
        }
    }
}
