package com.local.minecraft.statsteal;

import java.util.Locale;
import java.util.Optional;
import org.bukkit.attribute.Attribute;

public enum StealableStat {
    MAX_HEALTH("max-health", Attribute.MAX_HEALTH),
    ATTACK_DAMAGE("attack-damage", Attribute.ATTACK_DAMAGE),
    MOVEMENT_SPEED("movement-speed", Attribute.MOVEMENT_SPEED),
    ATTACK_SPEED("attack-speed", Attribute.ATTACK_SPEED),
    ARMOR("armor", Attribute.ARMOR),
    LUCK("luck", Attribute.LUCK);

    private final String configKey;
    private final Attribute attribute;

    StealableStat(String configKey, Attribute attribute) {
        this.configKey = configKey;
        this.attribute = attribute;
    }

    public String configKey() {
        return configKey;
    }

    public Attribute attribute() {
        return attribute;
    }

    public static Optional<StealableStat> fromConfigKey(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        for (StealableStat stat : values()) {
            if (stat.configKey.equals(normalized)) {
                return Optional.of(stat);
            }
        }
        return Optional.empty();
    }
}
