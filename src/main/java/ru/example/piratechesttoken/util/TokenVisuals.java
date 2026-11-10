package ru.example.piratechesttoken.util;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import ru.example.piratechesttoken.config.ConfigManager;

public final class TokenVisuals {

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final NamespacedKey tokenKey;

    public TokenVisuals(JavaPlugin plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        this.tokenKey = new NamespacedKey(plugin, "pirate_shop_token");
    }

    public ItemStack createTokenItem() {
        ItemStack token = new ItemStack(Material.PAPER);
        ItemMeta meta = token.getItemMeta();
        meta.displayName(Component.text(config.getTokenName()));
        meta.lore(List.of(Component.text("Доступ к Пиратскому Магазину")));
        meta.getPersistentDataContainer().set(tokenKey, PersistentDataType.BYTE, (byte) 1);
        token.setItemMeta(meta);
        return token;
    }

    public void playOwnerReward(Player owner) {
        if (!owner.isOnline()) {
            return;
        }

        if (config.isTitleEnabled()) {
            Title.Times times = Title.Times.times(
                    Duration.ZERO, Duration.ofSeconds(3), Duration.ZERO);
            owner.showTitle(Title.title(
                    Component.text(config.getTokenName()),
                    Component.text("Токен Пиратского Магазина найден!"),
                    times));
        }

        owner.sendActionBar(Component.text("🏴‍☠️ Вы нашли токен Пиратского Магазина!"));

        if (config.isSoundEnabled()) {
            owner.playSound(owner.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.0F);
        }

        if (config.isParticleEnabled()) {
            Location particleLocation = owner.getLocation().add(0.0D, 1.0D, 0.0D);
            owner.getWorld().spawnParticle(
                    Particle.TOTEM_OF_UNDYING, particleLocation, 35,
                    0.55D, 0.7D, 0.55D, 0.08D);
        }
    }

    public void showChestEffectToOthers(Player owner, List<Block> chestBlocks) {
        if (chestBlocks.isEmpty()) {
            return;
        }

        Location center = calculateCenter(chestBlocks);
        World world = center.getWorld();
        if (world == null) {
            return;
        }

        int radius = config.getGlowRadius();
        double radiusSquared = (double) radius * radius;
        Map<UUID, Player> nearbyOthers = new LinkedHashMap<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.equals(owner) || !player.getWorld().equals(world)) {
                continue;
            }
            if (player.getLocation().distanceSquared(center) <= radiusSquared) {
                nearbyOthers.put(player.getUniqueId(), player);
            }
        }

        ArmorStand glowStand = spawnGlowStand(center, nearbyOthers.values());
        List<Location> chestLocations = chestBlocks.stream()
                .map(block -> block.getLocation().clone())
                .toList();
        List<Player> blockViewers = new ArrayList<>();

        if (config.shouldRemoveChestForOthers()) {
            for (Player viewer : nearbyOthers.values()) {
                for (Location chestLocation : chestLocations) {
                    // sendBlockChange is a per-client illusion: it changes no server block,
                    // so the owner's still-open inventory and the real world remain intact.
                    viewer.sendBlockChange(chestLocation, Material.AIR.createBlockData());
                }
                blockViewers.add(viewer);
            }
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (glowStand != null && !glowStand.isDead()) {
                glowStand.remove();
            }

            // Restore the current block data, not a hard-coded chest block: the chest may
            // have been legitimately broken/replaced while this temporary client view ran.
            for (Player viewer : blockViewers) {
                if (!viewer.isOnline()) {
                    continue;
                }
                for (Location chestLocation : chestLocations) {
                    World chestWorld = chestLocation.getWorld();
                    if (chestWorld == null || !viewer.getWorld().equals(chestWorld)) {
                        continue;
                    }
                    int chunkX = chestLocation.getBlockX() >> 4;
                    int chunkZ = chestLocation.getBlockZ() >> 4;
                    if (!chestWorld.isChunkLoaded(chunkX, chunkZ)) {
                        continue;
                    }
                    viewer.sendBlockChange(chestLocation,
                            chestWorld.getBlockAt(chestLocation).getBlockData());
                }
            }
        }, config.getGlowDurationTicks());
    }

    private ArmorStand spawnGlowStand(Location chestCenter, Iterable<Player> visibleTo) {
        World world = chestCenter.getWorld();
        if (world == null) {
            return null;
        }

        Location standLocation = chestCenter.clone().add(0.0D, 0.15D, 0.0D);
        Entity spawned = world.spawnEntity(standLocation, EntityType.ARMOR_STAND);
        if (!(spawned instanceof ArmorStand stand)) {
            spawned.remove();
            return null;
        }

        stand.setInvisible(true);
        stand.setMarker(true);
        stand.setGlowing(true);
        stand.setGravity(false);
        stand.setInvulnerable(true);
        stand.setSilent(true);
        stand.setPersistent(false);

        // Hide it by default, then reveal it only to nearby non-owners. This keeps the
        // configured radius meaningful without changing visibility for unrelated players.
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.hideEntity(plugin, stand);
        }
        for (Player player : visibleTo) {
            if (player.isOnline()) {
                player.showEntity(plugin, stand);
            }
        }
        return stand;
    }

    private Location calculateCenter(List<Block> chestBlocks) {
        double x = 0.0D;
        double y = 0.0D;
        double z = 0.0D;
        for (Block block : chestBlocks) {
            x += block.getX() + 0.5D;
            y += block.getY() + 0.5D;
            z += block.getZ() + 0.5D;
        }
        double count = chestBlocks.size();
        return new Location(chestBlocks.getFirst().getWorld(), x / count, y / count, z / count);
    }
}
