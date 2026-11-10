package ru.example.piratechesttoken;

import org.bukkit.plugin.java.JavaPlugin;
import ru.example.piratechesttoken.config.ConfigManager;
import ru.example.piratechesttoken.listener.ChestOpenListener;
import ru.example.piratechesttoken.util.TokenVisuals;

public final class PirateChestTokenPlugin extends JavaPlugin {

    private ConfigManager configManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        configManager = new ConfigManager(this);

        TokenVisuals tokenVisuals = new TokenVisuals(this, configManager);
        getServer().getPluginManager().registerEvents(
                new ChestOpenListener(this, configManager, tokenVisuals), this);

        getLogger().info("PirateChestToken enabled.");
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }
}
