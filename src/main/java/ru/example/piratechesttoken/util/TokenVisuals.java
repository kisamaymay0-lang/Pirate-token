package ru.example.piratechesttoken.util;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import ru.example.piratechesttoken.config.ConfigManager;

public final class TokenVisuals {

    private final JavaPlugin plugin;
    private final ConfigManager config;

    public TokenVisuals(JavaPlugin plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
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
            owner.playSound(owner.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
        }
    }

    /** Creates a floating text entity; it is an animation only and never an inventory item. */
    public TextDisplay createTokenDisplay(Location chestCenter) {
        World world = chestCenter.getWorld();
        if (world == null) {
            return null;
        }

        Location spawnLocation = chestCenter.clone().add(0.0D, 1.0D, 0.0D);
        Entity entity = world.spawnEntity(spawnLocation, EntityType.TEXT_DISPLAY);
        if (!(entity instanceof TextDisplay display)) {
            entity.remove();
            return null;
        }

        display.setText(config.getTokenName() + "\nТокен Пиратского Магазина!");
        display.setTextColor(Color.fromRGB(255, 220, 90));
        display.setTextOpacity((byte) 0xFF);
        display.setLineWidth(300);
        display.setAlignment(TextDisplay.TextAlignment.CENTER);
        display.setBillboard(Display.Billboard.CENTER);
        display.setSeeThrough(true);
        display.setShadowed(true);
        display.setDefaultBackground(false);
        display.setGlowing(true);
        display.setPersistent(false);
        return display;
    }

    public void advanceTokenDisplay(TextDisplay display, Location center, int frame) {
        if (display == null || display.isDead()) {
            return;
        }
        Location next = display.getLocation().add(0.0D, 0.025D, 0.0D);
        display.teleport(next);

        if (config.isParticleEnabled() && frame % 2 == 0) {
            World world = center.getWorld();
            if (world != null) {
                world.spawnParticle(Particle.TOTEM_OF_UNDYING,
                        display.getLocation(), 5, 0.28D, 0.12D, 0.28D, 0.03D);
                world.spawnParticle(Particle.ENCHANT,
                        display.getLocation(), 7, 0.35D, 0.18D, 0.35D, 0.2D);
            }
        }
    }

    /**
     * Adds the old glow cue for nearby bystanders only. The invisible marker stand is
     * a vanilla entity workaround: block states themselves do not support glowing.
     */
    public ArmorStand createGlowStandForOthers(Player owner, Location chestCenter) {
        World world = chestCenter.getWorld();
        if (world == null) {
            return null;
        }

        Location standLocation = chestCenter.clone().add(0.0D, 0.15D, 0.0D);
        Entity entity = world.spawnEntity(standLocation, EntityType.ARMOR_STAND);
        if (!(entity instanceof ArmorStand stand)) {
            entity.remove();
            return null;
        }

        stand.setInvisible(true);
        stand.setMarker(true);
        stand.setGlowing(true);
        stand.setGravity(false);
        stand.setInvulnerable(true);
        stand.setSilent(true);
        stand.setPersistent(false);

        List<Player> nearby = getPlayersInRadius(chestCenter, config.getGlowRadius());
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.hideEntity(plugin, stand);
        }
        for (Player player : nearby) {
            if (!player.equals(owner) && player.isOnline()) {
                player.showEntity(plugin, stand);
            }
        }
        return stand;
    }

    public List<Player> getPlayersInRadius(Location center, int radius) {
        World world = center.getWorld();
        if (world == null) {
            return List.of();
        }
        double radiusSquared = (double) Math.max(0, radius) * Math.max(0, radius);
        List<Player> nearby = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().equals(world)
                    && player.getLocation().distanceSquared(center) <= radiusSquared) {
                nearby.add(player);
            }
        }
        return nearby;
    }
}
