package ru.example.piratechesttoken.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class ConfigManager {

    private final JavaPlugin plugin;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
    }

    private FileConfiguration config() {
        return plugin.getConfig();
    }

    public double getDropChance() {
        double chance = config().getDouble("drop-chance", 0.001D);
        if (!Double.isFinite(chance)) {
            return 0.001D;
        }
        return Math.max(0.0D, Math.min(1.0D, chance));
    }

    public boolean isDebug() {
        return config().getBoolean("debug", false);
    }

    public boolean isTitleEnabled() {
        return config().getBoolean("title-enabled", true);
    }

    public boolean isSoundEnabled() {
        return config().getBoolean("sound-enabled", true);
    }

    public boolean isParticleEnabled() {
        return config().getBoolean("particle-enabled", true);
    }

    public int getGlowRadius() {
        return Math.max(0, config().getInt("glow-radius", 30));
    }

    public int getGlowDurationTicks() {
        return Math.max(1, config().getInt("glow-duration-ticks", 30));
    }

    public int getShakeDurationTicks() {
        return Math.max(4, config().getInt("shake-duration-ticks", 12));
    }

    public int getTokenAnimationTicks() {
        return Math.max(10, config().getInt("token-animation-ticks", 50));
    }

    public String getTokenName() {
        String name = config().getString("token-name", "🏴‍☠️ Pirate Shop Access");
        return name == null || name.isBlank() ? "🏴‍☠️ Pirate Shop Access" : name;
    }
}
