package ru.example.piratechesttoken.listener;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import ru.example.piratechesttoken.config.ConfigManager;
import ru.example.piratechesttoken.util.TokenVisuals;

public final class ChestOpenListener implements Listener {

    private static final List<BlockFace> FACES = List.of(
            BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST);

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final TokenVisuals tokenVisuals;
    private final Map<String, AnimationSequence> activeByChest = new HashMap<>();
    private final Map<UUID, AnimationSequence> activeByOwner = new HashMap<>();
    private final Map<UUID, AnimationSequence> reopening = new HashMap<>();

    public ChestOpenListener(JavaPlugin plugin, ConfigManager config, TokenVisuals tokenVisuals) {
        this.plugin = plugin;
        this.config = config;
        this.tokenVisuals = tokenVisuals;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryOpen(InventoryOpenEvent event) {
        HumanEntity human = event.getPlayer();
        if (!(human instanceof Player player)) {
            return;
        }

        AnimationSequence reopeningSequence = reopening.get(player.getUniqueId());
        if (reopeningSequence != null) {
            if (event.isCancelled()) {
                reopening.remove(player.getUniqueId(), reopeningSequence);
                reopeningSequence.abort();
                return;
            }
            reopening.remove(player.getUniqueId(), reopeningSequence);
            reopeningSequence.onReopenAllowed();
            return;
        }

        if (event.isCancelled()) {
            return;
        }
        List<Block> chestBlocks = findChestBlocks(event.getInventory().getHolder());
        if (chestBlocks.isEmpty()) {
            return;
        }

        String chestKey = keyFor(chestBlocks);
        if (activeByChest.containsKey(chestKey)) {
            event.setCancelled(true);
            return;
        }

        double chance = config.getDropChance();
        if (ThreadLocalRandom.current().nextDouble() >= chance) {
            if (config.isDebug()) {
                plugin.getLogger().info("No token animation for " + player.getName()
                        + " (chance=" + chance + ").");
            }
            return;
        }

        event.setCancelled(true);
        AnimationSequence sequence = new AnimationSequence(
                player, event.getInventory(), chestBlocks, chestKey);
        activeByChest.put(chestKey, sequence);
        activeByOwner.put(player.getUniqueId(), sequence);
        sequence.startShake();
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        AnimationSequence sequence = activeByOwner.get(event.getPlayer().getUniqueId());
        if (sequence == null || sequence.finishing
                || !sequence.matchesChestInventory(event.getInventory())) {
            return;
        }
        sequence.abort();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        AnimationSequence sequence = activeByOwner.get(event.getPlayer().getUniqueId());
        if (sequence != null) {
            sequence.abort();
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
        if (isChest(block.getType())) {
            uniqueBlocks.putIfAbsent(blockKey(block), block);
        }
    }

    private boolean isChest(Material type) {
        return type == Material.CHEST || type == Material.TRAPPED_CHEST;
    }

    private String blockKey(Block block) {
        return block.getWorld().getUID() + ":" + block.getX() + ":"
                + block.getY() + ":" + block.getZ();
    }

    private String keyFor(List<Block> blocks) {
        return blocks.stream()
                .map(this::blockKey)
                .sorted(Comparator.naturalOrder())
                .reduce((left, right) -> left + "|" + right)
                .orElseThrow();
    }

    private Location centerOf(List<Block> blocks) {
        double x = 0.0D;
        double y = 0.0D;
        double z = 0.0D;
        for (Block block : blocks) {
            x += block.getX() + 0.5D;
            y += block.getY() + 0.5D;
            z += block.getZ() + 0.5D;
        }
        double count = blocks.size();
        return new Location(blocks.getFirst().getWorld(), x / count, y / count, z / count);
    }

    private final class AnimationSequence {

        private final Player owner;
        private final Inventory inventory;
        private final List<Block> chestBlocks;
        private final String chestKey;
        private final Location center;
        private final List<Player> viewers;

        private BukkitTask task;
        private TextDisplay tokenDisplay;
        private org.bukkit.entity.ArmorStand glowStand;
        private int shakeTicks;
        private int tokenTicks;
        private boolean finishing;

        private AnimationSequence(Player owner, Inventory inventory, List<Block> chestBlocks,
                                  String chestKey) {
            this.owner = owner;
            this.inventory = inventory;
            this.chestBlocks = List.copyOf(chestBlocks);
            this.chestKey = chestKey;
            this.center = centerOf(chestBlocks);
            List<Player> nearby = new ArrayList<>(tokenVisuals.getPlayersInRadius(
                    center, config.getGlowRadius()));
            if (!nearby.contains(owner)) {
                nearby.add(owner);
            }
            this.viewers = List.copyOf(nearby);
        }

        private void startShake() {
            if (!owner.isOnline() || !hasChestBlock()) {
                abort();
                return;
            }
            if (config.isSoundEnabled()) {
                center.getWorld().playSound(center, Sound.BLOCK_CHEST_CLOSE, 0.8F, 0.7F);
            }
            task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!owner.isOnline() || !hasChestBlock()) {
                    abort();
                    return;
                }
                if (shakeTicks >= config.getShakeDurationTicks()) {
                    task.cancel();
                    task = null;
                    restoreChestForViewers();
                    beginReopen();
                    return;
                }

                shakeChestForViewers(shakeTicks);
                if (config.isParticleEnabled() && shakeTicks % 2 == 0) {
                    center.getWorld().spawnParticle(Particle.BLOCK, center, 6,
                            0.30D, 0.15D, 0.30D, 0.02D,
                            chestBlocks.getFirst().getBlockData());
                }
                if (config.isSoundEnabled() && shakeTicks % 4 == 0) {
                    center.getWorld().playSound(center, Sound.BLOCK_CHEST_CLOSE,
                            0.45F, 0.8F + (shakeTicks % 3) * 0.15F);
                }
                shakeTicks++;
            }, 1L, 1L);
        }

