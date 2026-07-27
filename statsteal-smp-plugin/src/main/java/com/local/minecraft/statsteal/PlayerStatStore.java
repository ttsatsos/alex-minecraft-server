package com.local.minecraft.statsteal;

import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class PlayerStatStore {
    private final File file;
    private final Map<UUID, EnumMap<StealableStat, Integer>> values = new HashMap<>();
    private final Map<UUID, List<PendingStatLoss>> pendingLosses = new HashMap<>();

    public PlayerStatStore(File file) {
        this.file = file;
    }

    public void load() {
        values.clear();
        pendingLosses.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players != null) {
            for (String uuidText : players.getKeys(false)) {
                UUID uuid = parseUuid(uuidText);
                ConfigurationSection section = players.getConfigurationSection(uuidText);
                if (uuid == null || section == null) {
                    continue;
                }
                EnumMap<StealableStat, Integer> stats = new EnumMap<>(StealableStat.class);
                for (String statKey : section.getKeys(false)) {
                    StealableStat.fromConfigKey(statKey).ifPresent(stat ->
                            stats.put(stat, section.getInt(statKey)));
                }
                values.put(uuid, stats);
            }
        }

        ConfigurationSection notifications = yaml.getConfigurationSection("pending-losses");
        if (notifications != null) {
            for (String uuidText : notifications.getKeys(false)) {
                UUID uuid = parseUuid(uuidText);
                if (uuid == null) {
                    continue;
                }
                List<PendingStatLoss> losses = notifications.getMapList(uuidText).stream()
                        .map(PlayerStatStore::parsePendingLoss)
                        .filter(Objects::nonNull)
                        .toList();
                if (!losses.isEmpty()) {
                    pendingLosses.put(uuid, new java.util.ArrayList<>(losses));
                }
            }
        }
    }

    public void save() throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection players = yaml.createSection("players");
        for (Map.Entry<UUID, EnumMap<StealableStat, Integer>> entry : values.entrySet()) {
            ConfigurationSection playerSection = players.createSection(entry.getKey().toString());
            for (Map.Entry<StealableStat, Integer> statEntry : entry.getValue().entrySet()) {
                playerSection.set(statEntry.getKey().configKey(), statEntry.getValue());
            }
        }
        ConfigurationSection notifications = yaml.createSection("pending-losses");
        for (Map.Entry<UUID, List<PendingStatLoss>> entry : pendingLosses.entrySet()) {
            List<Map<String, Object>> serialized = entry.getValue().stream()
                    .map(loss -> Map.<String, Object>of(
                            "stat", loss.statKey(),
                            "level", loss.newLevel(),
                            "source", loss.source()))
                    .toList();
            notifications.set(entry.getKey().toString(), serialized);
        }
        yaml.save(file);
    }

    public int getLevel(UUID playerId, StealableStat stat) {
        return values.getOrDefault(playerId, new EnumMap<>(StealableStat.class)).getOrDefault(stat, 0);
    }

    public void setLevel(UUID playerId, StealableStat stat, int value) {
        values.computeIfAbsent(playerId, ignored -> new EnumMap<>(StealableStat.class)).put(stat, value);
    }

    public EnumMap<StealableStat, Integer> getAll(UUID playerId) {
        return new EnumMap<>(values.getOrDefault(playerId, new EnumMap<>(StealableStat.class)));
    }

    public void reset(UUID playerId) {
        values.remove(playerId);
    }

    public void clearAll() {
        values.clear();
        pendingLosses.clear();
    }

    public void addPendingLoss(UUID playerId, PendingStatLoss loss) {
        pendingLosses.computeIfAbsent(playerId, ignored -> new java.util.ArrayList<>()).add(loss);
    }

    public List<PendingStatLoss> drainPendingLosses(UUID playerId) {
        List<PendingStatLoss> losses = pendingLosses.remove(playerId);
        return losses == null ? List.of() : List.copyOf(losses);
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static PendingStatLoss parsePendingLoss(Map<?, ?> value) {
        String statKey = Objects.toString(value.get("stat"), "");
        Object levelValue = value.get("level");
        if (statKey.isBlank() || !(levelValue instanceof Number number)) {
            return null;
        }
        return new PendingStatLoss(statKey, number.intValue(), Objects.toString(value.get("source"), "another player"));
    }
}
