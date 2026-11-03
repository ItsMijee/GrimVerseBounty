package main;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class BountyManager {

    private final GrimVerseBounty plugin;
    private final Map<Integer, BountyData> bountyMap = new LinkedHashMap<>();
    private final Map<UUID, List<ItemStack>> bountyRefund = new HashMap<>();
    private final Map<UUID, UUID> bountySetup = new HashMap<>();
    
    private int bountyCounter = 0;
    private File dataFile;
    private FileConfiguration dataConfig;

    public BountyManager(GrimVerseBounty plugin) {
        this.plugin = plugin;
        loadData();
    }

    public void loadData() {
        dataFile = new File(plugin.getDataFolder(), "bounties.yml");
        if (!dataFile.exists()) {
            try { dataFile.createNewFile(); } catch (IOException ignored) {}
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        bountyCounter = dataConfig.getInt("bounty_counter", 0);

        if (dataConfig.contains("bounty_list")) {
            for (String key : dataConfig.getConfigurationSection("bounty_list").getKeys(false)) {
                int id = Integer.parseInt(key);
                UUID target = UUID.fromString(dataConfig.getString("bounty_list." + key + ".target"));
                UUID owner = UUID.fromString(dataConfig.getString("bounty_list." + key + ".owner"));
                int ownerMaxLimit = dataConfig.getInt("bounty_list." + key + ".ownerMaxLimit", 1);
                List<ItemStack> items = (List<ItemStack>) dataConfig.getList("bounty_list." + key + ".items");
                if (items == null) items = new ArrayList<>();
                
                List<ItemStack> safeItems = new ArrayList<>();
                for (ItemStack item : items) {
                    if (item != null && item.getType() != Material.AIR) safeItems.add(item.clone());
                }
                bountyMap.put(id, new BountyData(id, target, owner, ownerMaxLimit, safeItems));
            }
        }

        if (dataConfig.contains("refunds")) {
            for (String uuidStr : dataConfig.getConfigurationSection("refunds").getKeys(false)) {
                UUID uuid = UUID.fromString(uuidStr);
                List<ItemStack> items = (List<ItemStack>) dataConfig.getList("refunds." + uuidStr);
                if (items != null && !items.isEmpty()) {
                    List<ItemStack> safeRefund = new ArrayList<>();
                    for (ItemStack item : items) {
                        if (item != null && item.getType() != Material.AIR) safeRefund.add(item.clone());
                    }
                    bountyRefund.put(uuid, safeRefund);
                }
            }
        }
    }

    public void saveData() {
        dataConfig.set("bounty_counter", bountyCounter);
        dataConfig.set("bounty_list", null);
        for (Map.Entry<Integer, BountyData> entry : bountyMap.entrySet()) {
            String path = "bounty_list." + entry.getKey();
            dataConfig.set(path + ".target", entry.getValue().target.toString());
            dataConfig.set(path + ".owner", entry.getValue().owner.toString());
            dataConfig.set(path + ".ownerMaxLimit", entry.getValue().ownerMaxLimit);
            dataConfig.set(path + ".items", entry.getValue().items);
        }
        dataConfig.set("refunds", null);
        for (Map.Entry<UUID, List<ItemStack>> entry : bountyRefund.entrySet()) {
            dataConfig.set("refunds." + entry.getKey().toString(), entry.getValue());
        }
        try { dataConfig.save(dataFile); } catch (IOException ignored) {}
    }

    public String getMsg(String key) {
        String prefix = plugin.getConfig().getString("options.prefix", "&8[&c&lSʏsᴛᴇᴍ&8] &7");
        String msg = plugin.getConfig().getString("messages." + key, "");
        return ChatColor.translateAlternateColorCodes('&', prefix + msg);
    }
    
    public String getRawMsg(String key) {
        return ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages." + key, ""));
    }

    public void playSound(Player p, String key) {
        try {
            String soundName = plugin.getConfig().getString("sounds." + key, "ENTITY_VILLAGER_NO");
            p.playSound(p.getLocation(), Sound.valueOf(soundName.toUpperCase()), 1f, 1f);
        } catch (Exception ignored) {}
    }

    public int getPlayerBountyLimit(Player p) {
        int maxCheck = plugin.getConfig().getInt("options.max-permission-check", 15);
        for (int i = maxCheck; i >= 1; i--) {
            if (p.hasPermission("bounty.limit." + i)) return i;
        }
        return 1;
    }

    public int getBountyCount(Player p) {
        int count = 0;
        for (BountyData data : bountyMap.values()) {
            if (data.owner.equals(p.getUniqueId())) count++;
        }
        return count;
    }

    public void addRefund(UUID uuid, List<ItemStack> items) {
        List<ItemStack> list = bountyRefund.computeIfAbsent(uuid, k -> new ArrayList<>());
        for(ItemStack i : items) if(i != null && i.getType() != Material.AIR) list.add(i.clone());
    }

    public boolean hasRefund(Player p) {
        return bountyRefund.containsKey(p.getUniqueId());
    }

    public void deliverRefund(Player p) {
        List<ItemStack> list = bountyRefund.get(p.getUniqueId());
        if (list == null || list.isEmpty()) return;

        int given = 0;
        int left = 0;
        Iterator<ItemStack> it = list.iterator();

        while (it.hasNext()) {
            ItemStack item = it.next();
            if (p.getInventory().firstEmpty() != -1) {
                p.getInventory().addItem(item.clone());
                it.remove();
                given++;
            } else {
                left++;
            }
        }
        if (list.isEmpty()) bountyRefund.remove(p.getUniqueId());

        if (given > 0) {
            p.sendMessage(getMsg("claim-success"));
            playSound(p, "item-pickup");
        }
        if (left > 0) {
            p.sendMessage(getMsg("inventory-full"));
            playSound(p, "error");
        }
        saveData();
    }

    public boolean evictLowestPriority(int newPlayerLimit) {
        int lowestLimit = 999;
        int lowestId = -1;

        for (BountyData data : bountyMap.values()) {
            if (data.ownerMaxLimit < lowestLimit) {
                lowestLimit = data.ownerMaxLimit;
                lowestId = data.id;
            } else if (data.ownerMaxLimit == lowestLimit) {
                if (lowestId == -1 || data.id < lowestId) lowestId = data.id;
            }
        }
        if (lowestId == -1 || newPlayerLimit <= lowestLimit) return false;

        BountyData evicted = bountyMap.remove(lowestId);
        if (evicted == null) return false;

        addRefund(evicted.owner, evicted.items);

        Player ownerPlayer = Bukkit.getPlayer(evicted.owner);
        OfflinePlayer targetOp = Bukkit.getOfflinePlayer(evicted.target);
        String targetName = targetOp.getName() != null ? targetOp.getName() : "Unknown";

        if (ownerPlayer != null && ownerPlayer.isOnline()) {
            String msg = getRawMsg("evicted").replace("%target%", targetName);
            ownerPlayer.sendMessage(plugin.getConfig().getString("options.prefix").replace("&", "§") + msg);
            deliverRefund(ownerPlayer);
        }
        saveData();
        return true;
    }

    public void addBounty(UUID owner, UUID target, int limit, List<ItemStack> items) {
        bountyCounter++;
        bountyMap.put(bountyCounter, new BountyData(bountyCounter, target, owner, limit, items));
        saveData();
    }

    public void setSetup(UUID p, UUID target) { bountySetup.put(p, target); }
    public UUID removeSetup(UUID p) { return bountySetup.remove(p); }
    public boolean hasSetup(UUID p) { return bountySetup.containsKey(p); }
    
    public Map<Integer, BountyData> getBounties() { return bountyMap; }
}
