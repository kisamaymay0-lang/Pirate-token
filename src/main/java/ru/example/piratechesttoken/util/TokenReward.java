package ru.example.piratechesttoken.util;

import java.util.Map;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import ru.example.piratechesttoken.config.ConfigManager;

/**
 * Награда за находку: настоящий предмет токена с секретной меткой (PDC),
 * чтобы его можно было отличить от обычных предметов.
 */
public final class TokenReward {

    private final ConfigManager config;
    private final NamespacedKey key;

    public TokenReward(Plugin plugin, ConfigManager config) {
        this.config = config;
        this.key = new NamespacedKey(plugin, "token");
    }

    public ItemStack create() {
        ItemStack item = new ItemStack(config.getRewardMaterial(), 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Texts.text(config.getRewardName()));
            if (!config.getRewardLore().isEmpty()) {
                meta.lore(Texts.lines(config.getRewardLore()));
            }
            if (config.isRewardGlow()) {
                meta.setEnchantmentGlintOverride(true);
            }
            meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Выдать токен игроку; если инвентарь полон — упасть под ноги. */
    public void give(Player player) {
        if (!config.isRewardEnabled()) {
            return;
        }
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(create());
        for (ItemStack rest : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), rest);
        }
    }
}
