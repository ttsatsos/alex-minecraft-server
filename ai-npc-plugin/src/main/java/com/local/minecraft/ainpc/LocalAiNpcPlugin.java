package com.local.minecraft.ainpc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Level;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.trait.TraitInfo;
import net.citizensnpcs.api.trait.trait.Equipment;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class LocalAiNpcPlugin extends JavaPlugin implements Listener {
    private static final List<BotPreset> CAST_PRESETS = List.of(
            new BotPreset("Nova", false, "A curious explorer who loves discovering strange places."),
            new BotPreset("Brick", false, "A brave, dependable guard who speaks plainly."),
            new BotPreset("Echo", false, "A friendly storyteller who exaggerates adventures for fun."),
            new BotPreset("Sparks", false, "A playful prankster who jokes but never bullies anyone."),
            new BotPreset("Willow", false, "A calm, kind teammate who tries to help everyone."),
            new BotPreset("Atlas", false, "A bold adventurer who always wants to explore farther."),
            new BotPreset("Pixel", false, "A creative builder who notices interesting structures."),
            new BotPreset("Milo", false, "A cautious scout who warns others about danger."),
            new BotPreset("Rogue", true, "A competitive rival who boasts after winning a fight."),
            new BotPreset("Vex", true, "A clever rival who challenges strong players and respects good fights."));

    private static LocalAiNpcPlugin instance;

    private OllamaClient ollamaClient;
    private int chatRadius;
    private int memoryMessages;
    private int followDistance;
    private int movementIntervalTicks;
    private boolean sentinelAvailable;
    private boolean combatEnabledByDefault;
    private boolean guardOwnerByDefault;
    private boolean attackMonstersByDefault;
    private double sentinelRange;
    private double sentinelChaseRange;
    private double sentinelHealth;
    private double sentinelDamage;
    private double sentinelSpeed;
    private double sentinelGuardDistance;
    private double sentinelGuardSelectionRange;
    private long sentinelRespawnTimeTicks;
    private int wildRoamRadius;
    private int wildRoamIntervalTicks;

    public static LocalAiNpcPlugin getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        reloadSettings();

        Plugin citizens = getServer().getPluginManager().getPlugin("Citizens");
        if (citizens == null || !citizens.isEnabled()) {
            getLogger().severe("Citizens is required for LocalAiNpc to work.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        Plugin sentinel = getServer().getPluginManager().getPlugin("Sentinel");
        sentinelAvailable = sentinel != null && sentinel.isEnabled();
        if (!sentinelAvailable) {
            getLogger().warning("Sentinel is not enabled. AI companions will stay conversational only.");
        }

        CitizensAPI.getTraitFactory().registerTrait(TraitInfo.create(AiCompanionTrait.class));
        getServer().getPluginManager().registerEvents(this, this);

        Bukkit.getScheduler().runTaskTimer(this, this::tickCompanions, 20L, movementIntervalTicks);
        getLogger().info("LocalAiNpc enabled.");
    }

    public void reloadSettings() {
        reloadConfig();
        String baseUrl = getConfig().getString("ollama.base-url", "http://127.0.0.1:11434");
        String model = getConfig().getString("ollama.model", "qwen2.5:7b");
        int timeoutSeconds = getConfig().getInt("ollama.timeout-seconds", 45);
        String systemPrompt = getConfig().getString("ollama.system-prompt", "");
        chatRadius = getConfig().getInt("npc.chat-radius", 24);
        memoryMessages = getConfig().getInt("npc.memory-messages", 8);
        followDistance = getConfig().getInt("npc.follow-distance", 3);
        movementIntervalTicks = getConfig().getInt("npc.movement-interval-ticks", 20);
        combatEnabledByDefault = getConfig().getBoolean("npc.combat-enabled-by-default", true);
        guardOwnerByDefault = getConfig().getBoolean("npc.guard-owner-by-default", true);
        attackMonstersByDefault = getConfig().getBoolean("npc.attack-monsters-by-default", true);
        sentinelRange = getConfig().getDouble("npc.sentinel-range", 16);
        sentinelChaseRange = getConfig().getDouble("npc.sentinel-chase-range", 24);
        sentinelHealth = getConfig().getDouble("npc.sentinel-health", 40);
        sentinelDamage = getConfig().getDouble("npc.sentinel-damage", 5);
        sentinelSpeed = getConfig().getDouble("npc.sentinel-speed", 1.2);
        sentinelGuardDistance = getConfig().getDouble("npc.sentinel-guard-distance", 4);
        sentinelGuardSelectionRange = getConfig().getDouble("npc.sentinel-guard-selection-range", 12);
        sentinelRespawnTimeTicks = getConfig().getLong("npc.sentinel-respawn-time-ticks", 100L);
        wildRoamRadius = getConfig().getInt("npc.wild-roam-radius", 32);
        wildRoamIntervalTicks = getConfig().getInt("npc.wild-roam-interval-ticks", 100);
        ollamaClient = new OllamaClient(baseUrl, model, timeoutSeconds, systemPrompt);
    }

    public OllamaClient getOllamaClient() {
        return ollamaClient;
    }

    public int getChatRadius() {
        return chatRadius;
    }

    public int getMemoryMessages() {
        return memoryMessages;
    }

    public int getFollowDistance() {
        return followDistance;
    }

    public boolean isSentinelAvailable() {
        return sentinelAvailable;
    }

    public boolean isCombatEnabledByDefault() {
        return combatEnabledByDefault;
    }

    public boolean isGuardOwnerByDefault() {
        return guardOwnerByDefault;
    }

    public boolean isAttackMonstersByDefault() {
        return attackMonstersByDefault;
    }

    public double getSentinelRange() {
        return sentinelRange;
    }

    public double getSentinelChaseRange() {
        return sentinelChaseRange;
    }

    public double getSentinelHealth() {
        return sentinelHealth;
    }

    public double getSentinelDamage() {
        return sentinelDamage;
    }

    public double getSentinelSpeed() {
        return sentinelSpeed;
    }

    public double getSentinelGuardDistance() {
        return sentinelGuardDistance;
    }

    public double getSentinelGuardSelectionRange() {
        return sentinelGuardSelectionRange;
    }

    public long getSentinelRespawnTimeTicks() {
        return sentinelRespawnTimeTicks;
    }

    public int getWildRoamRadius() {
        return wildRoamRadius;
    }

    public int getWildRoamIntervalTicks() {
        return wildRoamIntervalTicks;
    }

    @EventHandler
    public void onAsyncChat(AsyncPlayerChatEvent event) {
        String message = event.getMessage().trim();
        if (message.isEmpty()) {
            return;
        }

        List<AiCompanionTrait> targets = new ArrayList<>();
        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            AiCompanionTrait trait = npc.getTraitNullable(AiCompanionTrait.class);
            if (trait == null || !npc.isSpawned()) {
                continue;
            }
            if (!Objects.equals(npc.getStoredLocation().getWorld(), event.getPlayer().getWorld())) {
                continue;
            }
            if (npc.getStoredLocation().distanceSquared(event.getPlayer().getLocation()) > (long) chatRadius * chatRadius) {
                continue;
            }
            if (trait.shouldHandle(event.getPlayer(), message)) {
                targets.add(trait);
            }
        }

        for (AiCompanionTrait trait : targets) {
            trait.handlePlayerMessage(event.getPlayer(), message);
        }
    }

    private void tickCompanions() {
        long currentTick = Bukkit.getCurrentTick();
        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            AiCompanionTrait trait = npc.getTraitNullable(AiCompanionTrait.class);
            if (trait != null) {
                trait.tickFollow();
                trait.tickCombat();
                trait.tickWildBehavior(currentTick);
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("bots")) {
            return handleBotsCommand(sender, args);
        }
        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /ainpc <create|createwild|bind|owner|model|prompt|combat|status|reload>");
            return true;
        }

        try {
            switch (args[0].toLowerCase()) {
                case "create" -> {
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage("This command is only for players.");
                        return true;
                    }
                    if (args.length < 2) {
                        player.sendMessage(ChatColor.RED + "Usage: /ainpc create <name>");
                        return true;
                    }
                    String name = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
                    NPC npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, name);
                    npc.spawn(player.getLocation());
                    npc.getOrAddTrait(Equipment.class);
                    AiCompanionTrait trait = npc.getOrAddTrait(AiCompanionTrait.class);
                    trait.setOwnerName(player.getName());
                    trait.applyDefaultCombatProfile();
                    player.sendMessage(ChatColor.GREEN + "Created AI NPC " + name + " with id " + npc.getId());
                    return true;
                }
                case "createwild" -> {
                    if (args.length < 2) {
                        sender.sendMessage(ChatColor.RED + "Usage: /ainpc createwild <name>");
                        return true;
                    }
                    String name = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
                    World world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().getFirst();
                    if (world == null) {
                        sender.sendMessage(ChatColor.RED + "No world is loaded.");
                        return true;
                    }
                    Location spawn = world.getSpawnLocation();
                    NPC npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, name);
                    npc.spawn(spawn);
                    npc.getOrAddTrait(Equipment.class);
                    AiCompanionTrait trait = npc.getOrAddTrait(AiCompanionTrait.class);
                    trait.applyWildProfile(spawn);
                    sender.sendMessage(ChatColor.GREEN + "Created wild AI NPC " + name + " with id " + npc.getId() + " at world spawn.");
                    return true;
                }
                case "bind" -> {
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage("This command is only for players.");
                        return true;
                    }
                    if (args.length != 2) {
                        player.sendMessage(ChatColor.RED + "Usage: /ainpc bind <npc-id>");
                        return true;
                    }
                    NPC npc = requireNpc(player, args[1]);
                    if (npc == null) {
                        return true;
                    }
                    npc.getOrAddTrait(Equipment.class);
                    AiCompanionTrait trait = npc.getOrAddTrait(AiCompanionTrait.class);
                    trait.applyDefaultCombatProfile();
                    player.sendMessage(ChatColor.GREEN + "Bound AI trait to NPC " + npc.getName() + ".");
                    return true;
                }
                case "owner" -> {
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage("This command is only for players.");
                        return true;
                    }
                    if (args.length != 3) {
                        player.sendMessage(ChatColor.RED + "Usage: /ainpc owner <npc-id> <player>");
                        return true;
                    }
                    NPC npc = requireNpc(player, args[1]);
                    if (npc == null) {
                        return true;
                    }
                    AiCompanionTrait trait = requireTrait(player, npc);
                    if (trait == null) {
                        return true;
                    }
                    trait.setOwnerName(args[2]);
                    trait.configureSentinelCombat();
                    player.sendMessage(ChatColor.GREEN + "Updated owner for " + npc.getName() + " to " + args[2] + ".");
                    return true;
                }
                case "model" -> {
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage("This command is only for players.");
                        return true;
                    }
                    if (args.length != 3) {
                        player.sendMessage(ChatColor.RED + "Usage: /ainpc model <npc-id> <model>");
                        return true;
                    }
                    NPC npc = requireNpc(player, args[1]);
                    if (npc == null) {
                        return true;
                    }
                    AiCompanionTrait trait = requireTrait(player, npc);
                    if (trait == null) {
                        return true;
                    }
                    trait.setModelOverride(args[2]);
                    player.sendMessage(ChatColor.GREEN + "Updated model for " + npc.getName() + " to " + args[2] + ".");
                    return true;
                }
                case "prompt" -> {
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage("This command is only for players.");
                        return true;
                    }
                    if (args.length < 3) {
                        player.sendMessage(ChatColor.RED + "Usage: /ainpc prompt <npc-id> <prompt...>");
                        return true;
                    }
                    NPC npc = requireNpc(player, args[1]);
                    if (npc == null) {
                        return true;
                    }
                    AiCompanionTrait trait = requireTrait(player, npc);
                    if (trait == null) {
                        return true;
                    }
                    String prompt = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
                    trait.setPersonaPrompt(prompt);
                    player.sendMessage(ChatColor.GREEN + "Updated persona prompt for " + npc.getName() + ".");
                    return true;
                }
                case "combat" -> {
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage("This command is only for players.");
                        return true;
                    }
                    if (args.length != 3) {
                        player.sendMessage(ChatColor.RED + "Usage: /ainpc combat <npc-id> <on|off>");
                        return true;
                    }
                    NPC npc = requireNpc(player, args[1]);
                    if (npc == null) {
                        return true;
                    }
                    AiCompanionTrait trait = requireTrait(player, npc);
                    if (trait == null) {
                        return true;
                    }
                    String value = args[2].toLowerCase();
                    if (value.equals("on")) {
                        trait.setCombatEnabled(true);
                        trait.configureSentinelCombat();
                        player.sendMessage(ChatColor.GREEN + npc.getName() + " combat is now on.");
                        return true;
                    }
                    if (value.equals("off")) {
                        trait.setCombatEnabled(false);
                        trait.configureSentinelCombat();
                        player.sendMessage(ChatColor.GREEN + npc.getName() + " combat is now off.");
                        return true;
                    }
                    player.sendMessage(ChatColor.RED + "Usage: /ainpc combat <npc-id> <on|off>");
                    return true;
                }
                case "status" -> {
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage("This command is only for players.");
                        return true;
                    }
                    if (args.length != 2) {
                        player.sendMessage(ChatColor.RED + "Usage: /ainpc status <npc-id>");
                        return true;
                    }
                    NPC npc = requireNpc(player, args[1]);
                    if (npc == null) {
                        return true;
                    }
                    AiCompanionTrait trait = requireTrait(player, npc);
                    if (trait == null) {
                        return true;
                    }
                    player.sendMessage(ChatColor.GOLD + "NPC " + npc.getName() + " (" + npc.getId() + ")");
                    player.sendMessage(ChatColor.YELLOW + "Owner: " + trait.getOwnerName());
                    player.sendMessage(ChatColor.YELLOW + "Model: " + trait.getEffectiveModel());
                    player.sendMessage(ChatColor.YELLOW + "Following: " + (trait.getFollowTarget() == null ? "none" : trait.getFollowTarget()));
                    player.sendMessage(ChatColor.YELLOW + "Combat: " + (trait.isCombatEnabled() ? "on" : "off"));
                    player.sendMessage(ChatColor.YELLOW + "Guard owner: " + (trait.isGuardOwnerEnabled() ? "yes" : "no"));
                    player.sendMessage(ChatColor.YELLOW + "Attack monsters: " + (trait.isAttackMonstersEnabled() ? "yes" : "no"));
                    player.sendMessage(ChatColor.YELLOW + "Wild mode: " + (trait.isWildMode() ? "yes" : "no"));
                    return true;
                }
                case "reload" -> {
                    reloadSettings();
                    sender.sendMessage(ChatColor.GREEN + "LocalAiNpc config reloaded.");
                    return true;
                }
                default -> {
                    sender.sendMessage(ChatColor.YELLOW + "Usage: /ainpc <create|createwild|bind|owner|model|prompt|combat|status|reload>");
                    return true;
                }
            }
        } catch (Exception exception) {
            getLogger().log(Level.SEVERE, "Command handling failed", exception);
            sender.sendMessage(ChatColor.RED + "That command failed. Check the server log.");
            return true;
        }
    }

    private boolean handleBotsCommand(CommandSender sender, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            showBotsHelp(sender);
            return true;
        }

        try {
            String action = args[0].toLowerCase(Locale.ROOT);
            if (action.equals("list")) {
                listBots(sender);
                return true;
            }
            if (action.equals("populate")) {
                Location center = sender instanceof Player player
                        ? player.getLocation()
                        : Bukkit.getWorlds().getFirst().getSpawnLocation();
                int created = populateCast(center);
                sender.sendMessage(ChatColor.GREEN + "Created " + created + " new cast member"
                        + (created == 1 ? "." : "s.") + " Existing names were left alone.");
                return true;
            }
            if (action.equals("create") || action.equals("enemy")) {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatColor.RED + "Run this command in the game.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /bots " + action + " <name>");
                    return true;
                }
                String name = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                NPC npc = createBot(name, player.getLocation(), action.equals("enemy") ? null : player);
                sender.sendMessage(ChatColor.GREEN + "Created " + npc.getName() + " (bot " + npc.getId() + ").");
                return true;
            }
            if (action.equals("freeze") || action.equals("reset")) {
                if (args.length != 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /bots " + action + " <bot|all>");
                    return true;
                }
                List<NPC> selected = selectBots(sender, args[1]);
                for (NPC npc : selected) {
                    AiCompanionTrait trait = npc.getTraitNullable(AiCompanionTrait.class);
                    if (action.equals("freeze")) {
                        trait.freeze();
                    } else {
                        trait.resetBot();
                    }
                }
                sender.sendMessage(ChatColor.GREEN + (action.equals("freeze") ? "Frozen " : "Reset ")
                        + selected.size() + " bot" + (selected.size() == 1 ? "." : "s."));
                return true;
            }
            if (action.equals("remove")) {
                if (args.length != 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /bots remove <bot>");
                    return true;
                }
                NPC npc = findBot(sender, args[1]);
                if (npc != null) {
                    String name = npc.getName();
                    npc.destroy();
                    sender.sendMessage(ChatColor.GREEN + "Removed " + name + ".");
                }
                return true;
            }
            if (action.equals("scene")) {
                if (args.length != 4 || !args[1].equalsIgnoreCase("battle")) {
                    sender.sendMessage(ChatColor.RED + "Usage: /bots scene battle <bot1> <bot2>");
                    return true;
                }
                NPC first = findBot(sender, args[2]);
                NPC second = findBot(sender, args[3]);
                if (first != null && second != null && first != second) {
                    first.getTrait(AiCompanionTrait.class).attackNpc(second);
                    second.getTrait(AiCompanionTrait.class).attackNpc(first);
                    sender.sendMessage(ChatColor.GREEN + "Battle started: " + first.getName() + " vs. " + second.getName() + ".");
                }
                return true;
            }

            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "Run this command in the game.");
                return true;
            }
            if (args.length < 2) {
                showBotsHelp(sender);
                return true;
            }
            NPC npc = findBot(sender, args[1]);
            if (npc == null) {
                return true;
            }
            AiCompanionTrait trait = npc.getTrait(AiCompanionTrait.class);
            switch (action) {
                case "follow" -> {
                    trait.follow(player);
                    sender.sendMessage(ChatColor.GREEN + npc.getName() + " is following you.");
                }
                case "guard" -> {
                    trait.guard(player);
                    sender.sendMessage(ChatColor.GREEN + npc.getName() + " is guarding you.");
                }
                case "roam" -> {
                    trait.roam(player.getLocation());
                    sender.sendMessage(ChatColor.GREEN + npc.getName() + " is roaming near this spot.");
                }
                case "attack" -> {
                    if (args.length != 3) {
                        sender.sendMessage(ChatColor.RED + "Usage: /bots attack <bot> <player-or-bot>");
                        return true;
                    }
                    NPC targetNpc = findBotQuietly(args[2]);
                    if (targetNpc != null) {
                        trait.attackNpc(targetNpc);
                    } else {
                        Player targetPlayer = Bukkit.getPlayerExact(args[2]);
                        if (targetPlayer == null) {
                            sender.sendMessage(ChatColor.RED + "That player or bot is not here.");
                            return true;
                        }
                        trait.attackPlayer(targetPlayer);
                    }
                    sender.sendMessage(ChatColor.GREEN + npc.getName() + " is attacking " + args[2] + ".");
                }
                case "persona" -> {
                    if (args.length < 3) {
                        sender.sendMessage(ChatColor.RED + "Usage: /bots persona <bot> <description>");
                        return true;
                    }
                    trait.setPersonaPrompt(String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
                    sender.sendMessage(ChatColor.GREEN + "Updated " + npc.getName() + "'s personality.");
                }
                case "status" -> showBotStatus(sender, npc, trait);
                default -> showBotsHelp(sender);
            }
            return true;
        } catch (Exception exception) {
            getLogger().log(Level.SEVERE, "Bot command failed", exception);
            sender.sendMessage(ChatColor.RED + "That bot command failed. Check the server log.");
            return true;
        }
    }

    private NPC createBot(String name, Location location, Player owner) {
        NPC npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, name);
        npc.spawn(location);
        npc.getOrAddTrait(Equipment.class);
        AiCompanionTrait trait = npc.getOrAddTrait(AiCompanionTrait.class);
        if (owner == null) {
            trait.applyWildProfile(location);
        } else {
            trait.setOwnerName(owner.getName());
            trait.applyDefaultCombatProfile();
            trait.setHome(location);
        }
        trait.equipDefaultLoadout();
        return npc;
    }

    private void showBotsHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "COMPUTER PLAYER COMMANDS");
        sender.sendMessage(ChatColor.YELLOW + "/bots create <name>" + ChatColor.GRAY + " - friendly bot");
        sender.sendMessage(ChatColor.YELLOW + "/bots enemy <name>" + ChatColor.GRAY + " - roaming enemy");
        sender.sendMessage(ChatColor.YELLOW + "/bots populate" + ChatColor.GRAY + " - create the 10-character cast");
        sender.sendMessage(ChatColor.YELLOW + "/bots list");
        sender.sendMessage(ChatColor.YELLOW + "/bots follow <bot> | guard <bot> | roam <bot>");
        sender.sendMessage(ChatColor.YELLOW + "/bots attack <bot> <player-or-bot>");
        sender.sendMessage(ChatColor.YELLOW + "/bots scene battle <bot1> <bot2>");
        sender.sendMessage(ChatColor.YELLOW + "/bots freeze <bot|all> | reset <bot|all>");
        sender.sendMessage(ChatColor.YELLOW + "/bots persona <bot> <description>");
        sender.sendMessage(ChatColor.YELLOW + "/bots status <bot> | remove <bot>");
        sender.sendMessage(ChatColor.GRAY + "Use a bot's number or one-word name in commands.");
    }

    private void listBots(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "COMPUTER PLAYERS");
        int count = 0;
        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            AiCompanionTrait trait = npc.getTraitNullable(AiCompanionTrait.class);
            if (trait != null) {
                sender.sendMessage(ChatColor.YELLOW + "- " + npc.getName() + " [" + npc.getId() + "] "
                        + ChatColor.GRAY + trait.getModeName() + (npc.isSpawned() ? "" : " (respawning)"));
                count++;
            }
        }
        if (count == 0) {
            sender.sendMessage(ChatColor.GRAY + "No computer players yet. Try /bots create Steve.");
        }
    }

    private int populateCast(Location center) {
        int created = 0;
        for (int index = 0; index < CAST_PRESETS.size(); index++) {
            BotPreset preset = CAST_PRESETS.get(index);
            if (findBotQuietly(preset.name()) != null) {
                continue;
            }
            double angle = (Math.PI * 2 * index) / CAST_PRESETS.size();
            double radius = 8 + (index % 3) * 4;
            Location spawn = center.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
            World world = spawn.getWorld();
            spawn.setY(world.getHighestBlockYAt(spawn) + 1.0);

            NPC npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, preset.name());
            npc.spawn(spawn);
            npc.getOrAddTrait(Equipment.class);
            AiCompanionTrait trait = npc.getOrAddTrait(AiCompanionTrait.class);
            trait.applyRoamingProfile(spawn, preset.hostile(), preset.persona());
            trait.equipDefaultLoadout();
            created++;
        }
        return created;
    }

    private void showBotStatus(CommandSender sender, NPC npc, AiCompanionTrait trait) {
        sender.sendMessage(ChatColor.GOLD + npc.getName() + " [" + npc.getId() + "]");
        sender.sendMessage(ChatColor.YELLOW + "Mode: " + trait.getModeName());
        sender.sendMessage(ChatColor.YELLOW + "Owner: " + (trait.getOwnerName().isBlank() ? "none" : trait.getOwnerName()));
        sender.sendMessage(ChatColor.YELLOW + "Combat: " + (trait.isCombatEnabled() ? "on" : "off"));
        sender.sendMessage(ChatColor.YELLOW + "Location: " + formatLocation(npc.getStoredLocation()));
    }

    private String formatLocation(Location location) {
        return location.getWorld().getName() + " " + location.getBlockX() + ", "
                + location.getBlockY() + ", " + location.getBlockZ();
    }

    private List<NPC> selectBots(CommandSender sender, String selector) {
        if (selector.equalsIgnoreCase("all")) {
            List<NPC> bots = new ArrayList<>();
            for (NPC npc : CitizensAPI.getNPCRegistry()) {
                if (npc.hasTrait(AiCompanionTrait.class)) {
                    bots.add(npc);
                }
            }
            return bots;
        }
        NPC npc = findBot(sender, selector);
        return npc == null ? List.of() : List.of(npc);
    }

    private NPC findBot(CommandSender sender, String selector) {
        NPC npc = findBotQuietly(selector);
        if (npc == null) {
            sender.sendMessage(ChatColor.RED + "No computer player named or numbered " + selector + " exists.");
        }
        return npc;
    }

    private NPC findBotQuietly(String selector) {
        try {
            NPC byId = CitizensAPI.getNPCRegistry().getById(Integer.parseInt(selector));
            if (byId != null && byId.hasTrait(AiCompanionTrait.class)) {
                return byId;
            }
        } catch (NumberFormatException ignored) {
            // The selector is a name.
        }
        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            if (npc.hasTrait(AiCompanionTrait.class) && npc.getName().equalsIgnoreCase(selector)) {
                return npc;
            }
        }
        return null;
    }

    private record BotPreset(String name, boolean hostile, String persona) {
    }

    private NPC requireNpc(Player player, String idText) {
        int id;
        try {
            id = Integer.parseInt(idText);
        } catch (NumberFormatException exception) {
            player.sendMessage(ChatColor.RED + "NPC id must be a number.");
            return null;
        }

        NPC npc = CitizensAPI.getNPCRegistry().getById(id);
        if (npc == null) {
            player.sendMessage(ChatColor.RED + "No NPC with id " + id + " exists.");
            return null;
        }
        return npc;
    }

    private AiCompanionTrait requireTrait(Player player, NPC npc) {
        AiCompanionTrait trait = npc.getTraitNullable(AiCompanionTrait.class);
        if (trait == null) {
            player.sendMessage(ChatColor.RED + "That NPC does not have the AI companion trait. Use /ainpc bind " + npc.getId());
        }
        return trait;
    }
}
