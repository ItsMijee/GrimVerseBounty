package main;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public class BountyCommand implements CommandExecutor {

    private final GrimVerseBounty plugin;
    private final BountyManager manager;
    private final BountyGUI gui;

    public BountyCommand(GrimVerseBounty plugin, BountyManager manager, BountyGUI gui) {
        this.plugin = plugin;
        this.manager = manager;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(manager.getRawMsg("player-only"));
            return true;
        }
        Player p = (Player) sender;

        if (command.getName().equalsIgnoreCase("bountyclaim")) {
            if (!manager.hasRefund(p)) {
                p.sendMessage(manager.getMsg("no-claim-pending"));
                manager.playSound(p, "error");
                return true;
            }
            manager.deliverRefund(p);
            return true;
        }

        if (command.getName().equalsIgnoreCase("bounty")) {
            if (args.length == 0) {
                gui.openMain(p, 1);
                manager.playSound(p, "open-gui");
                return true;
            }

            if (args[0].equalsIgnoreCase("reload")) {
                if (!p.hasPermission("bounty.admin")) {
                    p.sendMessage(manager.getMsg("no-permission"));
                    manager.playSound(p, "error");
                    return true;
                }
                plugin.reloadConfig();
                manager.loadData();
                p.sendMessage(manager.getMsg("reload-success"));
                manager.playSound(p, "success");
                return true;
            }
            
            if (args[0].equalsIgnoreCase("help")) {
                for(String s : plugin.getConfig().getStringList("messages.help")) {
                    p.sendMessage(s.replace("&", "ยง"));
                }
                return true;
            }

            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                p.sendMessage(manager.getMsg("invalid-player"));
                manager.playSound(p, "error");
                return true;
            }

            if (target.equals(p)) {
                p.sendMessage(manager.getMsg("cannot-self"));
                manager.playSound(p, "error");
                return true;
            }

            int limit = manager.getPlayerBountyLimit(p);
            int count = manager.getBountyCount(p);

            if (count >= limit) {
                String msg = manager.getRawMsg("limit-reached").replace("%current%", count+"").replace("%limit%", limit+"");
                p.sendMessage(plugin.getConfig().getString("options.prefix").replace("&", "ยง") + msg);
                manager.playSound(p, "error");
                return true;
            }

            int maxGlobal = plugin.getConfig().getInt("options.max-global", 90);
            if (manager.getBounties().size() >= maxGlobal) {
                if (!manager.evictLowestPriority(limit)) {
                    p.sendMessage(manager.getMsg("global-full"));
                    manager.playSound(p, "error");
                    return true;
                }
            }

            manager.setSetup(p.getUniqueId(), target.getUniqueId());
            gui.openDeposit(p);
            
            String msg = manager.getRawMsg("deposit-instruction").replace("%target%", target.getName());
            p.sendMessage(plugin.getConfig().getString("options.prefix").replace("&", "ยง") + msg);
            manager.playSound(p, "deposit");
            return true;
        }

        return true;
    }
}
