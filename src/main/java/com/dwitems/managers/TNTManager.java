package com.dwitems.managers;

import com.dwitems.DWItems;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class TNTManager {
    private final DWItems plugin;
    private final File tntFile;
    private FileConfiguration tntConfig;
    private final Map<Location, String> customTNTLocations; // Location -> TNT ID

    public TNTManager(DWItems plugin) {
        this.plugin = plugin;
        this.customTNTLocations = new HashMap<>();
        this.tntFile = new File(plugin.getDataFolder() + "/playerdata", "tnt.yml");
        loadTNTLocations();
    }

    private void loadTNTLocations() {
        if (!tntFile.exists()) {
            tntFile.getParentFile().mkdirs();
            plugin.saveResource("playerdata/tnt.yml", false);
        }

        tntConfig = YamlConfiguration.loadConfiguration(tntFile);

        if (tntConfig.contains("tnt_locations")) {
            for (String key : tntConfig.getConfigurationSection("tnt_locations").getKeys(false)) {
                String worldName = tntConfig.getString("tnt_locations." + key + ".world");
                int x = tntConfig.getInt("tnt_locations." + key + ".x");
                int y = tntConfig.getInt("tnt_locations." + key + ".y");
                int z = tntConfig.getInt("tnt_locations." + key + ".z");
                String tntId = tntConfig.getString("tnt_locations." + key + ".tnt_id");

                // Создаем локацию с блочными координатами
                Location loc = new Location(
                        plugin.getServer().getWorld(worldName),
                        x,
                        y,
                        z,
                        0, // pitch
                        0  // yaw
                );
                customTNTLocations.put(loc, tntId);
            }
        }
    }

    public void saveTNTLocations() {
        tntConfig.set("tnt_locations", null); // Очищаем старые данные

        int index = 0;
        for (Map.Entry<Location, String> entry : customTNTLocations.entrySet()) {
            Location loc = entry.getKey();
            String tntId = entry.getValue();

            String path = "tnt_locations." + index;
            tntConfig.set(path + ".world", loc.getWorld().getName());
            tntConfig.set(path + ".x", loc.getBlockX());
            tntConfig.set(path + ".y", loc.getBlockY());
            tntConfig.set(path + ".z", loc.getBlockZ());
            tntConfig.set(path + ".tnt_id", tntId);

            index++;
        }

        try {
            tntConfig.save(tntFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Ошибка при сохранении tnt.yml: " + e.getMessage());
        }
    }


    private boolean locationsEqual(Location loc1, Location loc2) {
        boolean equal = loc1.getWorld().equals(loc2.getWorld()) &&
                loc1.getBlockX() == loc2.getBlockX() &&
                loc1.getBlockY() == loc2.getBlockY() &&
                loc1.getBlockZ() == loc2.getBlockZ();

        return equal;
    }

    public void addTNTLocation(Location location, String tntId) {
        Location blockLoc = new Location(
                location.getWorld(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ()
        );

        customTNTLocations.put(blockLoc, tntId);
        saveTNTLocations();

        if (blockLoc.getBlock().getType() != Material.TNT) {
            blockLoc.getBlock().setType(Material.TNT);
        }
    }

    public Set<Location> getAllLocations() {
        return new HashSet<>(customTNTLocations.keySet());
    }

    public boolean isCustomTNTLocation(Location location) {
        Location blockLoc = new Location(
                location.getWorld(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ()
        );

        for (Location savedLoc : customTNTLocations.keySet()) {
            if (locationsEqual(blockLoc, savedLoc)) {
                return true;
            }
        }
        return false;
    }

    public String getTNTId(Location location) {
        Location blockLoc = new Location(
                location.getWorld(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ()
        );

        for (Map.Entry<Location, String> entry : customTNTLocations.entrySet()) {
            if (locationsEqual(blockLoc, entry.getKey())) {
                String id = entry.getValue();
                return id;
            }
        }

        return null;
    }

    public void removeTNTLocation(Location location) {
        Location blockLoc = new Location(
                location.getWorld(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ()
        );

        String removedId = customTNTLocations.remove(blockLoc);
        if (removedId != null) {
            saveTNTLocations();
        } else {
        }
    }

}