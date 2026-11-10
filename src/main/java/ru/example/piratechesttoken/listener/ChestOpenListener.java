package ru.example.piratechesttoken.listener;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.Block;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.java.JavaPlugin;
import ru.example.piratechesttoken.config.ConfigManager;
import ru.example.piratechesttoken.util.TokenVisuals;

public final class ChestOpenListener implements Listener {

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final TokenVisuals tokenVisuals;

    public ChestOpenListener(JavaPlugin plugin, ConfigManager config, TokenVisuals tokenVisuals) {
        this.plugin = plugin;
        this.config = config;
        this.tokenVisuals = tokenVisuals;
    }

    /**
     * InventoryOpenEvent runs for the actual inventory being opened, rather than for
     * every right-click attempt. A double chest has one combined inventory and thus
     * reaches this handler once; we still inspect both block halves for client visuals.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        HumanEntity human = event.getPlayer();
        if (!(human instanceof Player player)) {
            return;
        }

        List<Block> chestBlocks = findChestBlocks(event.getInventory().getHolder());
        if (chestBlocks.isEmpty()) {
            return;
        }

        double chance = config.getDropChance();
        if (ThreadLocalRandom.current().nextDouble() >= chance) {
            if (config.isDebug()) {
                plugin.getLogger().info("No token drop for " + player.getName()
                        + " (chance=" + chance + ").");
            }
            return;
        }

        // Drop exactly one token for this inventory-open event (including double chests).
        player.getWorld().dropItemNaturally(player.getLocation(), tokenVisuals.createTokenItem());
        tokenVisuals.playOwnerReward(player);
        tokenVisuals.showChestEffectToOthers(player, chestBlocks);

        if (config.isDebug()) {
            plugin.getLogger().info("Pirate Shop token dropped for " + player.getName() + ".");
        }
    }

    private List<Block> findChestBlocks(InventoryHolder holder) {
        Map<String, Block> uniqueBlocks = new LinkedHashMap<>();
        if (holder instanceof Chest chest) {
            addChestBlock(chest, uniqueBlocks);
        } else if (holder instanceof DoubleChest doubleChest) {
            addChestHolder(doubleChest.getLeftSide(), uniqueBlocks);
            addChestHolder(doubleChest.getRightSide(), uniqueBlocks);
        }
        return new ArrayList<>(uniqueBlocks.values());
    }

    private void addChestHolder(InventoryHolder holder, Map<String, Block> uniqueBlocks) {
        if (holder instanceof Chest chest) {
            addChestBlock(chest, uniqueBlocks);
        }
    }

    private void addChestBlock(Chest chest, Map<String, Block> uniqueBlocks) {
        Block block = chest.getBlock();
        Material type = block.getType();
        if (type != Material.CHEST && type != Material.TRAPPED_CHEST) {
            return;
        }

        String key = block.getWorld().getUID() + ":" + block.getX() + ":"
                + block.getY() + ":" + block.getZ();
        uniqueBlocks.putIfAbsent(key, block);
    }
}
