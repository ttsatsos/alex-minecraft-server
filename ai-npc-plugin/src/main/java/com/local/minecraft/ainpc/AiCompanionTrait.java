package com.local.minecraft.ainpc;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.persistence.Persist;
import net.citizensnpcs.api.trait.Trait;
import net.citizensnpcs.api.trait.TraitName;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.json.JSONArray;
import org.json.JSONObject;
import org.mcmonkey.sentinel.SentinelTrait;

@TraitName("aicompanion")
public class AiCompanionTrait extends Trait {
    @Persist("ownerName")
    private String ownerName = "";

    @Persist("modelOverride")
    private String modelOverride = "";

    @Persist("personaPrompt")
    private String personaPrompt = "";

    @Persist("followTarget")
    private String followTarget = "";

    @Persist("combatEnabled")
    private boolean combatEnabled = true;

    @Persist("guardOwner")
    private boolean guardOwner = true;

    @Persist("attackMonsters")
    private boolean attackMonsters = true;

    @Persist("attackPlayers")
    private boolean attackPlayers = false;

    @Persist("wildMode")
    private boolean wildMode = false;

    @Persist("roamAnchorWorld")
    private String roamAnchorWorld = "";

    @Persist("roamAnchorX")
    private double roamAnchorX;

    @Persist("roamAnchorY")
    private double roamAnchorY;

    @Persist("roamAnchorZ")
    private double roamAnchorZ;

    @Persist("respawnWorld")
    private String respawnWorld = "";

    @Persist("respawnX")
    private double respawnX;

    @Persist("respawnY")
    private double respawnY;

    @Persist("respawnZ")
    private double respawnZ;

    private long nextRoamTick;
    private final Map<UUID, Deque<JSONObject>> memory = new HashMap<>();

    public AiCompanionTrait() {
        super("aicompanion");
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName == null ? "" : ownerName;
    }

    public void setModelOverride(String modelOverride) {
        this.modelOverride = modelOverride == null ? "" : modelOverride;
    }

    public void setPersonaPrompt(String personaPrompt) {
        this.personaPrompt = personaPrompt == null ? "" : personaPrompt;
    }

    public String getFollowTarget() {
        return followTarget;
    }

    public String getEffectiveModel() {
        return modelOverride == null || modelOverride.isBlank()
                ? LocalAiNpcPlugin.getInstance().getConfig().getString("ollama.model", "qwen2.5:7b")
                : modelOverride;
    }

    public boolean isCombatEnabled() {
        return combatEnabled;
    }

    public void setCombatEnabled(boolean combatEnabled) {
        this.combatEnabled = combatEnabled;
    }

    public boolean isGuardOwnerEnabled() {
        return guardOwner;
    }

    public boolean isAttackMonstersEnabled() {
        return attackMonsters;
    }

    public boolean isWildMode() {
        return wildMode;
    }

    public boolean shouldHandle(Player player, String message) {
        String trimmed = message.trim();
        String lower = trimmed.toLowerCase();
        String npcName = npc.getName().toLowerCase();
        return lower.startsWith("!" + npcName)
                || lower.startsWith(npcName + ",")
                || lower.startsWith(npcName + ":")
                || lower.contains(" " + npcName + " ");
    }

    public void handlePlayerMessage(Player player, String rawMessage) {
        String message = stripAddressing(rawMessage);
        if (message.isBlank()) {
            return;
        }

        if (handleLocalCommand(player, message)) {
            return;
        }

        remember(player.getUniqueId(), "user", player.getName() + ": " + message);
        Bukkit.getScheduler().runTaskAsynchronously(LocalAiNpcPlugin.getInstance(), () -> {
            try {
                JSONArray messages = buildMessages(player);
                String reply = LocalAiNpcPlugin.getInstance()
                        .getOllamaClient()
                        .chat(getEffectiveModel(), messages);
                if (reply == null || reply.isBlank()) {
                    reply = "I'm thinking, but I don't have a good answer yet.";
                }
                remember(player.getUniqueId(), "assistant", reply);
                final String finalReply = reply;
                Bukkit.getScheduler().runTask(LocalAiNpcPlugin.getInstance(), () -> sayNearby(finalReply));
            } catch (Exception exception) {
                LocalAiNpcPlugin.getInstance().getLogger().warning("Ollama chat failed for NPC " + npc.getName() + ": " + exception.getMessage());
                Bukkit.getScheduler().runTask(LocalAiNpcPlugin.getInstance(),
                        () -> sayNearby("I hit a snag thinking that through. Try me again in a moment."));
            }
        });
    }