        private void shakeChestForViewers(int tick) {
            int offset = switch (tick % 4) {
                case 0 -> 1;
                case 1 -> 0;
                case 2 -> 3;
                default -> 0;
            };
            for (Block block : chestBlocks) {
                if (!isChest(block.getType())) {
                    continue;
                }
                BlockData fakeData = block.getBlockData().clone();
                if (fakeData instanceof Directional directional) {
                    BlockFace original = directional.getFacing();
                    int index = FACES.indexOf(original);
                    if (index >= 0) {
                        BlockFace fakeFacing = FACES.get((index + offset) % FACES.size());
                        if (directional.getFaces().contains(fakeFacing)) {
                            directional.setFacing(fakeFacing);
                        }
                    }
                }
                for (Player viewer : viewers) {
                    if (viewer.isOnline() && viewer.getWorld().equals(block.getWorld())) {
                        viewer.sendBlockChange(block.getLocation(), fakeData);
                    }
                }
            }
        }

        private void beginReopen() {
            if (!owner.isOnline() || !hasChestBlock()) {
                abort();
                return;
            }
            reopening.put(owner.getUniqueId(), this);
            owner.openInventory(inventory);
            if (reopening.remove(owner.getUniqueId(), this)) {
                abort();
            }
        }

        private void onReopenAllowed() {
            task = Bukkit.getScheduler().runTask(plugin, () -> {
                if (!owner.isOnline()
                        || !matchesChestInventory(owner.getOpenInventory().getTopInventory())
                        || !hasChestBlock()) {
                    abort();
                    return;
                }
                task = Bukkit.getScheduler().runTaskLater(plugin, this::startTokenAnimation, 10L);
            });
        }

