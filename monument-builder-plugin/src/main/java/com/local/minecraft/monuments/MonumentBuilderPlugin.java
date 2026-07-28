package com.local.minecraft.monuments;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class MonumentBuilderPlugin extends JavaPlugin implements TabExecutor {
    private BukkitTask activeTask;
    private String activeOperation;
    private List<OriginalBlock> lastUndo = List.of();
    private BuildSite lastSite;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (getCommand("monument") != null) {
            getCommand("monument").setExecutor(this);
            getCommand("monument").setTabCompleter(this);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            showUsage(sender);
            return true;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        if (action.equals("visit")) {
            if (!sender.hasPermission("monument.visit")) {
                sender.sendMessage(Component.text("You do not have permission.", NamedTextColor.RED));
                return true;
            }
            return handleVisit(sender);
        }
        if (!sender.hasPermission("monument.admin")) {
            sender.sendMessage(Component.text("You do not have permission.", NamedTextColor.RED));
            return true;
        }

        return switch (action) {
            case "build" -> handleBuild(sender, args);
            case "undo" -> handleUndo(sender);
            case "status" -> handleStatus(sender);
            default -> {
                showUsage(sender);
                yield true;
            }
        };
    }

    private boolean handleBuild(CommandSender sender, String[] args) {
        if (activeTask != null) {
            sender.sendMessage(Component.text("A monument operation is already running.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 2 || !isStatueName(args[1])) {
            sender.sendMessage("Usage: /monument build statue-of-liberty [world x y z]");
            return true;
        }

        BuildSite site;
        try {
            site = args.length == 6 ? siteFromArguments(args) : siteFromConfig();
        } catch (IllegalArgumentException exception) {
            sender.sendMessage(Component.text(exception.getMessage(), NamedTextColor.RED));
            return true;
        }
        if (site.y() - 2 < site.world().getMinHeight() || site.y() + 94 >= site.world().getMaxHeight()) {
            sender.sendMessage(Component.text("The statue would exceed this world's height limits.", NamedTextColor.RED));
            return true;
        }

        StatueOfLibertyGenerator.BuildPlan plan = StatueOfLibertyGenerator.generate();
        List<Map.Entry<StatueOfLibertyGenerator.Point, Material>> placements = plan.placements();
        List<OriginalBlock> undo = new ArrayList<>(placements.size());
        AtomicInteger cursor = new AtomicInteger();
        int blocksPerTick = Math.max(100, getConfig().getInt("blocks-per-tick", 1200));
        activeOperation = "Building Statue of Liberty";
        lastSite = site;
        sender.sendMessage(Component.text("Building " + plan.size() + " blocks at " + site.describe() + ".",
                NamedTextColor.GOLD));

        activeTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            int end = Math.min(placements.size(), cursor.get() + blocksPerTick);
            while (cursor.get() < end) {
                Map.Entry<StatueOfLibertyGenerator.Point, Material> placement = placements.get(cursor.getAndIncrement());
                StatueOfLibertyGenerator.Point point = placement.getKey();
                Block block = site.world().getBlockAt(site.x() + point.x(), site.y() + point.y(), site.z() + point.z());
                undo.add(new OriginalBlock(block.getX(), block.getY(), block.getZ(), block.getBlockData().clone()));
                block.setType(placement.getValue(), false);
            }
            if (cursor.get() >= placements.size()) {
                activeTask.cancel();
                activeTask = null;
                activeOperation = null;
                lastUndo = List.copyOf(undo);
                Bukkit.broadcast(Component.text("The Statue of Liberty test build is ready. Use /monument visit.",
                        NamedTextColor.GREEN));
            }
        }, 1L, 1L);
        return true;
    }

    private boolean handleVisit(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only a player can use /monument visit.");
            return true;
        }
        BuildSite site;
        try {
            site = lastSite != null ? lastSite : siteFromConfig();
        } catch (IllegalArgumentException exception) {
            sender.sendMessage(Component.text(exception.getMessage(), NamedTextColor.RED));
            return true;
        }
        Location destination = new Location(site.world(), site.x() + 0.5, site.y() + 4.0, site.z() + 44.5,
                180.0F, -8.0F);
        player.teleport(destination);
        player.sendMessage(Component.text("Welcome to the Statue of Liberty test site.", NamedTextColor.GREEN));
        return true;
    }

    private boolean handleUndo(CommandSender sender) {
        if (activeTask != null) {
            sender.sendMessage(Component.text("Wait for the current monument operation to finish.", NamedTextColor.RED));
            return true;
        }
        if (lastUndo.isEmpty() || lastSite == null) {
            sender.sendMessage(Component.text("There is no monument build to undo in this server session.",
                    NamedTextColor.RED));
            return true;
        }

        List<OriginalBlock> undo = lastUndo;
        AtomicInteger cursor = new AtomicInteger(undo.size() - 1);
        int blocksPerTick = Math.max(100, getConfig().getInt("blocks-per-tick", 1200));
        activeOperation = "Undoing Statue of Liberty";
        activeTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            int remaining = blocksPerTick;
            while (cursor.get() >= 0 && remaining-- > 0) {
                OriginalBlock original = undo.get(cursor.getAndDecrement());
                lastSite.world().getBlockAt(original.x(), original.y(), original.z())
                        .setBlockData(original.data(), false);
            }
            if (cursor.get() < 0) {
                activeTask.cancel();
                activeTask = null;
                activeOperation = null;
                lastUndo = List.of();
                sender.sendMessage(Component.text("The monument build was undone.", NamedTextColor.GREEN));
            }
        }, 1L, 1L);
        return true;
    }

    private boolean handleStatus(CommandSender sender) {
        if (activeTask != null) {
            sender.sendMessage(Component.text(activeOperation + " is in progress.", NamedTextColor.YELLOW));
        } else if (lastSite != null) {
            sender.sendMessage(Component.text("Last monument site: " + lastSite.describe(), NamedTextColor.YELLOW));
        } else {
            sender.sendMessage(Component.text("No monument operation has run in this server session.",
                    NamedTextColor.YELLOW));
        }
        return true;
    }

    private BuildSite siteFromConfig() {
        String path = "statue-of-liberty.";
        return createSite(
                getConfig().getString(path + "world", "world"),
                getConfig().getInt(path + "x", 544),
                getConfig().getInt(path + "y", 63),
                getConfig().getInt(path + "z", 928));
    }

    private BuildSite siteFromArguments(String[] args) {
        try {
            return createSite(args[2], Integer.parseInt(args[3]), Integer.parseInt(args[4]),
                    Integer.parseInt(args[5]));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Coordinates must be whole numbers.");
        }
    }

    private BuildSite createSite(String worldName, int x, int y, int z) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            throw new IllegalArgumentException("World '" + worldName + "' is not loaded.");
        }
        return new BuildSite(world, x, y, z);
    }

    private boolean isStatueName(String value) {
        String normalized = value.toLowerCase(Locale.ROOT).replace("_", "-");
        return normalized.equals("statue-of-liberty") || normalized.equals("liberty");
    }

    private void showUsage(CommandSender sender) {
        sender.sendMessage(Component.text("MonumentBuilder commands", NamedTextColor.GOLD));
        sender.sendMessage("/monument build statue-of-liberty [world x y z]");
        sender.sendMessage("/monument visit");
        sender.sendMessage("/monument undo");
        sender.sendMessage("/monument status");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("build", "visit", "undo", "status");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("build")) {
            return List.of("statue-of-liberty");
        }
        return List.of();
    }

    private record OriginalBlock(int x, int y, int z, BlockData data) {
    }

    private record BuildSite(World world, int x, int y, int z) {
        String describe() {
            return world.getName() + " at " + x + ", " + y + ", " + z;
        }
    }
}
