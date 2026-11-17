package me.iamnotgalaxy.dailyrewards;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class DailyRewards extends JavaPlugin implements Listener, CommandExecutor {

    private static DailyRewards instance;
    private final Map<UUID, PlayerRewardData> playerData = new HashMap<>();
    private File dataFile;
    private File messagesFile;
    private FileConfiguration messages;
    private boolean debugMode = false;

    private static class PlayerRewardData {
        int currentDay;
        LocalDateTime lastClaim;
        int totalClaims;
        int streak;

        PlayerRewardData(int currentDay, LocalDateTime lastClaim) {
            this.currentDay = currentDay;
            this.lastClaim = lastClaim;
            this.totalClaims = 0;
            this.streak = 0;
        }
    }

    private enum RewardState {
        CLAIMED,
        READY,
        COOLDOWN,
        NEXT,
        LOCKED
    }

    @Override
    public void onEnable() {
        instance = this;
        dataFile = new File(getDataFolder(), "data.yml");
        messagesFile = new File(getDataFolder(), "messages.yml");

        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        saveDefaultConfig();
        createMessagesFile();
        loadMessages();

        getServer().getPluginManager().registerEvents(this, this);
        getCommand("rewards").setExecutor(this);
        getCommand("dailyreload").setExecutor(this);
        getCommand("dailyskip").setExecutor(this);
        getCommand("dailyreset").setExecutor(this);
        getCommand("dailydebug").setExecutor(this);
        getCommand("dailyleaderboard").setExecutor(this);
        getCommand("dailystats").setExecutor(this);
        getCommand("dailyhelp").setExecutor(this);

        loadPlayerData();
        getLogger().info("✅ DailyRewards successfully loaded!");
    }

    @Override
    public void onDisable() {
        savePlayerData();
        getLogger().info("🛑 DailyRewards disabled.");
    }

    public static DailyRewards getInstance() {
        return instance;
    }

    private void createMessagesFile() {
        if (!messagesFile.exists()) {
            saveResource("messages.yml", false);
            getLogger().info("📝 messages.yml created successfully!");
        }
    }

    private void loadMessages() {
        messages = YamlConfiguration.loadConfiguration(messagesFile);
    }

    private void savePlayerData() {
        YamlConfiguration data = new YamlConfiguration();

        for (Map.Entry<UUID, PlayerRewardData> entry : playerData.entrySet()) {
            String basePath = "players." + entry.getKey() + ".";
            data.set(basePath + "day", entry.getValue().currentDay);
            data.set(basePath + "lastClaim", entry.getValue().lastClaim.toString());
            data.set(basePath + "totalClaims", entry.getValue().totalClaims);
            data.set(basePath + "streak", entry.getValue().streak);
        }

        try {
            data.save(dataFile);
            if (debugMode) {
                getLogger().info("✅ Data successfully saved for " + playerData.size() + " players");
            }
        } catch (IOException e) {
            getLogger().severe("❌ Error saving data: " + e.getMessage());
        }
    }

    private void loadPlayerData() {
        if (!dataFile.exists()) {
            getLogger().info("📁 Data file does not exist, starting fresh");
            return;
        }

        YamlConfiguration data = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection players = data.getConfigurationSection("players");

        if (players == null) {
            getLogger().info("📁 No player data to load");
            return;
        }

        int loadedCount = 0;
        for (String uuidStr : players.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                int day = players.getInt(uuidStr + ".day", 1);
                String lastClaimStr = players.getString(uuidStr + ".lastClaim");
                int totalClaims = players.getInt(uuidStr + ".totalClaims", 0);
                int streak = players.getInt(uuidStr + ".streak", 0);

                LocalDateTime lastClaim = lastClaimStr != null ?
                        LocalDateTime.parse(lastClaimStr) :
                        LocalDateTime.now().minusHours(25);

                PlayerRewardData playerDataObj = new PlayerRewardData(day, lastClaim);
                playerDataObj.totalClaims = totalClaims;
                playerDataObj.streak = streak;
                playerData.put(uuid, playerDataObj);
                loadedCount++;

            } catch (Exception e) {
                getLogger().warning("⚠️ Error loading data for " + uuidStr + ": " + e.getMessage());
            }
        }
        if (debugMode) {
            getLogger().info("✅ Loaded data for " + loadedCount + " players");
        }
    }

    private RewardState getDayState(int day, int currentDay, boolean canClaim) {
        if (day < currentDay) {
            return RewardState.CLAIMED;
        } else if (day == currentDay) {
            return canClaim ? RewardState.READY : RewardState.COOLDOWN;
        } else if (day == currentDay + 1) {
            return RewardState.NEXT;
        } else {
            return RewardState.LOCKED;
        }
    }

    private String getRemainingTime(LocalDateTime lastClaimTime) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextAvailable = lastClaimTime.plusHours(24);

        if (now.isAfter(nextAvailable)) {
            return "0h 0m";
        }

        long hours = ChronoUnit.HOURS.between(now, nextAvailable);
        long minutes = ChronoUnit.MINUTES.between(now, nextAvailable) % 60;

        return hours + "h " + minutes + "m";
    }

    private boolean canPlayerClaim(Player p) {
        UUID uuid = p.getUniqueId();
        PlayerRewardData data = playerData.get(uuid);

        if (data == null || data.lastClaim == null) {
            return true;
        }

        return data.lastClaim.plusHours(24).isBefore(LocalDateTime.now());
    }

    private PlayerRewardData getOrCreatePlayerData(UUID uuid) {
        return playerData.computeIfAbsent(uuid,
                k -> new PlayerRewardData(1, LocalDateTime.now().minusHours(25)));
    }

    // METHOD TO GET REWARDS FOR SPECIFIC DAY
    private List<String> getRewardsForDay(ConfigurationSection daySec) {
        List<String> rewards = new ArrayList<>();

        if (daySec == null) return rewards;

        // GET REWARDS DIRECTLY FROM CONFIGURATION
        List<String> configRewards = daySec.getStringList("rewards");
        if (!configRewards.isEmpty()) {
            for (String reward : configRewards) {
                // Replace placeholders and add formatting
                String formattedReward = reward.replace("&", "§");
                rewards.add(formattedReward);
            }
        } else {
            // Fallback to old system for backward compatibility
            List<String> commands = daySec.getStringList("commands");
            for (String cmd : commands) {
                if (cmd.contains("give %player%")) {
                    String[] parts = cmd.split(" ");
                    if (parts.length >= 4) {
                        String item = parts[2];
                        String amount = parts[3];
                        if (!amount.equals("%player%")) {
                            rewards.add(amount + "x " + translateItemName(item));
                        }
                    }
                } else if (cmd.contains("eco give")) {
                    String[] parts = cmd.split(" ");
                    for (int i = 0; i < parts.length; i++) {
                        if (parts[i].equals("give") && i + 2 < parts.length) {
                            String amount = parts[i + 1];
                            String player = parts[i + 2];
                            if (!amount.equals("%player%") && !player.equals("%player%")) {
                                rewards.add(amount + "$");
                            } else if (!amount.equals("%player%")) {
                                rewards.add(amount + "$");
                            }
                            break;
                        }
                    }
                } else if (cmd.contains("cipgive") || cmd.contains("ci give") || cmd.contains("ci dailyreward")) {
                    rewards.add("Weekly Key");
                }
            }
        }

        return rewards;
    }

    // ITEM NAME TRANSLATION
    private String translateItemName(String itemName) {
        Map<String, String> itemTranslations = new HashMap<>();
        itemTranslations.put("cooked_beef", "Steak");
        itemTranslations.put("beef", "Steak");
        itemTranslations.put("iron_ingot", "Iron");
        itemTranslations.put("iron", "Iron");
        itemTranslations.put("diamond", "Diamond");
        itemTranslations.put("gold_ingot", "Gold");
        itemTranslations.put("gold", "Gold");
        itemTranslations.put("netherite_scrap", "Netherite");
        itemTranslations.put("netherite", "Netherite");
        itemTranslations.put("tripwire_hook", "Key");
        itemTranslations.put("emerald", "Emerald");
        itemTranslations.put("diamond_block", "Diamond Block");

        return itemTranslations.getOrDefault(itemName.toLowerCase(), itemName);
    }

    // GET MESSAGE FROM messages.yml
    private String getMessage(String path) {
        return messages.getString(path, "&cMessage not found: " + path).replace("&", "§");
    }

    private List<String> getMessageList(String path) {
        List<String> list = messages.getStringList(path);
        List<String> formatted = new ArrayList<>();
        for (String line : list) {
            formatted.add(line.replace("&", "§"));
        }
        return formatted;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        switch (cmd.getName().toLowerCase()) {
            case "dailyreload":
                return handleReload(sender);
            case "dailyskip":
                return handleSkip(sender, args);
            case "dailyreset":
                return handleReset(sender, args);
            case "dailydebug":
                return handleDebug(sender, args);
            case "dailyleaderboard":
                return handleLeaderboard(sender);
            case "dailystats":
                return handleStats(sender, args);
            case "dailyhelp":
                return handleHelp(sender);
            default:
                if (!(sender instanceof Player)) {
                    sender.sendMessage(getMessage("player-only"));
                    return true;
                }
                openGUI((Player) sender);
                return true;
        }
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("dailyrewards.reload")) {
            sender.sendMessage(getMessage("no-permission"));
            return true;
        }
        reloadConfig();
        loadMessages();
        sender.sendMessage(getMessage("reload-success"));
        return true;
    }

    private boolean handleSkip(CommandSender sender, String[] args) {
        if (!sender.hasPermission("dailyrewards.skip")) {
            sender.sendMessage(getMessage("no-permission"));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§cUsage: /dailyskip <player> [days]");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(getMessage("player-offline").replace("{player}", args[0]));
            return true;
        }

        UUID uuid = target.getUniqueId();
        PlayerRewardData data = getOrCreatePlayerData(uuid);

        if (args.length >= 2) {
            try {
                int daysToSkip = Integer.parseInt(args[1]);
                data.currentDay = Math.min(data.currentDay + daysToSkip, 7);
                data.lastClaim = LocalDateTime.now().minusHours(25);

                sender.sendMessage("§aPlayer " + target.getName() + " was advanced to day " + data.currentDay);
                target.sendMessage("§aYour daily rewards progress has been updated!");

                if (debugMode && sender.hasPermission("dailyrewards.debug")) {
                    sender.sendMessage("§7[DEBUG] Player " + target.getName() + " skipped to day " + data.currentDay);
                }

            } catch (NumberFormatException e) {
                sender.sendMessage("§cDays must be a number!");
                return true;
            }
        } else {
            data.lastClaim = LocalDateTime.now().minusHours(25);
            sender.sendMessage("§aPlayer " + target.getName() + " can claim rewards again!");
            target.sendMessage("§aYou can claim today's reward again!");

            if (debugMode && sender.hasPermission("dailyrewards.debug")) {
                sender.sendMessage("§7[DEBUG] Player " + target.getName() + " reset for today's reward");
            }
        }

        savePlayerData();
        return true;
    }

    private boolean handleReset(CommandSender sender, String[] args) {
        if (!sender.hasPermission("dailyrewards.reset")) {
            sender.sendMessage(getMessage("no-permission"));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§cUsage: /dailyreset <player>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(getMessage("player-offline").replace("{player}", args[0]));
            return true;
        }

        UUID uuid = target.getUniqueId();
        playerData.put(uuid, new PlayerRewardData(1, LocalDateTime.now().minusHours(25)));

        sender.sendMessage("§aPlayer " + target.getName() + " was reset to day 1!");
        target.sendMessage("§aYour daily rewards progress has been reset!");

        if (debugMode && sender.hasPermission("dailyrewards.debug")) {
            sender.sendMessage("§7[DEBUG] Player " + target.getName() + " completely reset to day 1");
        }

        savePlayerData();
        return true;
    }

    private boolean handleDebug(CommandSender sender, String[] args) {
        if (!sender.hasPermission("dailyrewards.debug")) {
            sender.sendMessage(getMessage("no-permission"));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§cUsage: /dailydebug <on|off|info> [player]");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "on":
                debugMode = true;
                sender.sendMessage("§aDebug mode enabled! Messages will be sent to players with permission.");
                getLogger().info("Debug mode enabled - messages will be sent to players with permission");
                break;

            case "off":
                debugMode = false;
                sender.sendMessage("§cDebug mode disabled! Messages will be sent to console only.");
                getLogger().info("Debug mode disabled - messages will be sent to console only");
                break;

            case "info":
                Player target = args.length > 1 ? Bukkit.getPlayer(args[1]) : (sender instanceof Player ? (Player) sender : null);
                if (target == null) {
                    sender.sendMessage(getMessage("player-offline"));
                    return true;
                }

                UUID uuid = target.getUniqueId();
                PlayerRewardData data = getOrCreatePlayerData(uuid);
                boolean canClaim = canPlayerClaim(target);

                sender.sendMessage("§6=== DEBUG INFO ===");
                sender.sendMessage("§ePlayer: §f" + target.getName());
                sender.sendMessage("§eCurrent Day: §f" + data.currentDay);
                sender.sendMessage("§eLast Claim: §f" + data.lastClaim);
                sender.sendMessage("§eCan Claim: §f" + canClaim);
                sender.sendMessage("§eRemaining Time: §f" + getRemainingTime(data.lastClaim));
                sender.sendMessage("§eTotal Claims: §f" + data.totalClaims);
                sender.sendMessage("§eCurrent Streak: §f" + data.streak);

                for (int day = 1; day <= 7; day++) {
                    RewardState state = getDayState(day, data.currentDay, canClaim);
                    sender.sendMessage("§eDay " + day + ": §f" + state);
                }
                break;

            default:
                sender.sendMessage("§cUsage: /dailydebug <on|off|info> [player]");
                break;
        }

        return true;
    }

    private boolean handleLeaderboard(CommandSender sender) {
        if (!sender.hasPermission("dailyrewards.leaderboard")) {
            sender.sendMessage(getMessage("no-permission"));
            return true;
        }

        // Sort players by total claims
        List<Map.Entry<UUID, PlayerRewardData>> sortedPlayers = new ArrayList<>(playerData.entrySet());
        sortedPlayers.sort((a, b) -> Integer.compare(b.getValue().totalClaims, a.getValue().totalClaims));

        sender.sendMessage("§6=== DAILY REWARDS LEADERBOARD ===");
        sender.sendMessage("§eTop 10 players by total claims:");

        int count = 0;
        for (Map.Entry<UUID, PlayerRewardData> entry : sortedPlayers) {
            if (count >= 10) break;

            String playerName = Bukkit.getOfflinePlayer(entry.getKey()).getName();
            if (playerName == null) continue;

            PlayerRewardData data = entry.getValue();
            sender.sendMessage("§6" + (count + 1) + ". §f" + playerName +
                    " §7- §b" + data.totalClaims + " claims §7- §eDay " + data.currentDay);
            count++;
        }

        if (count == 0) {
            sender.sendMessage("§cNo player data available.");
        }

        return true;
    }

    private boolean handleStats(CommandSender sender, String[] args) {
        if (!sender.hasPermission("dailyrewards.stats")) {
            sender.sendMessage(getMessage("no-permission"));
            return true;
        }

        Player target = args.length > 0 ? Bukkit.getPlayer(args[0]) : (sender instanceof Player ? (Player) sender : null);
        if (target == null) {
            sender.sendMessage(getMessage("player-offline"));
            return true;
        }

        UUID uuid = target.getUniqueId();
        PlayerRewardData data = getOrCreatePlayerData(uuid);
        boolean canClaim = canPlayerClaim(target);

        sender.sendMessage("§6=== DAILY REWARDS STATS ===");
        sender.sendMessage("§ePlayer: §f" + target.getName());
        sender.sendMessage("§eCurrent Day: §f" + data.currentDay + "/7");
        sender.sendMessage("§eCan Claim Today: §f" + (canClaim ? "Yes" : "No"));
        sender.sendMessage("§eRemaining Time: §f" + getRemainingTime(data.lastClaim));
        sender.sendMessage("§eTotal Claims: §f" + data.totalClaims);
        sender.sendMessage("§eCurrent Streak: §f" + data.streak + " days");
        sender.sendMessage("§eLast Claim: §f" + data.lastClaim);

        return true;
    }

    private boolean handleHelp(CommandSender sender) {
        if (!sender.hasPermission("dailyrewards.help")) {
            sender.sendMessage(getMessage("no-permission"));
            return true;
        }

        sender.sendMessage("§6=== DAILY REWARDS HELP ===");
        sender.sendMessage("§e/rewards §7- Open daily rewards GUI");
        sender.sendMessage("§e/dailyreload §7- Reload configuration");
        sender.sendMessage("§e/dailyskip <player> [days] §7- Skip days for player");
        sender.sendMessage("§e/dailyreset <player> §7- Reset player progress");
        sender.sendMessage("§e/dailydebug <on|off|info> [player] §7- Debug commands");
        sender.sendMessage("§e/dailyleaderboard §7- View leaderboard");
        sender.sendMessage("§e/dailystats [player] §7- View player statistics");
        sender.sendMessage("§e/dailyhelp §7- Show this help");

        return true;
    }

    public void openGUI(Player p) {
        FileConfiguration cfg = getConfig();
        ConfigurationSection rewards = cfg.getConfigurationSection("rewards");
        if (rewards == null) {
            p.sendMessage(getMessage("config-error"));
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 27, getMessage("gui.title"));

        UUID uuid = p.getUniqueId();
        PlayerRewardData data = getOrCreatePlayerData(uuid);
        boolean canClaim = canPlayerClaim(p);

        // DEBUG MESSAGE - only if debug mode is enabled and player has permission
        if (debugMode && p.hasPermission("dailyrewards.debug")) {
            p.sendMessage("§7[DEBUG] Day: " + data.currentDay + ", Can Claim: " + canClaim);
        }

        int slot = 10;
        for (int day = 1; day <= 7; day++) {
            ConfigurationSection daySec = rewards.getConfigurationSection("day" + day);
            if (daySec == null) continue;

            RewardState state = getDayState(day, data.currentDay, canClaim);
            ItemStack item = createDayItem(daySec, state, p, data);

            inv.setItem(slot, item);
            slot++;
        }

        p.openInventory(inv);
    }

    private ItemStack createDayItem(ConfigurationSection daySec, RewardState state, Player p, PlayerRewardData data) {
        Material mat;
        List<String> lore = new ArrayList<>();
        FileConfiguration cfg = getConfig();

        // GET REWARDS LIST FOR THE DAY
        List<String> rewardsList = getRewardsForDay(daySec);
        String rewardsText = rewardsList.isEmpty() ? "???" : String.join(", ", rewardsList);

        switch (state) {
            case CLAIMED:
                mat = Material.LIME_DYE;
                for (String l : getMessageList("claimed-lore")) {
                    String formatted = l.replace("???", rewardsText);
                    lore.add(formatted);
                }
                break;

            case READY:
                String itemStr = daySec.getString("item", "BARRIER");
                mat = Material.matchMaterial(itemStr);
                if (mat == null) {
                    getLogger().warning("Invalid material: " + itemStr);
                    mat = Material.BARRIER;
                }
                for (String l : getMessageList("today-lore")) {
                    String formatted = l.replace("???", rewardsText);
                    lore.add(formatted);
                }
                break;

            case COOLDOWN:
                mat = Material.CLOCK;
                for (String l : getMessageList("cooldown-lore")) {
                    String formatted = l.replace("???", rewardsText)
                            .replace("%time%", getRemainingTime(data.lastClaim));
                    lore.add(formatted);
                }
                break;

            case NEXT:
                mat = Material.GRAY_DYE;
                for (String l : getMessageList("locked-lore")) {
                    String formatted = l.replace("???", rewardsText);
                    lore.add(formatted);
                }
                break;

            case LOCKED:
            default:
                mat = Material.GRAY_DYE;
                for (String l : getMessageList("locked-lore")) {
                    String formatted = l.replace("???", rewardsText);
                    lore.add(formatted);
                }
                break;
        }

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();

        String displayName = daySec.getString("name", "§fDay " + daySec.getName().replace("day", ""));
        meta.setDisplayName(displayName.replace("&", "§"));
        meta.setLore(lore);

        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!e.getView().getTitle().equals(getMessage("gui.title"))) return;
        e.setCancelled(true);

        if (e.getCurrentItem() == null) return;
        Player p = (Player) e.getWhoClicked();
        int slot = e.getSlot();
        UUID uuid = p.getUniqueId();

        PlayerRewardData data = getOrCreatePlayerData(uuid);
        boolean canClaim = canPlayerClaim(p);

        int baseSlot = 10;
        int clickedDay = slot - baseSlot + 1;

        FileConfiguration cfg = getConfig();
        ConfigurationSection rewards = cfg.getConfigurationSection("rewards");
        if (rewards == null) return;

        if (clickedDay != data.currentDay) {
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            p.sendMessage("§cYou can only click on day " + data.currentDay + "!");
            return;
        }

        if (!canClaim) {
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            p.sendMessage("§cYou can claim again in " + getRemainingTime(data.lastClaim) + "!");
            return;
        }

        ConfigurationSection daySec = rewards.getConfigurationSection("day" + data.currentDay);
        if (daySec == null) {
            p.sendMessage(getMessage("reward-not-exist").replace("{day}", String.valueOf(data.currentDay)));
            return;
        }

        List<String> cmds = daySec.getStringList("commands");
        for (String cmd : cmds) {
            String formattedCmd = cmd.replace("%player%", p.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), formattedCmd);

            // DEBUG MESSAGE - only if debug mode is enabled
            if (debugMode) {
                getLogger().info("Executed command for " + p.getName() + ": " + formattedCmd);
            }
        }

        String message = daySec.getString("message", "§aYou received your daily reward!");
        p.sendMessage(message.replace("&", "§"));

        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);

        // Update statistics
        data.lastClaim = LocalDateTime.now();
        data.totalClaims++;
        data.streak++;

        if (data.currentDay < 7) {
            data.currentDay++;
        } else {
            data.currentDay = 1;
            p.sendMessage("§6🎉 Congratulations! You completed the weekly rewards! Starting over from day 1.");
        }

        savePlayerData();
        openGUI(p);
    }
}