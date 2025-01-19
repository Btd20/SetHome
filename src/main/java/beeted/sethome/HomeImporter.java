package beeted.sethome;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class HomeImporter {
    private final SetHome plugin;
    public HomeImporter (SetHome plugin) {this.plugin = plugin;}
    public void importHomesFromEssentialsForAllPlayers(CommandSender sender) {
        File essentialsDir = new File(plugin.getDataFolder().getParentFile(), "Essentials/userdata");

        File pluginDataDir = new File(plugin.getDataFolder(), "data");

        if (!essentialsDir.exists() || !essentialsDir.isDirectory()) {
            String message = "Essentials userdata directory not found: " + essentialsDir.getAbsolutePath();
            sender.sendMessage(ChatColor.RED + message);
            plugin.getLogger().warning(message);
            return;
        }

        File[] playerFiles = essentialsDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (playerFiles == null || playerFiles.length == 0) {
            String message = "No player data files found in Essentials/userdata.";
            sender.sendMessage(ChatColor.RED + message);
            plugin.getLogger().warning(message);
            return;
        }

        sender.sendMessage(ChatColor.GREEN + "Starting to import homes from Essentials for all players...");

        for (File essentialsPlayerFile : playerFiles) {
            try {
                FileConfiguration essentialsConfig = YamlConfiguration.loadConfiguration(essentialsPlayerFile);
                ConfigurationSection homesSection = essentialsConfig.getConfigurationSection("homes");

                if (homesSection == null) {
                    String message = "No homes found for player file: " + essentialsPlayerFile.getName();
                    sender.sendMessage(ChatColor.YELLOW + message);
                    plugin.getLogger().info(message);
                    continue;
                }

                String uuidString = essentialsPlayerFile.getName().replace(".yml", "");
                File pluginPlayerFile = new File(pluginDataDir, uuidString + ".yml");
                if (!pluginPlayerFile.exists()) {
                    pluginPlayerFile.getParentFile().mkdirs();
                    pluginPlayerFile.createNewFile();
                }

                FileConfiguration pluginConfig = YamlConfiguration.loadConfiguration(pluginPlayerFile);

                List<String> homeList = pluginConfig.getStringList("homes");
                if (homeList == null) {
                    homeList = new ArrayList<>();
                }

                for (String homeName : homesSection.getKeys(false)) {
                    if (!homeList.contains(homeName)) {
                        homeList.add(homeName);

                        ConfigurationSection homeData = homesSection.getConfigurationSection(homeName);
                        ConfigurationSection newHomeData = pluginConfig.createSection(homeName);
                        newHomeData.set("world", homeData.getString("world-name"));
                        newHomeData.set("x", homeData.getDouble("x"));
                        newHomeData.set("y", homeData.getDouble("y"));
                        newHomeData.set("z", homeData.getDouble("z"));
                    }
                }

                pluginConfig.set("homes", homeList);
                pluginConfig.save(pluginPlayerFile);

                String message = "Successfully imported homes for player UUID: " + uuidString;
                sender.sendMessage(ChatColor.GREEN + message);
                plugin.getLogger().info(message);
            } catch (Exception e) {
                String message = "An error occurred while importing homes for file: " + essentialsPlayerFile.getName();
                sender.sendMessage(ChatColor.RED + message);
                plugin.getLogger().warning(message);
                e.printStackTrace();
            }
        }

        sender.sendMessage(ChatColor.GREEN + "All player homes have been successfully imported from Essentials!");
        plugin.getLogger().info("All player homes have been successfully imported from Essentials!");
    }
}
