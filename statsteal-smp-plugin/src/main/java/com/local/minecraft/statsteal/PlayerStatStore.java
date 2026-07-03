package com.local.minecraft.statsteal;

import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class PlayerStatStore {
    private final File file;
    private final Map<UUID, EnumMap<StealableStat, Integer>> values = new HashMap<>();

    public PlayerStatStore(File file) {
        this.file = file;
    }

    public void load() {
        values.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) {
            return;
        }
        for (String uuidText : players.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(uuidText);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            ConfigurationSection section = players.getConfigurationSection(uuidText);
            if (section == null) {
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

    public void save() throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection players = yaml.createSection("players");
        for (Map.Entry<UUID, EnumMap<StealableStat, Integer>> entry : values.entrySet()) {
            ConfigurationSection playerSection = players.createSection(entry.getKey().toString());
            for (Map.Entry<StealableStat, Integer> statEntry : entry.getValue().entrySet()) {
                playerSection.set(statEntry.getKey().configKey(), statEntry.getValue());
            }
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
    }
}
