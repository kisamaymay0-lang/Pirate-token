package ru.example.piratechesttoken;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import ru.example.piratechesttoken.config.ConfigManager;
import ru.example.piratechesttoken.listener.ChestOpenListener;
import ru.example.piratechesttoken.util.TokenReward;
import ru.example.piratechesttoken.util.TokenVisuals;

public final class PirateChestTokenPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private ChestOpenListener listener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        configManager = new ConfigManager(this);

        TokenVisuals tokenVisuals = new TokenVisuals(this, configManager);
        TokenReward tokenReward = new TokenReward(this, configManager);
        listener = new ChestOpenListener(this, configManager, tokenVisuals, tokenReward);
        getServer().getPluginManager().registerEvents(listener, this);

        PluginCommand command = getCommand("piratechesttoken");
        if (command != null) {
            PirateChestTokenCommand executor = new PirateChestTokenCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        getLogger().info("PirateChestToken включён: токен Пиратского Магазина ждёт в сундуках.");
    }

    @Override
    public void onDisable() {
        if (listener != null) {
            listener.shutdown();
        }
    }

    public void reloadPlugin() {
        if (configManager != null) {
            configManager.reload();
        }
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }
}
