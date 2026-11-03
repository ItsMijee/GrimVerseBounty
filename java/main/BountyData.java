package main;

import org.bukkit.inventory.ItemStack;
import java.util.List;
import java.util.UUID;

public class BountyData {
    public int id;
    public UUID target;
    public UUID owner;
    public int ownerMaxLimit;
    public List<ItemStack> items;

    public BountyData(int id, UUID target, UUID owner, int ownerMaxLimit, List<ItemStack> items) {
        this.id = id;
        this.target = target;
        this.owner = owner;
        this.ownerMaxLimit = ownerMaxLimit;
        this.items = items;
    }
}
