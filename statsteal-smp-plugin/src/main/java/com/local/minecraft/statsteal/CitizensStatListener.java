package com.local.minecraft.statsteal;

import net.citizensnpcs.api.event.NPCSpawnEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

final class CitizensStatListener implements Listener {
    private final StatStealPlugin plugin;

    CitizensStatListener(StatStealPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onNpcSpawn(NPCSpawnEvent event) {
        if (event.getNPC().getEntity() instanceof Player player) {
            plugin.applyStoredStatsToNpc(player);
        }
    }
}
