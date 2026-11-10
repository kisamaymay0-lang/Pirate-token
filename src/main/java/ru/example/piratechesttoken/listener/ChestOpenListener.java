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
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import ru.example.piratechesttoken.config.ConfigManager;
import ru.example.piratechesttoken.util.TokenVisuals;

public final class ChestOpenListener implements Listener {

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final TokenVisuals tokenVisuals;
    private final Map<String, AnimationSequence> activeByChest = new HashMap<>();
    private final Map<UUID, AnimationSequence> activeByOwner = new HashMap<>();

    public ChestOpenListener(JavaPlugin plugin, ConfigManager config, TokenVisuals tokenVisuals) {
        this.plugin = plugin;
        this.config = config;
        this.tokenVisuals = tokenVisuals;
    }

    /** On a winning roll, cancel the normal open and run the entity transformation sequence. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryOpen(InventoryOpenEvent event) {
        HumanEntity human = event.getPlayer();
        if (!(human instanceof Player player) || event.isCancelled()) {
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
        sequence.startChestEntityAnimation();
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
        if (!isChest(block.getType())) {
            return;
        }
        uniqueBlocks.putIfAbsent(blockKey(block), block);
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
        private final List<BlockDisplay> chestDisplays = new ArrayList<>();
        private final List<Location> displayOrigins = new ArrayList<>();

        private BukkitTask task;
        private TextDisplay tokenDisplay;
        private org.bukkit.entity.ArmorStand glowStand;
        private int shakeTicks;
        private int tokenTicks;
        private boolean chestDestroyed;
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

        private void startChestEntityAnimation() {
            if (!owner.isOnline() || !hasChestBlock()) {
                abort();
                return;
            }

            createChestLikeDisplayEntities();
            if (chestDisplays.isEmpty()) {
                abort();
                return;
            }

            // A chest is a block entity, not a normal renderable block model. Hide the real
            // chest only for viewers of this effect and show a small vanilla display-entity
            // approximation (oak body, lid and gold lock) in its place.
            for (Block block : chestBlocks) {
                for (Player viewer : viewers) {
                    if (viewer.isOnline() && viewer.getWorld().equals(block.getWorld())) {
                        viewer.sendBlockChange(block.getLocation(), Material.AIR.createBlockData());
                    }
                }
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
                    vanishChestWithParticlesAndBreak();
                    return;
                }

                moveChestDisplays(shakeTicks);
                if (config.isParticleEnabled() && shakeTicks % 2 == 0) {
                    center.getWorld().spawnParticle(Particle.BLOCK, center, 7,
                            0.35D, 0.22D, 0.35D, 0.04D,
                            Material.OAK_PLANKS.createBlockData());
                }
                if (config.isSoundEnabled() && shakeTicks % 4 == 0) {
                    center.getWorld().playSound(center, Sound.BLOCK_CHEST_CLOSE,
                            0.45F, 0.8F + (shakeTicks % 3) * 0.15F);
                }
                shakeTicks++;
            }, 1L, 1L);
        }

        private void createChestLikeDisplayEntities() {
            for (Block block : chestBlocks) {
                if (!isChest(block.getType())) {
                    continue;
                }
                spawnDisplayPart(block, Material.OAK_PLANKS,
                        new Vector3f(0.05F, 0.06F, 0.06F),
                        new Vector3f(0.90F, 0.60F, 0.88F));
                spawnDisplayPart(block, Material.OAK_PLANKS,
                        new Vector3f(0.02F, 0.69F, 0.04F),
                        new Vector3f(0.96F, 0.25F, 0.92F));
                spawnDisplayLock(block);
            }
        }

        private void spawnDisplayPart(Block source, Material material,
                                      Vector3f translation, Vector3f scale) {
            Entity entity = source.getWorld().spawnEntity(
                    source.getLocation(), EntityType.BLOCK_DISPLAY);
            if (!(entity instanceof BlockDisplay display)) {
                entity.remove();
                return;
            }
            display.setBlock(material.createBlockData());
            display.setTransformation(new Transformation(
                    translation, new Quaternionf(), scale, new Quaternionf()));
            display.setGravity(false);
            display.setInvulnerable(true);
            display.setPersistent(false);
            showDisplayOnlyToViewers(display);
            chestDisplays.add(display);
            displayOrigins.add(display.getLocation().clone());
        }

        private void spawnDisplayLock(Block source) {
            BlockFace facing = BlockFace.NORTH;
            BlockData sourceData = source.getBlockData();
            if (sourceData instanceof Directional directional) {
                facing = directional.getFacing();
            }

            Vector3f translation;
            Vector3f scale;
            switch (facing) {
                case SOUTH -> {
                    translation = new Vector3f(0.44F, 0.38F, 0.94F);
                    scale = new Vector3f(0.12F, 0.16F, 0.08F);
                }
                case EAST -> {
                    translation = new Vector3f(0.94F, 0.38F, 0.44F);
                    scale = new Vector3f(0.08F, 0.16F, 0.12F);
                }
                case WEST -> {
                    translation = new Vector3f(-0.02F, 0.38F, 0.44F);
                    scale = new Vector3f(0.08F, 0.16F, 0.12F);
                }
                default -> {
                    translation = new Vector3f(0.44F, 0.38F, -0.02F);
                    scale = new Vector3f(0.12F, 0.16F, 0.08F);
                }
            }
            spawnDisplayPart(source, Material.GOLD_BLOCK, translation, scale);
        }

        private void showDisplayOnlyToViewers(BlockDisplay display) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.hideEntity(plugin, display);
            }
            for (Player viewer : viewers) {
                if (viewer.isOnline()) {
                    viewer.showEntity(plugin, display);
                }
            }
        }

        private void moveChestDisplays(int tick) {
            double side = tick % 2 == 0 ? 0.045D : -0.045D;
            double forward = tick % 4 < 2 ? -0.035D : 0.035D;
            double lift = tick % 3 == 0 ? 0.025D : 0.0D;
            for (int index = 0; index < chestDisplays.size(); index++) {
                BlockDisplay display = chestDisplays.get(index);
                if (display.isDead()) {
                    continue;
                }
                Location moved = displayOrigins.get(index).clone().add(side, lift, forward);
                display.teleport(moved);
            }
        }

        private void vanishChestWithParticlesAndBreak() {
            if (!hasChestBlock()) {
                abort();
                return;
            }

            for (Block block : chestBlocks) {
                if (isChest(block.getType())) {
                    center.getWorld().spawnParticle(Particle.BLOCK,
                            block.getLocation().add(0.5D, 0.5D, 0.5D),
                            24, 0.32D, 0.32D, 0.32D, 0.06D,
                            Material.OAK_PLANKS.createBlockData());
                }
            }
            center.getWorld().spawnParticle(Particle.POOF, center, 16,
                    0.35D, 0.45D, 0.35D, 0.08D);
            removeChestDisplays();
            breakChestAndDropContents();
            chestDestroyed = true;
            startTokenAnimation();
        }

        private void breakChestAndDropContents() {
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (viewer.getOpenInventory().getTopInventory() == inventory) {
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
                block.setType(Material.AIR, false);
                removedAnyBlock = true;
            }

            if (removedAnyBlock) {
                center.getWorld().playSound(center, Sound.BLOCK_WOOD_BREAK, 1.0F, 1.0F);
                center.getWorld().dropItemNaturally(center, new ItemStack(chestDrop));
                for (ItemStack stack : contents) {
                    center.getWorld().dropItemNaturally(center, stack);
                }
            }
        }

        private void startTokenAnimation() {
            if (!owner.isOnline()) {
                abort();
                return;
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
                    cleanupAndUnlock();
                    return;
                }
                if (tokenTicks < config.getGlowDurationTicks() && glowStand != null) {
                    glowStand.setGlowing(true);
                } else if (glowStand != null) {
                    glowStand.remove();
                    glowStand = null;
                }
                tokenVisuals.advanceTokenDisplay(tokenDisplay, center, tokenTicks);
                tokenTicks++;
            }, 1L, 1L);
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

        private void removeChestDisplays() {
            for (BlockDisplay display : chestDisplays) {
                if (!display.isDead()) {
                    display.remove();
                }
            }
            chestDisplays.clear();
            displayOrigins.clear();
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
            cleanupAndUnlock();
        }

        private void cleanupAndUnlock() {
            if (task != null) {
                task.cancel();
                task = null;
            }
            removeChestDisplays();
            if (tokenDisplay != null && !tokenDisplay.isDead()) {
                tokenDisplay.remove();
                tokenDisplay = null;
            }
            if (glowStand != null && !glowStand.isDead()) {
                glowStand.remove();
                glowStand = null;
            }
            restoreChestForViewers();
            activeByOwner.remove(owner.getUniqueId(), this);
            activeByChest.remove(chestKey, this);
            finishing = true;
        }
    }
}