    public void tickFollow() {
        if (wildMode || followTarget == null || followTarget.isBlank() || !npc.isSpawned()) {
            return;
        }
        Player player = Bukkit.getPlayerExact(followTarget);
        if (player == null || !player.isOnline() || player.getWorld() != npc.getStoredLocation().getWorld()) {
            return;
        }

        Location npcLocation = npc.getStoredLocation();
        if (npcLocation.distanceSquared(player.getLocation()) > 4) {
            npc.getNavigator().setTarget(player, true);
        }
    }

    public void tickCombat() {
        if (!npc.isSpawned() || !combatEnabled) {
            return;
        }
        if (guardOwner && !ownerName.isBlank()) {
            Player owner = Bukkit.getPlayerExact(ownerName);
            if (owner != null && owner.isOnline()) {
                SentinelTrait sentinel = getSentinelTrait();
                if (sentinel != null && !owner.getUniqueId().equals(sentinel.getGuarding())) {
                    configureSentinelCombat();
                }
            }
        }
    }

    public void tickWildBehavior(long currentTick) {
        if (!wildMode || !npc.isSpawned()) {
            return;
        }
        SentinelTrait sentinel = getSentinelTrait();
        if (sentinel != null && sentinel.chasing != null && !sentinel.chasing.isDead()) {
            return;
        }
        if (npc.getNavigator().isNavigating()) {
            return;
        }
        if (currentTick < nextRoamTick) {
            return;
        }
        Location anchor = getRoamAnchor();
        if (anchor == null) {
            return;
        }
        int radius = LocalAiNpcPlugin.getInstance().getWildRoamRadius();
        int interval = LocalAiNpcPlugin.getInstance().getWildRoamIntervalTicks();
        Location target = anchor.clone().add(
                ThreadLocalRandom.current().nextInt(-radius, radius + 1),
                0,
                ThreadLocalRandom.current().nextInt(-radius, radius + 1));
        World world = target.getWorld();
        if (world == null) {
            return;
        }
        target.setY(world.getHighestBlockYAt(target) + 1.0);
        npc.getNavigator().setTarget(target);
        nextRoamTick = currentTick + interval;
    }

    @EventHandler
    public void onRightClick(NPCRightClickEvent event) {
        if (event.getNPC() != this.getNPC()) {
            return;
        }
        Player clicker = event.getClicker();
        if (!ownerName.isBlank() && !ownerName.equalsIgnoreCase(clicker.getName())) {
            clicker.sendMessage(ChatColor.RED + npc.getName() + " only listens to " + ownerName + ".");
            return;
        }
        clicker.sendMessage(ChatColor.YELLOW + npc.getName() + ChatColor.GRAY + ": Try saying \"" + npc.getName()
                + ", follow me\", \"" + npc.getName() + ", guard me\", or \"!" + npc.getName() + " hello\".");
    }

