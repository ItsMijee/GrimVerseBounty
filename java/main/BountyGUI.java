package main;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class BountyGUI {

    private final GrimVerseBounty plugin;
    private final BountyManager manager;
    private final Map<UUID, Integer> bountyPage = new HashMap<>();
    private final Map<UUID, Map<Integer, Integer>> bountyMainMap = new HashMap<>();

    public BountyGUI(GrimVerseBounty plugin, BountyManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    public void openMain(Player p, int page) {
        int maxPage = plugin.getConfig().getInt("options.max-page", 2);
        if (page > maxPage) page = 1;

        String title = color(plugin.getConfig().getString("gui.main-title", "&5&lɢʀɪᴍᴠᴇʀsᴇ ʙᴏᴜɴᴛʏ"));
        Inventory inv = Bukkit.createInventory(null, 54, title);
        bountyPage.put(p.getUniqueId(), page);

        Map<Integer, Integer> slotMap = new HashMap<>();

        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta paneMeta = pane.getItemMeta();
        if (paneMeta != null) { paneMeta.setDisplayName(" "); pane.setItemMeta(paneMeta); }

        for (int i = 45; i <= 53; i++) inv.setItem(i, pane);

        int total = manager.getBounties().size();
        int maxGlobal = plugin.getConfig().getInt("options.max-global", 90);

        if (page > 1) {
            ItemStack prev = new ItemStack(Material.ARROW);
            ItemMeta m = prev.getItemMeta();
            if (m != null) { m.setDisplayName(color("&e&l⬅ ʜᴀʟᴀᴍᴀɴ sᴇʙᴇʟᴜᴍɴʏᴀ")); prev.setItemMeta(m); }
            inv.setItem(45, prev);
        }

        ItemStack paper = new ItemStack(Material.PAPER);
        ItemMeta paperMeta = paper.getItemMeta();
        if (paperMeta != null) {
            paperMeta.setDisplayName(color("&d&lʜᴀʟᴀᴍᴀɴ &f&l" + page + "&7/" + maxPage));
            paperMeta.setLore(Arrays.asList(
                    color("&7ʙᴏᴜɴᴛʏ ᴀᴋᴛɪғ: &f" + total + "&7/" + maxGlobal),
                    color("&7ᴘᴀsᴀɴɢ ʙᴏᴜɴᴛʏ: &e/bounty <ᴘᴇᴍᴀɪɴ>")
            ));
            paper.setItemMeta(paperMeta);
        }
        inv.setItem(49, paper);

        if (page < maxPage) {
            ItemStack next = new ItemStack(Material.ARROW);
            ItemMeta m = next.getItemMeta();
            if (m != null) { m.setDisplayName(color("&e&lʜᴀʟᴀᴍᴀɴ ʙᴇʀɪᴋᴜᴛɴʏᴀ ➡")); next.setItemMeta(m); }
            inv.setItem(53, next);
        }

        int startIndex = (page - 1) * 45;
        int endIndex = startIndex + 44;
        int slotIdx = 0;
        int currentIndex = 0;

        for (BountyData data : manager.getBounties().values()) {
            if (currentIndex >= startIndex && currentIndex <= endIndex) {
                OfflinePlayer targetOp = Bukkit.getOfflinePlayer(data.target);
                OfflinePlayer ownerOp = Bukkit.getOfflinePlayer(data.owner);

                String tName = targetOp.getName() != null ? targetOp.getName() : "Unknown";
                String oName = ownerOp.getName() != null ? ownerOp.getName() : "Unknown";

                ItemStack book = new ItemStack(Material.WRITABLE_BOOK);
                ItemMeta bookMeta = book.getItemMeta();
                if (bookMeta != null) {
                    bookMeta.setDisplayName(color("&e&lʙᴏᴜɴᴛʏ #" + (currentIndex + 1)));
                    List<String> lore = new ArrayList<>();
                    lore.add(color("&7ᴛᴀʀɢᴇᴛ: &c" + tName));
                    lore.add(color("&7ᴘᴇᴍʙᴜᴀᴛ: &f" + oName));
                    lore.add(color("&7ʜᴀᴅɪᴀʜ:"));

                    int shown = 0;
                    int all = data.items.size();
                    for (ItemStack item : data.items) {
                        if (shown < 4) {
                            String itemText = item.getType().name().toLowerCase().replace("_", " ");
                            if (item.getItemMeta() != null && item.getItemMeta().hasDisplayName()) {
                                itemText = item.getItemMeta().getDisplayName();
                            }
                            lore.add(color("&8- &f" + item.getAmount() + "x " + itemText));
                            shown++;
                        }
                    }
                    if (all > 4) {
                        lore.add(color("&8+ " + (all - 4) + " ɪᴛᴇᴍ ʟᴀɪɴɴʏᴀ"));
                    }
                    lore.add("");
                    lore.add(color("&e&lᴋʟɪᴋ ᴜɴᴛᴜᴋ ʟɪʜᴀᴛ ʜᴀᴅɪᴀʜ!"));
                    bookMeta.setLore(lore);
                    book.setItemMeta(bookMeta);
                }
                inv.setItem(slotIdx, book);
                slotMap.put(slotIdx, data.id);
                slotIdx++;
            }
            currentIndex++;
        }

        bountyMainMap.put(p.getUniqueId(), slotMap);
        p.openInventory(inv);
    }

    public void openPreview(Player p, int id) {
        String title = color(plugin.getConfig().getString("gui.preview-title", "&5&lʜᴀᴅɪᴀʜ ʙᴏᴜɴᴛʏ"));
        Inventory inv = Bukkit.createInventory(null, 27, title);

        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta paneMeta = pane.getItemMeta();
        if (paneMeta != null) { paneMeta.setDisplayName(" "); pane.setItemMeta(paneMeta); }

        for (int i = 0; i < 27; i++) inv.setItem(i, pane);

        BountyData data = manager.getBounties().get(id);
        if (data != null) {
            int slot = 0;
            for (ItemStack item : data.items) {
                if (slot < 27 && item != null && item.getType() != Material.AIR) {
                    inv.setItem(slot, item.clone());
                    slot++;
                }
            }
        }
        p.openInventory(inv);
    }

    public void openDeposit(Player p) {
        String title = color(plugin.getConfig().getString("gui.deposit-title", "&6&lᴍᴀsᴜᴋᴋᴀɴ ʜᴀᴅɪᴀʜ ʙᴏᴜɴᴛʏ"));
        Inventory inv = Bukkit.createInventory(null, 27, title);
        p.openInventory(inv);
    }

    public int getPage(Player p) { return bountyPage.getOrDefault(p.getUniqueId(), 1); }
    public Map<Integer, Integer> getSlotMap(Player p) { return bountyMainMap.get(p.getUniqueId()); }
}
