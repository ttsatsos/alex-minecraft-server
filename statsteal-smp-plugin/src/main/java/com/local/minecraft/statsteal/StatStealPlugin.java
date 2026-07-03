package com.local.minecraft.statsteal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.BanList;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class StatStealPlugin extends JavaPlugin implements Listener {
    private static final int MIN_LEVEL = -5;
    private static final int MAX_LEVEL = 5;
    private final Map<StealableStat, ConfiguredStat> configuredStats = new EnumMap<>(StealableStat.class);
    private final Map<java.util.UUID, Integer> processedDeathTicks = new HashMap<>();
    private PlayerStatStore store;
    private boolean requirePlayerKill;
    private boolean announceToServer;
    private boolean restoreHealthOnSteal;
    private String messagePrefix;
    private final Set<String> excludedPlayerNames = new HashSet<>();
    private final Set<String> excludedPlayerPrefixes = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        store = new PlayerStatStore(new java.io.File(getDataFolder(), "player-stats.yml"));
        reloadPluginState();
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        saveStore();
    }

    private void reloadPluginState() {
        reloadConfig();
        requirePlayerKill = getConfig().getBoolean("steal.require-player-kill", true);
        announceToServer = getConfig().getBoolean("steal.announce-to-server", true);
        restoreHealthOnSteal = getConfig().getBoolean("steal.restore-health-on-steal", true);
        messagePrefix = colorize(getConfig().getString("steal.message-prefix", "&6[StatSteal]&r "));
        excludedPlayerNames.clear();
        excludedPlayerPrefixes.clear();
        for (String name : getConfig().getStringList("steal.excluded-player-names")) {
            excludedPlayerNames.add(name.toLowerCase(Locale.ROOT));
        }
        for (String prefix : getConfig().getStringList("steal.excluded-player-name-prefixes")) {
            excludedPlayerPrefixes.add(prefix.toLowerCase(Locale.ROOT));
        }
        configuredStats.clear();
        for (StealableStat stat : StealableStat.values()) {
            String path = "stats." + stat.configKey();
            configuredStats.put(stat, new ConfiguredStat(
                    stat,
                    getConfig().getBoolean(path + ".enabled", false),
                    getConfig().getDouble(path + ".steal-amount", 1.0),
                    getConfig().getDouble(path + ".min-value", 0.0),
                    getConfig().getDouble(path + ".max-value", 100.0),
                    getConfig().getString(path + ".display-name", stat.name())));
        }
        store.load();
        for (Player player : Bukkit.getOnlinePlayers()) {
            applyStats(player, true);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        applyStats(event.getPlayer(), true);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        processedDeathTicks.remove(event.getPlayer().getUniqueId());
        Bukkit.getScheduler().runTask(this, () -> applyStats(event.getPlayer(), true));
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getPlayer();
        Player killer = victim.getKiller();
        if (victim.hasMetadata("NPC")) {
            return;
        }
        if (isExcludedPlayer(victim) || (killer != null && isExcludedPlayer(killer))) {
            return;
        }

        int currentTick = Bukkit.getCurrentTick();
        Integer previousTick = processedDeathTicks.put(victim.getUniqueId(), currentTick);
        if (previousTick != null && previousTick == currentTick) {
            return;
        }

        boolean realPlayerKill = killer != null
                && !killer.equals(victim)
                && !killer.hasMetadata("NPC");

        if (realPlayerKill) {
            handlePlayerKill(killer, victim);
            return;
        }

        handleDeathPenalty(victim, null);
    }

    private void handlePlayerKill(Player killer, Player victim) {
        List<ConfiguredStat> transferable = new ArrayList<>();
        for (ConfiguredStat config : configuredStats.values()) {
            if (!config.enabled()) {
                continue;
            }
            if (getLevel(killer, config.stat()) >= MAX_LEVEL) {
                continue;
            }
            if (getLevel(victim, config.stat()) <= MIN_LEVEL) {
                continue;
            }
            transferable.add(config);
        }

        if (transferable.isEmpty()) {
            killer.sendMessage(Component.text(messagePrefix + "You are maxed out on every stat and gained nothing from " + victim.getName() + ".", NamedTextColor.YELLOW));
            return;
        }

        ConfiguredStat chosen = transferable.get(ThreadLocalRandom.current().nextInt(transferable.size()));
        updateLevel(killer, chosen.stat(), 1);
        updateLevel(victim, chosen.stat(), -1);
        applyStats(killer, true);
        applyStats(victim, false);
        saveStore();

        killer.sendMessage(Component.text(messagePrefix + "You gained 1 " + chosen.displayName() + " stack ("
                + formatPercent(chosen.stat()) + ", level " + getLevel(killer, chosen.stat()) + "/" + MAX_LEVEL
                + ") from killing " + victim.getName() + ".", NamedTextColor.GREEN));
        victim.sendMessage(Component.text(messagePrefix + "You lost 1 " + chosen.displayName() + " stack to " + killer.getName() + ".", NamedTextColor.RED));
        if (announceToServer) {
            Bukkit.broadcast(Component.text(messagePrefix + killer.getName() + " stole 1 " + chosen.displayName()
                    + " stack from " + victim.getName() + ".", NamedTextColor.GOLD));
        }
        maybeBanIfBottomedOut(victim);
    }

    private void handleDeathPenalty(Player victim, Player killer) {
        List<ConfiguredStat> eligible = new ArrayList<>();
        for (ConfiguredStat config : configuredStats.values()) {
            if (!config.enabled()) {
                continue;
            }
            if (getLevel(victim, config.stat()) > MIN_LEVEL) {
                eligible.add(config);
            }
        }
        if (eligible.isEmpty()) {
            return;
        }

        ConfiguredStat chosen = eligible.get(ThreadLocalRandom.current().nextInt(eligible.size()));
        updateLevel(victim, chosen.stat(), -1);
        applyStats(victim, false);
        saveStore();

        String source = killer == null ? "death" : killer.getName();
        victim.sendMessage(Component.text(messagePrefix + "You lost 1 " + chosen.displayName() + " stack to " + source + ".", NamedTextColor.RED));
        maybeBanIfBottomedOut(victim);
    }

    private void maybeBanIfBottomedOut(Player player) {
        for (ConfiguredStat config : configuredStats.values()) {
            if (!config.enabled()) {
                continue;
            }
            if (getLevel(player, config.stat()) > MIN_LEVEL) {
                return;
            }
        }
        Date expires = Date.from(Instant.now().plus(Duration.ofDays(30)));
        Bukkit.getBanList(BanList.Type.NAME).addBan(
                player.getName(),
                "You reached -5 in every StatSteal attribute.",
                expires,
                "StatStealSmp");
        player.kick(Component.text("You reached -5 in every StatSteal attribute and are banned for 30 days.", NamedTextColor.RED));
    }

    private int getLevel(Player player, StealableStat stat) {
        return store.getLevel(player.getUniqueId(), stat);
    }

    private void updateLevel(Player player, StealableStat stat, int delta) {
        int current = getLevel(player, stat);
        store.setLevel(player.getUniqueId(), stat, clampLevel(current + delta));
    }

    private int clampLevel(int level) {
        return Math.max(MIN_LEVEL, Math.min(MAX_LEVEL, level));
    }

    private boolean isExcludedPlayer(Player player) {
        String normalized = normalizeName(player.getName());
        if (excludedPlayerNames.contains(normalized)) {
            return true;
        }
        for (String prefix : excludedPlayerPrefixes) {
            if (normalized.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeName(String input) {
        StringBuilder builder = new StringBuilder(input.length());
        boolean skipNext = false;
        for (char c : input.toCharArray()) {
            if (skipNext) {
                skipNext = false;
                continue;
            }
            if (c == '\u00A7') {
                skipNext = true;
                continue;
            }
            builder.append(c);
        }
        return builder.toString().trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("stats")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Run /statsteal stats <player> from console.");
                return true;
            }
            showStats(sender, player);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                if (!sender.hasPermission("statsteal.admin")) {
                    sender.sendMessage("You do not have permission.");
                    return true;
                }
                reloadPluginState();
                sender.sendMessage("StatSteal config reloaded.");
                return true;
            }
            case "resetplayer" -> {
                if (!sender.hasPermission("statsteal.admin")) {
                    sender.sendMessage("You do not have permission.");
                    return true;
                }
                if (args.length != 2) {
                    sender.sendMessage("Usage: /statsteal resetplayer <player>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage("That player must be online right now.");
                    return true;
                }
                resetPlayerStats(target);
                applyStats(target, true);
                saveStore();
                sender.sendMessage("Reset StatSteal stats for " + target.getName() + ".");
                target.sendMessage(Component.text(messagePrefix + "Your stolen stats were reset.", NamedTextColor.YELLOW));
                return true;
            }
            case "resetall" -> {
                if (!sender.hasPermission("statsteal.admin")) {
                    sender.sendMessage("You do not have permission.");
                    return true;
                }
                store.clearAll();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    resetPlayerStats(player);
                    applyStats(player, true);
                }
                saveStore();
                sender.sendMessage("Reset StatSteal stats for all players.");
                return true;
            }
            default -> {
                sender.sendMessage("Usage: /statsteal <stats|reload|resetplayer|resetall>");
                return true;
            }
        }
    }

    private void showStats(CommandSender sender, Player player) {
        sender.sendMessage(Component.text("StatSteal stats for " + player.getName(), NamedTextColor.GOLD));
        for (ConfiguredStat config : configuredStats.values()) {
            if (!config.enabled()) {
                continue;
            }
            int level = getLevel(player, config.stat());
            double value = getAppliedValue(config.stat(), level, config);
            sender.sendMessage(Component.text("- " + config.displayName() + ": level " + level
                    + " (" + trimDouble(value) + ")", NamedTextColor.YELLOW));
        }
    }

    private void applyStats(Player player, boolean clampHealthNow) {
        for (ConfiguredStat config : configuredStats.values()) {
            AttributeInstance attribute = player.getAttribute(config.stat().attribute());
            if (attribute == null) {
                continue;
            }
            attribute.setBaseValue(getAppliedValue(config.stat(), getLevel(player, config.stat()), config));
        }
        if (clampHealthNow && restoreHealthOnSteal && !player.isDead()) {
            AttributeInstance maxHealth = player.getAttribute(StealableStat.MAX_HEALTH.attribute());
            if (maxHealth != null) {
                player.setHealth(Math.min(player.getHealth(), maxHealth.getBaseValue()));
            }
        }
    }

    private double getAppliedValue(StealableStat stat, int level, ConfiguredStat config) {
        double value = getVanillaDefault(stat) + (config.stealAmount() * level);
        return clamp(value, config.minValue(), config.maxValue());
    }

    private void resetPlayerStats(Player player) {
        store.reset(player.getUniqueId());
        for (ConfiguredStat config : configuredStats.values()) {
            store.setLevel(player.getUniqueId(), config.stat(), 0);
        }
    }

    private static double getVanillaDefault(StealableStat stat) {
        return switch (stat) {
            case MAX_HEALTH -> 20.0;
            case ATTACK_DAMAGE -> 1.0;
            case MOVEMENT_SPEED -> 0.1;
            case ATTACK_SPEED -> 4.0;
            case ARMOR -> 0.0;
            case LUCK -> 0.0;
        };
    }

    private void saveStore() {
        try {
            if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
                getLogger().warning("Could not create plugin data folder.");
                return;
            }
            store.save();
        } catch (IOException exception) {
            getLogger().log(Level.SEVERE, "Failed to save StatSteal player data", exception);
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String trimDouble(double value) {
        if (Math.floor(value) == value) {
            return Long.toString((long) value);
        }
        return String.format(Locale.US, "%.2f", value);
    }

    private static String colorize(String input) {
        return Objects.requireNonNullElse(input, "").replace("&6", "").replace("&r", "");
    }

    private static String formatPercent(StealableStat stat) {
        return switch (stat) {
            case MAX_HEALTH, ATTACK_DAMAGE, MOVEMENT_SPEED, ARMOR -> "25%";
            case ATTACK_SPEED, LUCK -> trimDouble(0);
        };
    }
}