    private boolean handleLocalCommand(Player player, String message) {
        String lower = message.toLowerCase();
        boolean ownerAllowed = ownerName.isBlank() || ownerName.equalsIgnoreCase(player.getName());
        if (!ownerAllowed) {
            return false;
        }

        if (lower.equals("follow me") || lower.equals("follow")) {
            wildMode = false;
            followTarget = player.getName();
            combatEnabled = true;
            guardOwner = true;
            configureSentinelCombat();
            sayNearby("Alright " + player.getName() + ", I'll follow you.");
            return true;
        }
        if (lower.equals("guard me") || lower.equals("protect me") || lower.equals("defend me")) {
            wildMode = false;
            followTarget = player.getName();
            combatEnabled = true;
            guardOwner = true;
            attackMonsters = true;
            configureSentinelCombat();
            sayNearby("I've got your back.");
            return true;
        }
        if (lower.equals("attack monsters") || lower.equals("hunt monsters") || lower.equals("fight mobs")) {
            combatEnabled = true;
            attackMonsters = true;
            configureSentinelCombat();
            sayNearby("I'll engage nearby monsters.");
            return true;
        }
        if (lower.equals("stand down") || lower.equals("stop fighting") || lower.equals("be passive")) {
            combatEnabled = false;
            guardOwner = false;
            attackPlayers = false;
            configureSentinelCombat();
            sayNearby("Standing down.");
            return true;
        }
        if (lower.equals("come here") || lower.equals("come")) {
            wildMode = false;
            npc.getNavigator().setTarget(player, false);
            sayNearby("On my way.");
            return true;
        }
        if (lower.equals("stop")) {
            followTarget = "";
            npc.getNavigator().cancelNavigation();
            sayNearby("Stopping here.");
            return true;
        }
        if (lower.equals("where are you") || lower.equals("where are you?")) {
            Location loc = npc.getStoredLocation();
            sayNearby("I'm at " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ".");
            return true;
        }
        return false;
    }

    public void applyDefaultCombatProfile() {
        LocalAiNpcPlugin plugin = LocalAiNpcPlugin.getInstance();
        combatEnabled = plugin.isCombatEnabledByDefault();
        guardOwner = plugin.isGuardOwnerByDefault();
        attackMonsters = plugin.isAttackMonstersByDefault();
        attackPlayers = false;
        wildMode = false;
        configureSentinelCombat();
    }

    public void applyWildProfile(Location worldSpawn) {
        ownerName = "";
        followTarget = "";
        wildMode = true;
        combatEnabled = true;
        guardOwner = false;
        attackMonsters = true;
        attackPlayers = true;
        setRoamAnchor(worldSpawn);
        setRespawnPoint(worldSpawn);
        nextRoamTick = 0L;
        configureSentinelCombat();
    }

