package org.sethomegui.Importers;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.sethomegui.SetHomeGUI;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class HomeImporter {
    private final ConsoleCommandSender console = Bukkit.getConsoleSender();
    private final SetHomeGUI plugin;

    public HomeImporter(SetHomeGUI plugin) {
        this.plugin = plugin;
    }

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

                boolean modified = false;
                for (String homeName : homesSection.getKeys(false)) {
                    if (!homeList.contains(homeName)) {
                        homeList.add(homeName);

                        ConfigurationSection homeData = homesSection.getConfigurationSection(homeName);
                        if (homeData == null) continue;

                        pluginConfig.set(homeName + ".world", homeData.getString("world-name", "world"));
                        pluginConfig.set(homeName + ".x", homeData.getDouble("x"));
                        pluginConfig.set(homeName + ".y", homeData.getDouble("y"));
                        pluginConfig.set(homeName + ".z", homeData.getDouble("z"));
                        pluginConfig.set(homeName + ".yaw", homeData.getDouble("yaw", 0.0));
                        pluginConfig.set(homeName + ".pitch", homeData.getDouble("pitch", 0.0));
                        modified = true;
                    }
                }

                if (modified) {
                    pluginConfig.set("homes", homeList);
                    pluginConfig.save(pluginPlayerFile);
                    sender.sendMessage(ChatColor.GRAY + "Successfully imported homes for UUID: " + uuidString);
                }
            } catch (Exception e) {
                sender.sendMessage(ChatColor.RED + "An error occurred while importing file: " + essentialsPlayerFile.getName());
                e.printStackTrace();
            }
        }

        sender.sendMessage(ChatColor.GREEN + "All player homes have been successfully imported from Essentials!");
        plugin.getLogger().info("All player homes have been successfully imported from Essentials!");
    }

    public void importHomesFromHuskHomesForAllPlayers(CommandSender sender, String huskHomesDbPath) {
        File huskHomesDbFile = new File(plugin.getDataFolder().getParentFile(), huskHomesDbPath);
        File pluginDataDir = new File(plugin.getDataFolder(), "data");

        if (!huskHomesDbFile.exists()) {
            String message = "HuskHomes database file not found: " + huskHomesDbFile.getAbsolutePath();
            sender.sendMessage(ChatColor.RED + message);
            plugin.getLogger().warning(message);
            return;
        }

        sender.sendMessage(ChatColor.GREEN + "Starting to import homes from HuskHomes for all players...");

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + huskHomesDbFile.getAbsolutePath())) {
            String query = "SELECT h.owner_uuid, s.name, p.x, p.y, p.z, p.yaw, p.pitch, p.world_name " +
                    "FROM huskhomes_homes h " +
                    "JOIN huskhomes_saved_positions s ON h.saved_position_id = s.id " +
                    "JOIN huskhomes_position_data p ON s.position_id = p.id";

            try (PreparedStatement statement = connection.prepareStatement(query);
                 ResultSet resultSet = statement.executeQuery()) {

                while (resultSet.next()) {
                    String ownerUUID = resultSet.getString("owner_uuid");
                    String homeName = resultSet.getString("name");
                    double x = resultSet.getDouble("x");
                    double y = resultSet.getDouble("y");
                    double z = resultSet.getDouble("z");
                    float yaw = resultSet.getFloat("yaw");
                    float pitch = resultSet.getFloat("pitch");
                    String worldName = resultSet.getString("world_name");

                    File playerFile = new File(pluginDataDir, ownerUUID + ".yml");

                    if (!playerFile.exists()) {
                        playerFile.getParentFile().mkdirs();
                        playerFile.createNewFile();
                    }

                    FileConfiguration playerConfig = YamlConfiguration.loadConfiguration(playerFile);

                    List<String> homeList = playerConfig.getStringList("homes");
                    if (homeList == null) {
                        homeList = new ArrayList<>();
                    }

                    if (!homeList.contains(homeName)) {
                        homeList.add(homeName);
                        playerConfig.set("homes", homeList);

                        playerConfig.set(homeName + ".world", worldName);
                        playerConfig.set(homeName + ".x", x);
                        playerConfig.set(homeName + ".y", y);
                        playerConfig.set(homeName + ".z", z);
                        playerConfig.set(homeName + ".yaw", yaw);
                        playerConfig.set(homeName + ".pitch", pitch);

                        playerConfig.save(playerFile);
                    }
                }
            }

            sender.sendMessage(ChatColor.GREEN + "All player homes have been successfully imported from HuskHomes!");
            plugin.getLogger().info("All player homes have been successfully imported from HuskHomes!");

        } catch (Exception e) {
            String message = "An error occurred while importing homes from HuskHomes: " + e.getMessage();
            sender.sendMessage(ChatColor.RED + message);
            plugin.getLogger().warning(message);
            e.printStackTrace();
        }
    }
}