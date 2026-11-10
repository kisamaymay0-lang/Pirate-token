package ru.example.piratechesttoken.config;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class ConfigManager {

    private final JavaPlugin plugin;
    private Set<String> enabledWorlds = Set.of("*");

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        Set<String> worlds = new HashSet<>();
        for (String world : plugin.getConfig().getStringList("enabled-worlds")) {
            worlds.add(world.toLowerCase(Locale.ROOT));
        }
        if (worlds.isEmpty()) {
            worlds.add("*");
        }
        this.enabledWorlds = worlds;
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

    public boolean isWorldEnabled(String worldName) {
        return enabledWorlds.contains("*")
                || enabledWorlds.contains(worldName.toLowerCase(Locale.ROOT));
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
        String name = config().getString("token-name",
                "&6&l\ud83c\udff4\u200d\u2620\ufe0f Токен Пиратского Магазина");
        return name == null || name.isBlank() ? "Токен Пиратского Магазина" : name;
    }

    public String getMessageSubtitle() {
        return config().getString("messages.subtitle", "Токен Пиратского Магазина найден!");
    }

    public String getMessageActionbar() {
        return config().getString("messages.actionbar",
                "Вы нашли токен Пиратского Магазина!");
    }

    public boolean isRewardEnabled() {
        return config().getBoolean("reward.enabled", true);
    }

    public Material getRewardMaterial() {
        String raw = config().getString("reward.material", "GOLD_NUGGET");
        Material material = raw == null ? null : Material.matchMaterial(raw);
        return material == null ? Material.GOLD_NUGGET : material;
    }

    public String getRewardName() {
        String name = config().getString("reward.name", getTokenName());
        return name == null || name.isBlank() ? getTokenName() : name;
    }

    public List<String> getRewardLore() {
        return config().getStringList("reward.lore");
    }

    public boolean isRewardGlow() {
        return config().getBoolean("reward.glow", true);
    }

    public boolean isDestroyChestOnFind() {
        return config().getBoolean("destroy-chest-on-find", false);
    }
}