    public void configureSentinelCombat() {
        SentinelTrait sentinel = getSentinelTrait();
        if (sentinel == null) {
            return;
        }

        LocalAiNpcPlugin plugin = LocalAiNpcPlugin.getInstance();
        sentinel.fightback = combatEnabled;
        sentinel.closeChase = combatEnabled;
        sentinel.rangedChase = false;
        sentinel.autoswitch = true;
        sentinel.realistic = true;
        sentinel.allowKnockback = true;
        sentinel.invincible = false;
        sentinel.range = plugin.getSentinelRange();
        sentinel.chaseRange = plugin.getSentinelChaseRange();
        sentinel.damage = plugin.getSentinelDamage();
        sentinel.speed = plugin.getSentinelSpeed();
        sentinel.guardDistanceMinimum = plugin.getSentinelGuardDistance();
        sentinel.guardSelectionRange = plugin.getSentinelGuardSelectionRange();
        sentinel.respawnTime = plugin.getSentinelRespawnTimeTicks();
        sentinel.spawnPoint = getRespawnPoint();

        npc.setProtected(false);

        if (attackMonsters) {
            sentinel.addTarget("monsters");
        } else {
            sentinel.removeTarget("monsters");
        }

        if (attackPlayers) {
            sentinel.addTarget("players");
        } else {
            sentinel.removeTarget("players");
        }

        if (combatEnabled && guardOwner && !ownerName.isBlank()) {
            Player owner = Bukkit.getPlayerExact(ownerName);
            if (owner != null) {
                sentinel.setGuarding(owner.getUniqueId());
            } else {
                sentinel.setGuarding((UUID) null);
            }
        } else {
            sentinel.setGuarding((UUID) null);
        }

        if (npc.getEntity() instanceof LivingEntity livingEntity) {
            AttributeInstance maxHealth = livingEntity.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealth != null) {
                maxHealth.setBaseValue(plugin.getSentinelHealth());
                livingEntity.setHealth(Math.min(livingEntity.getHealth(), plugin.getSentinelHealth()));
            }
        }
    }

    private void setRoamAnchor(Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        roamAnchorWorld = location.getWorld().getName();
        roamAnchorX = location.getX();
        roamAnchorY = location.getY();
        roamAnchorZ = location.getZ();
    }

    private Location getRoamAnchor() {
        if (roamAnchorWorld == null || roamAnchorWorld.isBlank()) {
            return getRespawnPoint();
        }
        World world = Bukkit.getWorld(roamAnchorWorld);
        if (world == null) {
            return null;
        }
        return new Location(world, roamAnchorX, roamAnchorY, roamAnchorZ);
    }

    private void setRespawnPoint(Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        respawnWorld = location.getWorld().getName();
        respawnX = location.getX();
        respawnY = location.getY();
        respawnZ = location.getZ();
    }

    private Location getRespawnPoint() {
        if (respawnWorld == null || respawnWorld.isBlank()) {
            return npc.getStoredLocation();
        }
        World world = Bukkit.getWorld(respawnWorld);
        if (world == null) {
            return npc.getStoredLocation();
        }
        return new Location(world, respawnX, respawnY, respawnZ);
    }

    private JSONArray buildMessages(Player player) {
        JSONArray messages = new JSONArray();
        String systemPrompt = LocalAiNpcPlugin.getInstance().getConfig().getString("ollama.system-prompt", "");
        if (!systemPrompt.isBlank()) {
            messages.put(new JSONObject().put("role", "system").put("content", systemPrompt));
        }
        if (!personaPrompt.isBlank()) {
            messages.put(new JSONObject().put("role", "system").put("content", "NPC persona: " + personaPrompt));
        }
        messages.put(new JSONObject().put("role", "system").put("content",
                "You are speaking as the in-game NPC named " + npc.getName() + ". Keep responses under 2 short sentences."));

        Deque<JSONObject> history = memory.get(player.getUniqueId());
        if (history != null) {
            for (JSONObject item : history) {
                messages.put(item);
            }
        }
        return messages;
    }

    private void remember(UUID playerId, String role, String content) {
        Deque<JSONObject> history = memory.computeIfAbsent(playerId, ignored -> new ArrayDeque<>());
        history.addLast(new JSONObject().put("role", role).put("content", content));
        int maxSize = LocalAiNpcPlugin.getInstance().getMemoryMessages();
        while (history.size() > maxSize) {
            history.removeFirst();
        }
    }

    private String stripAddressing(String raw) {
        String value = raw.trim();
        String lower = value.toLowerCase();
        String npcName = npc.getName().toLowerCase();

        if (lower.startsWith("!" + npcName)) {
            return value.substring(npcName.length() + 1).trim();
        }
        if (lower.startsWith(npcName + ",")) {
            return value.substring(npcName.length() + 1).trim();
        }
        if (lower.startsWith(npcName + ":")) {
            return value.substring(npcName.length() + 1).trim();
        }
        return value;
    }

    private void sayNearby(String text) {
        if (!npc.isSpawned()) {
            return;
        }
        Location location = npc.getStoredLocation();
        int radius = LocalAiNpcPlugin.getInstance().getChatRadius();
        String rendered = ChatColor.YELLOW + "<" + npc.getName() + "> " + ChatColor.WHITE + text;
        for (Player nearby : location.getWorld().getPlayers()) {
            if (nearby.getLocation().distanceSquared(location) <= (long) radius * radius) {
                nearby.sendMessage(rendered);
            }
        }
    }

    private SentinelTrait getSentinelTrait() {
        if (!LocalAiNpcPlugin.getInstance().isSentinelAvailable()) {
            return null;
        }
        return npc.getOrAddTrait(SentinelTrait.class);
    }
}
