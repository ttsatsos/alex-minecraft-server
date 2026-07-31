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
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.BanList;
import org.bukkit.Material;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class StatStealPlugin extends JavaPlugin implements Listener {
    private static final int MIN_LEVEL = -5;
    private static final int MAX_LEVEL = 5;
    private final Map<StealableStat, ConfiguredStat> configuredStats = new EnumMap<>(StealableStat.class);
    private final Map<java.util.UUID, Integer> processedDeathTicks = new HashMap<>();
    private final Map<java.util.UUID, Integer> restrictionMessageTicks = new HashMap<>();
    private PlayerStatStore store;
    private boolean requirePlayerKill;
    private boolean allowCitizensNpcs;
    private boolean announceToServer;
    private boolean restoreHealthOnSteal;
    private boolean showRulesOnJoin;
    private boolean disableEndCrystals;
    private boolean disableRespawnAnchors;
    private long rulesJoinDelayTicks;
    private String messagePrefix;
    private List<String> serverRules = List.of();
    private Title.Times notificationTitleTimes;
    private final Set<String> excludedPlayerNames = new HashSet<>();
    private final Set<String> excludedPlayerPrefixes = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        store = new PlayerStatStore(new java.io.File(getDataFolder(), "player-stats.yml"));
        reloadPluginState();
        Bukkit.getPluginManager().registerEvents(this, this);
        if (Bukkit.getPluginManager().isPluginEnabled("Citizens")) {
            Bukkit.getPluginManager().registerEvents(new CitizensStatListener(this), this);
        }
    }

    @Override
    public void onDisable() {
        saveStore();
    }

    private void reloadPluginState() {
        reloadConfig();
        requirePlayerKill = getConfig().getBoolean("steal.require-player-kill", true);
        allowCitizensNpcs = getConfig().getBoolean("steal.allow-citizens-npcs", true);
        announceToServer = getConfig().getBoolean("steal.announce-to-server", true);
        restoreHealthOnSteal = getConfig().getBoolean("steal.restore-health-on-steal", true);
        showRulesOnJoin = getConfig().getBoolean("rules.show-on-join", true);
        disableEndCrystals = getConfig().getBoolean("restrictions.disable-end-crystals", true);
        disableRespawnAnchors = getConfig().getBoolean("restrictions.disable-respawn-anchors", true);
        rulesJoinDelayTicks = Math.max(0L, getConfig().getLong("rules.join-delay-ticks", 40L));
        serverRules = List.copyOf(getConfig().getStringList("rules.lines"));
        messagePrefix = colorize(getConfig().getString("steal.message-prefix", "&6[StatSteal]&r "));
        notificationTitleTimes = Title.Times.times(
                Duration.ofMillis(Math.max(0, getConfig().getLong("notifications.title.fade-in-millis", 300))),
                Duration.ofSeconds(Math.max(1, getConfig().getLong("notifications.title.stay-seconds", 8))),
                Duration.ofMillis(Math.max(0, getConfig().getLong("notifications.title.fade-out-millis", 700))));
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
        Bukkit.getScheduler().runTaskLater(this, () -> showPendingLosses(event.getPlayer()), 20L);
        if (showRulesOnJoin) {
            Bukkit.getScheduler().runTaskLater(this, () -> showRules(event.getPlayer()), rulesJoinDelayTicks);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onRestrictedBlockPlace(BlockPlaceEvent event) {
        if (disableRespawnAnchors && event.getBlockPlaced().getType() == Material.RESPAWN_ANCHOR) {
            event.setCancelled(true);
            showRestrictionMessage(event.getPlayer(), "Respawn Anchors are disabled on this server.");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onRestrictedEntityPlace(EntityPlaceEvent event) {
        if (disableEndCrystals && event.getEntity().getType().name().equals("END_CRYSTAL")) {
            event.setCancelled(true);
            if (event.getPlayer() != null) {
                showRestrictionMessage(event.getPlayer(), "End Crystals are disabled on this server.");
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onRestrictedInteraction(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (disableEndCrystals && event.getItem() != null && event.getItem().getType() == Material.END_CRYSTAL) {
            event.setCancelled(true);
            showRestrictionMessage(event.getPlayer(), "End Crystals are disabled on this server.");
            return;
        }
        if (disableRespawnAnchors && event.getClickedBlock() != null
                && event.getClickedBlock().getType() == Material.RESPAWN_ANCHOR) {
            event.setCancelled(true);
            showRestrictionMessage(event.getPlayer(), "Respawn Anchors are disabled on this server.");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onRestrictedDispense(BlockDispenseEvent event) {
        Material type = event.getItem().getType();
        if ((disableEndCrystals && type == Material.END_CRYSTAL)
                || (disableRespawnAnchors && type == Material.RESPAWN_ANCHOR)) {
            event.setCancelled(true);
        }
    }

    private void showRestrictionMessage(Player player, String message) {
        int currentTick = Bukkit.getCurrentTick();
        Integer previousTick = restrictionMessageTicks.put(player.getUniqueId(), currentTick);
        if (previousTick == null || currentTick - previousTick >= 40) {
            player.sendActionBar(Component.text(message, NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        processedDeathTicks.remove(event.getPlayer().getUniqueId());
        Bukkit.getScheduler().runTask(this, () -> {
            applyStats(event.getPlayer(), true);
            showPendingLosses(event.getPlayer());
        });
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getPlayer();
        Player killer = victim.getKiller();
        boolean victimNpc = isNpc(victim);
        boolean killerNpc = killer != null && isNpc(killer);
        if ((victimNpc || killerNpc) && !allowCitizensNpcs) {
            return;
        }
        if ((!victimNpc && isExcludedPlayer(victim))
                || (killer != null && !killerNpc && isExcludedPlayer(killer))) {
            return;
        }

        int currentTick = Bukkit.getCurrentTick();
        Integer previousTick = processedDeathTicks.put(victim.getUniqueId(), currentTick);
        if (previousTick != null && previousTick == currentTick) {
            return;
        }

        boolean realPlayerKill = killer != null
                && !killer.equals(victim);

        if (realPlayerKill) {
            handlePlayerKill(killer, victim);
            return;
        }

        if (!requirePlayerKill) {
            handleDeathPenalty(victim, null);
        }
    }

    private void handlePlayerKill(Player killer, Player victim) {
        List<ConfiguredStat> enabledStats = new ArrayList<>();
        for (ConfiguredStat config : configuredStats.values()) {
            if (config.enabled()) {
                enabledStats.add(config);
            }
        }

        if (enabledStats.isEmpty()) {
            return;
        }

        ConfiguredStat chosen = enabledStats.get(ThreadLocalRandom.current().nextInt(enabledStats.size()));
        boolean killerGained = getLevel(killer, chosen.stat()) < MAX_LEVEL;
        if (killerGained) {
            updateLevel(killer, chosen.stat(), 1);
        }
        updateLevel(victim, chosen.stat(), -1);
        if (!isNpc(victim)) {
            store.addPendingLoss(victim.getUniqueId(), new PendingStatLoss(
                    chosen.stat().configKey(), getLevel(victim, chosen.stat()), killer.getName()));
        }
        applyStats(killer, true);
        applyStats(victim, false);
        saveStore();

        if (killerGained && !isNpc(killer)) {
            killer.sendMessage(Component.text(messagePrefix + "You gained 1 " + chosen.displayName() + " stack ("
                    + formatChange(chosen.stat()) + ", level " + getLevel(killer, chosen.stat()) + "/" + MAX_LEVEL
                    + ") from killing " + victim.getName() + ".", NamedTextColor.GREEN));
            showStatTitle(killer, "STAT GAINED", chosen.displayName(), getLevel(killer, chosen.stat()), NamedTextColor.GREEN);
        } else if (!isNpc(killer)) {
            killer.sendMessage(Component.text(messagePrefix + "The roll selected " + chosen.displayName()
                    + ", but you are already at the +" + MAX_LEVEL + " maximum.", NamedTextColor.YELLOW));
            showStatTitle(killer, "STAT MAXED", chosen.displayName(), MAX_LEVEL, NamedTextColor.YELLOW);
        }
        if (!isNpc(victim)) {
            victim.sendMessage(Component.text(messagePrefix + "You lost 1 " + chosen.displayName()
                    + " stack to " + killer.getName() + ".", NamedTextColor.RED));
        }
        if (announceToServer) {
            String action = killerGained ? " stole 1 " : " rolled a maxed ";
            Bukkit.broadcast(Component.text(messagePrefix + killer.getName() + action + chosen.displayName()
                    + " stack; " + victim.getName() + " lost 1.", NamedTextColor.GOLD));
        }
        maybeBanIfAllCategoriesBottomedOut(victim);
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
        String source = killer == null ? "death" : killer.getName();
        store.addPendingLoss(victim.getUniqueId(), new PendingStatLoss(
                chosen.stat().configKey(), getLevel(victim, chosen.stat()), source));
        applyStats(victim, false);
        saveStore();

        victim.sendMessage(Component.text(messagePrefix + "You lost 1 " + chosen.displayName() + " stack to " + source + ".", NamedTextColor.RED));
        maybeBanIfAllCategoriesBottomedOut(victim);
    }

    private void showPendingLosses(Player player) {
        List<PendingStatLoss> losses = store.drainPendingLosses(player.getUniqueId());
        if (losses.isEmpty()) {
            return;
        }

        PendingStatLoss latest = losses.get(losses.size() - 1);
        String latestName = getStatDisplayName(latest.statKey());
        player.showTitle(Title.title(
                Component.text("STAT LOST", NamedTextColor.RED),
                Component.text(latestName + ": " + formatSignedLevel(latest.newLevel())
                        + " (all stats at " + MIN_LEVEL + " = ban)", NamedTextColor.YELLOW),
                notificationTitleTimes));
        player.sendMessage(Component.text(messagePrefix + "Stat changes from your last death:", NamedTextColor.GOLD));
        for (PendingStatLoss loss : losses) {
            String displayName = getStatDisplayName(loss.statKey());
            player.sendMessage(Component.text("- Lost 1 " + displayName + " to " + loss.source()
                    + ". Current level: " + loss.newLevel() + " (ban only when all stats reach " + MIN_LEVEL + ").", NamedTextColor.RED));
        }
        saveStore();
    }

    private void showStatTitle(
            Player player,
            String heading,
            String statName,
            int level,
            NamedTextColor headingColor) {
        player.showTitle(Title.title(
                Component.text(heading, headingColor),
                Component.text(statName + " is now " + formatSignedLevel(level), NamedTextColor.YELLOW),
                notificationTitleTimes));
    }

    private String getStatDisplayName(String statKey) {
        return StealableStat.fromConfigKey(statKey)
                .map(configuredStats::get)
                .filter(Objects::nonNull)
                .map(ConfiguredStat::displayName)
                .orElse(statKey);
    }

    private void maybeBanIfAllCategoriesBottomedOut(Player player) {
        if (isNpc(player)) {
            return;
        }
        boolean hasEnabledStat = false;
        for (ConfiguredStat config : configuredStats.values()) {
            if (!config.enabled()) {
                continue;
            }
            hasEnabledStat = true;
            if (getLevel(player, config.stat()) > MIN_LEVEL) {
                return;
            }
        }
        if (hasEnabledStat) {
            banForAllStatsBottomedOut(player);
        }
    }

    private void banForAllStatsBottomedOut(Player player) {
        Date expires = Date.from(Instant.now().plus(Duration.ofDays(30)));
        String reason = "You reached " + MIN_LEVEL + " in every StatSteal category.";
        Bukkit.getBanList(BanList.Type.NAME).addBan(
                player.getName(),
                reason,
                expires,
                "StatStealSmp");
        player.kick(Component.text(reason + " You are banned for 30 days.", NamedTextColor.RED));
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

    private boolean isNpc(Player player) {
        return player.hasMetadata("NPC");
    }

    void applyStoredStatsToNpc(Player npcPlayer) {
        if (!allowCitizensNpcs) {
            return;
        }
        Bukkit.getScheduler().runTask(this, () -> applyStats(npcPlayer, true));
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
        if (command.getName().equalsIgnoreCase("rules")) {
            showRules(sender);
            return true;
        }

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

    private void showRules(CommandSender sender) {
        sender.sendMessage(Component.text("SERVER RULES", NamedTextColor.GOLD));
        for (int index = 0; index < serverRules.size(); index++) {
            sender.sendMessage(Component.text((index + 1) + ". " + serverRules.get(index), NamedTextColor.YELLOW));
        }
        sender.sendMessage(Component.text("Use /rules to see this list again.", NamedTextColor.GRAY));
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

    private static String formatSignedLevel(int level) {
        return level > 0 ? "+" + level : Integer.toString(level);
    }

    private static String colorize(String input) {
        return Objects.requireNonNullElse(input, "").replace("&6", "").replace("&r", "");
    }

    private static String formatChange(StealableStat stat) {
        return switch (stat) {
            case MAX_HEALTH -> "1 heart";
            case ATTACK_DAMAGE -> "1 damage point";
            case MOVEMENT_SPEED -> "5% speed";
            case ARMOR -> "1 armor point";
            case ATTACK_SPEED, LUCK -> trimDouble(0);
        };
    }
}
