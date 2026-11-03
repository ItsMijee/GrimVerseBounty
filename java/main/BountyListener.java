package main;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BountyListener implements Listener {

    private final GrimVerseBounty plugin;
    private final BountyManager manager;
    private final BountyGUI gui;

    public BountyListener(GrimVerseBounty plugin, BountyManager manager, BountyGUI gui) {
        this.plugin = plugin;
        this.manager = manager;
        this.gui = gui;
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player p = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();

        String mainTitle = color(plugin.getConfig().getString("gui.main-title", "&5&lɢʀɪᴍᴠᴇʀsᴇ ʙᴏᴜɴᴛʏ"));
        String prevTitle = color(plugin.getConfig().getString("gui.preview-title", "&5&lʜᴀᴅɪᴀʜ ʙᴏᴜɴᴛʏ"));

        if (title.equals(mainTitle)) {
            event.setCancelled(true);
            if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getView().getTopInventory())) return;

            int slot = event.getSlot();
            int page = gui.getPage(p);
            int maxPage = plugin.getConfig().getInt("options.max-page", 2);

            if (slot == 45 && page > 1) {
                gui.openMain(p, page - 1);
                manager.playSound(p, "open-gui");
                return;
            }
            if (slot == 53 && page < maxPage) {
                gui.openMain(p, page + 1);
                manager.playSound(p, "open-gui");
                return;
            }

            if (slot >= 0 && slot <= 44) {
                var map = gui.getSlotMap(p);
                if (map != null && map.containsKey(slot)) {
                    int id = map.get(slot);
                    if (!manager.getBounties().containsKey(id)) {
                        p.sendMessage(manager.getMsg("bounty-not-found"));
                        manager.playSound(p, "error");
                        gui.openMain(p, page);
                        return;
                    }
                    gui.openPreview(p, id);
                    manager.playSound(p, "open-gui");
                }
            }
        } else if (title.equals(prevTitle)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player p = (Player) event.getPlayer();
        String title = event.getView().getTitle();
        String depTitle = color(plugin.getConfig().getString("gui.deposit-title", "&6&lᴍᴀsᴜᴋᴋᴀɴ ʜᴀᴅɪᴀʜ ʙᴏᴜɴᴛʏ"));

        if (title.equals(depTitle)) {
            if (!manager.hasSetup(p.getUniqueId())) return;
            UUID targetUuid = manager.removeSetup(p.getUniqueId());
            Inventory inv = event.getInventory();

            List<ItemStack> items = new ArrayList<>();
            for (int i = 0; i < 27; i++) {
                ItemStack item = inv.getItem(i);
                if (item != null && item.getType() != Material.AIR) {
                    items.add(item.clone());
                    inv.setItem(i, null);
                }
            }

            if (items.isEmpty()) {
                p.sendMessage(manager.getMsg("deposit-cancel"));
                return;
            }

            int limit = manager.getPlayerBountyLimit(p);
            int count = manager.getBountyCount(p);
            int maxGlobal = plugin.getConfig().getInt("options.max-global", 90);
            String failReason = "";

            if (targetUuid == null) failReason = "invalid";
            else if (count >= limit) failReason = "limit";
            else if (manager.getBounties().size() >= maxGlobal) {
                if (!manager.evictLowestPriority(limit)) failReason = "busy";
            }

            if (!failReason.isEmpty()) {
                manager.addRefund(p.getUniqueId(), items);
                manager.deliverRefund(p);

                if (failReason.equals("limit")) p.sendMessage(manager.getMsg("limit-reached").replace("%current%", count+"").replace("%limit%", limit+""));
                else if (failReason.equals("busy")) p.sendMessage(manager.getMsg("global-full"));
                else p.sendMessage(manager.getMsg("deposit-cancel"));
                
                manager.playSound(p, "error");
                manager.saveData();
                return;
            }

            manager.addBounty(p.getUniqueId(), targetUuid, limit, items);

            OfflinePlayer targetOp = Bukkit.getOfflinePlayer(targetUuid);
            String targetName = targetOp.getName() != null ? targetOp.getName() : "Unknown";

            String prefix = color(plugin.getConfig().getString("options.prefix", ""));
            String announceMsg = manager.getRawMsg("bounty-announced").replace("%player%", p.getName()).replace("%target%", targetName);
            Bukkit.broadcastMessage(prefix + announceMsg);
            
            for (Player online : Bukkit.getOnlinePlayers()) manager.playSound(online, "bounty-new");

            Player targetPlayer = Bukkit.getPlayer(targetUuid);
            if (targetPlayer != null && targetPlayer.isOnline()) {
                targetPlayer.sendMessage(manager.getMsg("target-warning"));
                manager.playSound(targetPlayer, "target-warn");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        if (killer == null || killer.equals(victim)) return;

        int claimed = 0;
        int blocked = 0;
        List<Integer> toDelete = new ArrayList<>();

        for (BountyData data : manager.getBounties().values()) {
            if (data.target.equals(victim.getUniqueId())) {
                if (data.owner.equals(killer.getUniqueId())) {
                    blocked++;
                } else {
                    manager.addRefund(killer.getUniqueId(), data.items);
                    toDelete.add(data.id);
                    claimed++;

                    Player ownerPlayer = Bukkit.getPlayer(data.owner);
                    if (ownerPlayer != null && ownerPlayer.isOnline()) {
                        String msg = manager.getRawMsg("bounty-claimed-owner").replace("%victim%", victim.getName()).replace("%killer%", killer.getName());
                        ownerPlayer.sendMessage(color(plugin.getConfig().getString("options.prefix", "") + msg));
                    }
                }
            }
        }

        for (int id : toDelete) manager.getBounties().remove(id);

        if (blocked > 0) killer.sendMessage(manager.getMsg("cannot-claim-self"));

        if (claimed > 0) {
            manager.deliverRefund(killer);
            String prefix = color(plugin.getConfig().getString("options.prefix", ""));
            String msg = manager.getRawMsg("bounty-claimed").replace("%killer%", killer.getName()).replace("%victim%", victim.getName());
            Bukkit.broadcastMessage(prefix + msg);
            
            for (Player online : Bukkit.getOnlinePlayers()) manager.playSound(online, "claim");
            manager.saveData();
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (manager.hasRefund(p)) manager.deliverRefund(p);
        }, 40L);
    }
}