        private void startTokenAnimation() {
            if (!owner.isOnline() || !hasChestBlock()) {
                abort();
                return;
            }
            if (config.isDebug()) {
                plugin.getLogger().info("Starting token text animation for " + owner.getName()
                        + " at " + center.getBlockX() + ", " + center.getBlockY() + ", "
                        + center.getBlockZ() + ".");
            }
            tokenVisuals.playOwnerReward(owner);
            tokenDisplay = tokenVisuals.createTokenDisplay(center);
            glowStand = tokenVisuals.createGlowStandForOthers(owner, center);
            tokenTicks = 0;
            task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!owner.isOnline()) {
                    abort();
                    return;
                }
                if (tokenTicks >= config.getTokenAnimationTicks()) {
                    task.cancel();
                    task = null;
                    finishAndBreakChest();
                    return;
                }
                if (tokenTicks >= config.getGlowDurationTicks() && glowStand != null) {
                    glowStand.remove();
                    glowStand = null;
                }
                tokenVisuals.advanceTokenDisplay(tokenDisplay, center, tokenTicks);
                tokenTicks++;
            }, 1L, 1L);
        }

        private void finishAndBreakChest() {
            if (!hasChestBlock()) {
                cleanupAndUnlock();
                return;
            }
            finishing = true;
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (matchesChestInventory(viewer.getOpenInventory().getTopInventory())) {
                    viewer.closeInventory();
                }
            }

            List<ItemStack> contents = new ArrayList<>();
            for (ItemStack stack : inventory.getStorageContents()) {
                if (stack != null && !stack.getType().isAir()) {
                    contents.add(stack.clone());
                }
            }
            inventory.clear();

            Material chestDrop = Material.CHEST;
            boolean removedAnyBlock = false;
            for (Block block : chestBlocks) {
                Material type = block.getType();
                if (!isChest(type)) {
                    continue;
                }
                if (!removedAnyBlock) {
                    chestDrop = type;
                }
                BlockData oldData = block.getBlockData();
                block.setType(Material.AIR, false);
                center.getWorld().spawnParticle(Particle.BLOCK,
                        block.getLocation().add(0.5D, 0.5D, 0.5D),
                        16, 0.25D, 0.25D, 0.25D, 0.05D, oldData);
                removedAnyBlock = true;
            }

            if (removedAnyBlock) {
                center.getWorld().playSound(center, Sound.BLOCK_WOOD_BREAK, 1.0F, 1.0F);
                center.getWorld().dropItemNaturally(center, new ItemStack(chestDrop));
                for (ItemStack stack : contents) {
                    center.getWorld().dropItemNaturally(center, stack);
                }
            }
            center.getWorld().spawnParticle(Particle.POOF, center, 12,
                    0.3D, 0.35D, 0.3D, 0.04D);
            cleanupAndUnlock();
        }

        private boolean matchesChestInventory(Inventory candidate) {
            List<Block> candidateBlocks = findChestBlocks(candidate.getHolder());
            return !candidateBlocks.isEmpty() && chestKey.equals(keyFor(candidateBlocks));
        }

        private boolean hasChestBlock() {
            return chestBlocks.stream().anyMatch(block -> isChest(block.getType()));
        }

        private void restoreChestForViewers() {
            for (Block block : chestBlocks) {
                World world = block.getWorld();
                if (!world.isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)) {
                    continue;
                }
                for (Player viewer : viewers) {
                    if (viewer.isOnline() && viewer.getWorld().equals(world)) {
                        viewer.sendBlockChange(block.getLocation(), block.getBlockData());
                    }
                }
            }
        }

        private void abort() {
            if (finishing) {
                return;
            }
            finishing = true;
            if (task != null) {
                task.cancel();
                task = null;
            }
            if (owner.isOnline()
                    && matchesChestInventory(owner.getOpenInventory().getTopInventory())) {
                owner.closeInventory();
            }
            cleanupAndUnlock();
        }

        private void cleanupAndUnlock() {
            if (task != null) {
                task.cancel();
                task = null;
            }
            if (tokenDisplay != null && !tokenDisplay.isDead()) {
                tokenDisplay.remove();
                tokenDisplay = null;
            }
            if (glowStand != null && !glowStand.isDead()) {
                glowStand.remove();
                glowStand = null;
            }
            reopening.remove(owner.getUniqueId(), this);
            restoreChestForViewers();
            activeByOwner.remove(owner.getUniqueId(), this);
            activeByChest.remove(chestKey, this);
            finishing = true;
        }
    }
}
