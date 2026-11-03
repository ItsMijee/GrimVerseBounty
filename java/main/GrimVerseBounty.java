package main;

import org.bukkit.plugin.java.JavaPlugin;

public class GrimVerseBounty extends JavaPlugin {

    private static GrimVerseBounty instance;
    private BountyManager bountyManager;
    private BountyGUI bountyGUI;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        // Inisialisasi Manager & GUI Builder
        bountyManager = new BountyManager(this);
        bountyGUI = new BountyGUI(this, bountyManager);

        // Register Commands
        BountyCommand cmd = new BountyCommand(this, bountyManager, bountyGUI);
        if (getCommand("bounty") != null) getCommand("bounty").setExecutor(cmd);
        if (getCommand("bountyclaim") != null) getCommand("bountyclaim").setExecutor(cmd);

        // Register Listeners
        getServer().getPluginManager().registerEvents(new BountyListener(this, bountyManager, bountyGUI), this);

        getLogger().info("§a[GrimVerseBounty] Ultimate Edition berhasil dimuat!");
    }

    @Override
    public void onDisable() {
        if (bountyManager != null) {
            bountyManager.saveData();
        }
        getLogger().info("§c[GrimVerseBounty] Data tersimpan aman, plugin dimatikan.");
    }

    public static GrimVerseBounty getInstance() {
        return instance;
    }
}
