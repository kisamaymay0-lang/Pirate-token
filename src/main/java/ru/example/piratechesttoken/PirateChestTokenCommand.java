package ru.example.piratechesttoken;

import java.util.List;
import java.util.Locale;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import ru.example.piratechesttoken.util.Texts;

/** /piratechesttoken reload (алиасы /pctoken, /pct). */
public final class PirateChestTokenCommand implements CommandExecutor, TabCompleter {

    private static final String PREFIX = "&8[&6PirateChestToken&8] &r";

    private final PirateChestTokenPlugin plugin;

    public PirateChestTokenCommand(PirateChestTokenPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1 || !args[0].equalsIgnoreCase("reload")) {
            send(sender, "&cИспользование: &e/piratechesttoken reload");
            return true;
        }
        if (!sender.hasPermission("piratechesttoken.admin")) {
            send(sender, "&cУ вас нет прав на эту команду!");
            return true;
        }
        plugin.reloadPlugin();
        send(sender, "&aКонфигурация перезагружена.");
        return true;
    }

    private void send(CommandSender sender, String message) {
        sender.sendMessage(Texts.text(PREFIX + message));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias,
                                      String[] args) {
        if (args.length == 1 && "reload".startsWith(args[0].toLowerCase(Locale.ROOT))) {
            return List.of("reload");
        }
        return List.of();
    }
}
