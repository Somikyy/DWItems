package com.dwitems.listeners;

import com.dwitems.DWItems;
import com.dwitems.managers.ItemManager;
import com.dwitems.utils.SharedShulkerInventory;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

public class ScroogeShulkerListener implements Listener {
    private final DWItems plugin;
    private final String customShulkerName;
    private final Map<String, SharedShulkerInventory> activeInventories = new HashMap<>();
    private final ItemManager itemManager;

    public ScroogeShulkerListener(DWItems plugin) {
        this.plugin = plugin;
        this.itemManager = plugin.getItemManager();
        this.customShulkerName = plugin.getItemManager().getItem("shulker_54").getItemMeta().getDisplayName();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onShulkerOpen(InventoryOpenEvent event) {
        if (!(event.getInventory().getHolder() instanceof ShulkerBox)) {
            return;
        }

        ShulkerBox shulker = (ShulkerBox) event.getInventory().getHolder();
        if (!isCustomShulker(shulker)) {
            return;
        }

        String locationKey = getLocationKey(shulker);
        SharedShulkerInventory sharedInv = activeInventories.get(locationKey);

        // Если шалкер уже открыт кем-то
        if (sharedInv != null && sharedInv.isOpen()) {
            event.setCancelled(true);
            return;
        }

        // Проверка блэклистов
        Player player = (Player) event.getPlayer();
        if (!itemManager.isItemAllowedInWorld("shulker_54", player.getWorld().getName())) {
            event.setCancelled(true);
            player.sendMessage("§6[§c✘§6] §cЗдесь этот предмет использовать нельзя!");
            return;
        }

        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionManager regions = container.get(BukkitAdapter.adapt(player.getWorld()));

        if (regions != null) {
            BlockVector3 loc = BlockVector3.at(
                    player.getLocation().getX(),
                    player.getLocation().getY(),
                    player.getLocation().getZ()
            );

            ApplicableRegionSet regionSet = regions.getApplicableRegions(loc);
            boolean hasWhitelistedRegion = false;

            // Сначала проверяем есть ли регион из вайтлиста
            for (ProtectedRegion region : regionSet) {
                if (region.getId().equals("__global__")) continue;
                if (itemManager.hasWhitelistedRegion("shulker_54", region.getId())) {
                    hasWhitelistedRegion = true;
                    break;
                }
            }

            // Если нет региона из вайтлиста, проверяем блэклист
            if (!hasWhitelistedRegion) {
                for (ProtectedRegion region : regionSet) {
                    if (region.getId().equals("__global__")) continue;
                    if (!itemManager.isItemAllowedInRegion("shulker_54", region.getId())) {
                        event.setCancelled(true);
                        player.sendMessage("§6[§c✘§6] §cЗдесь этот предмет использовать нельзя!");
                        return;
                    }
                }
            }
        }

        event.setCancelled(true);

        if (sharedInv == null) {
            sharedInv = new SharedShulkerInventory(customShulkerName, 54); // Изменено на 54 слота
            activeInventories.put(locationKey, sharedInv);

            // Загружаем содержимое
            File dataFolder = new File(plugin.getDataFolder(), "playerdata");
            File shulkerFile = new File(dataFolder, "scrooge_shulker.yml"); // Изменено имя файла

            if (shulkerFile.exists()) {
                YamlConfiguration config = YamlConfiguration.loadConfiguration(shulkerFile);
                if (config.contains(locationKey + ".contents")) {
                    @SuppressWarnings("unchecked")
                    List<ItemStack> contents = (List<ItemStack>) config.get(locationKey + ".contents");
                    sharedInv.getInventory().setContents(contents.toArray(new ItemStack[0]));
                }
            }
        }

        sharedInv.setOpen(true);
        event.getPlayer().openInventory(sharedInv.getInventory());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onShulkerClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof SharedShulkerInventory)) {
            return;
        }

        SharedShulkerInventory sharedInv = (SharedShulkerInventory) event.getInventory().getHolder();
        String locationKey = activeInventories.entrySet()
                .stream()
                .filter(entry -> entry.getValue() == sharedInv)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);

        if (locationKey != null) {
            saveShulkerContents(locationKey, sharedInv.getInventory().getContents());
            sharedInv.setOpen(false);
            activeInventories.remove(locationKey);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onShulkerBreak(BlockBreakEvent event) {
        // Проверяем наличие блока
        if (event.getBlock() == null) {
            return;
        }

        BlockState state = event.getBlock().getState();
        if (!(state instanceof ShulkerBox)) {
            return;
        }

        ShulkerBox shulker = (ShulkerBox) state;
        if (!isCustomShulker(shulker)) {
            return;
        }

        // Отменяем стандартный дроп
        event.setDropItems(false);

        // Проверяем права игрока на ломание блока
        Player player = event.getPlayer();
        if (player != null && !player.hasPermission("dwitems.break.shulker")) {
            event.setCancelled(true);
            player.sendMessage("§6[§c✘§6] §cУ вас нет прав ломать этот блок!");
            return;
        }

        String locationKey = getLocationKey(shulker);

        try {
            // Проверяем, не открыт ли шалкер сейчас
            SharedShulkerInventory activeInv = activeInventories.get(locationKey);
            if (activeInv != null && isShulkerInUse(activeInv)) {
                event.setCancelled(true);
                if (player != null) {
                    player.sendMessage("§6[§c✘§6] §cНельзя сломать открытый шалкер!");
                }
                return;
            }

            // Создаем копию предмета шалкера
            ItemStack shulkerItem = plugin.getItemManager().getItem("shulker_54").clone(); // Изменено на shulker_54
            if (shulkerItem == null) {
                plugin.getLogger().severe("Failed to create shulker item: item is null");
                event.setCancelled(true);
                return;
            }

            // Загружаем данные из конфига безопасно
            File dataFolder = new File(plugin.getDataFolder(), "playerdata");
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }

            File shulkerFile = new File(dataFolder, "scrooge_shulker.yml"); // Изменено имя файла

            if (shulkerFile.exists()) {
                YamlConfiguration config = YamlConfiguration.loadConfiguration(shulkerFile);

                // Безопасно получаем contents
                if (config.contains(locationKey + ".contents")) {
                    try {
                        @SuppressWarnings("unchecked")
                        List<ItemStack> contentsList = (List<ItemStack>) config.get(locationKey + ".contents");

                        if (contentsList != null) {
                            ItemMeta meta = shulkerItem.getItemMeta();
                            if (meta != null) {
                                // Сериализуем содержимое
                                String serializedContents = serializeContents(contentsList);
                                if (serializedContents != null) {
                                    // Сохраняем в NBT
                                    meta.getPersistentDataContainer().set(
                                            new NamespacedKey(plugin, "shared_shulker_contents"),
                                            PersistentDataType.STRING,
                                            serializedContents
                                    );
                                    shulkerItem.setItemMeta(meta);
                                }
                            }
                        }

                        // Безопасно удаляем данные из конфига
                        config.set(locationKey, null);
                        try {
                            config.save(shulkerFile);
                        } catch (IOException e) {
                            plugin.getLogger().warning("Error saving shulker config: " + e.getMessage());
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("Error processing shulker contents: " + e.getMessage());
                    }
                }
            }

            // Дроп предмета через задержку для избежания проблем с конкурентностью
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                try {
                    Location dropLocation = event.getBlock().getLocation().add(0.5, 0.5, 0.5);
                    event.getBlock().getWorld().dropItemNaturally(dropLocation, shulkerItem);
                } catch (Exception e) {
                    plugin.getLogger().severe("Error dropping shulker item: " + e.getMessage());
                }
            }, 1L);

        } catch (Exception e) {
            plugin.getLogger().severe("Critical error while breaking shulker: " + e.getMessage());
            e.printStackTrace();

            // В случае критической ошибки отменяем событие
            event.setCancelled(true);
            if (player != null) {
                player.sendMessage("§6[§c✘§6] §cПроизошла ошибка при ломании шалкера!");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onShulkerPlace(BlockPlaceEvent event) {
        Player player = (Player) event.getPlayer();
        ItemStack item = event.getItemInHand();
        if (!isCustomShulker(item)) {
            return;
        }

        // Проверка блэклистов
        if (!itemManager.isItemAllowedInWorld("shulker_54", player.getWorld().getName())) {
            event.setCancelled(true);
            player.sendMessage("§6[§c✘§6] §cЗдесь этот предмет использовать нельзя!");
            return;
        }

        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionManager regions = container.get(BukkitAdapter.adapt(player.getWorld()));

        if (regions != null) {
            BlockVector3 loc = BlockVector3.at(
                    player.getLocation().getX(),
                    player.getLocation().getY(),
                    player.getLocation().getZ()
            );

            ApplicableRegionSet regionSet = regions.getApplicableRegions(loc);
            boolean hasWhitelistedRegion = false;

            for (ProtectedRegion region : regionSet) {
                if (region.getId().equals("__global__")) continue;
                if (itemManager.hasWhitelistedRegion("shulker_54", region.getId())) {
                    hasWhitelistedRegion = true;
                    break;
                }
            }

            if (!hasWhitelistedRegion) {
                for (ProtectedRegion region : regionSet) {
                    if (region.getId().equals("__global__")) continue;
                    if (!itemManager.isItemAllowedInRegion("shulker_54", region.getId())) {
                        event.setCancelled(true);
                        player.sendMessage("§6[§c✘§6] §cЗдесь этот предмет использовать нельзя!");
                        return;
                    }
                }
            }
        }

        ShulkerBox shulker = (ShulkerBox) event.getBlock().getState();
        shulker.setCustomName(item.getItemMeta().getDisplayName());

        ItemMeta meta = item.getItemMeta();
        String serializedContents = meta.getPersistentDataContainer().get(
                new NamespacedKey(plugin, "shared_shulker_contents"),
                PersistentDataType.STRING
        );

        if (serializedContents != null) {
            try {
                List<ItemStack> contentsList = deserializeContents(serializedContents);
                if (contentsList != null) {
                    String locationKey = getLocationKey(shulker);

                    File dataFolder = new File(plugin.getDataFolder(), "playerdata");
                    if (!dataFolder.exists()) {
                        dataFolder.mkdirs();
                    }

                    File shulkerFile = new File(dataFolder, "scrooge_shulker.yml");
                    YamlConfiguration config = YamlConfiguration.loadConfiguration(shulkerFile);

                    config.set(locationKey + ".contents", contentsList);
                    config.save(shulkerFile);

                    // Сохраняем первые 27 слотов в сам блок шалкера
                    ItemStack[] standardContents = new ItemStack[27];
                    System.arraycopy(contentsList.toArray(new ItemStack[0]), 0, standardContents, 0, Math.min(27, contentsList.size()));
                    shulker.getInventory().setContents(standardContents);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Error placing shulker: " + e.getMessage());
            }
        }

        shulker.update();
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof SharedShulkerInventory)) {
            return;
        }

        // Проверка на хоткей
        if (event.getHotbarButton() != -1) {
            ItemStack hotbarItem = event.getWhoClicked().getInventory().getItem(event.getHotbarButton());
            if (hotbarItem != null && hotbarItem.getType().name().contains("SHULKER_BOX")) {
                event.setCancelled(true);
                return;
            }
        }

        // Обычная проверка на перемещение мышкой
        ItemStack clickedItem = event.getCurrentItem();
        ItemStack cursorItem = event.getCursor();

        if ((clickedItem != null && clickedItem.getType().name().contains("SHULKER_BOX")) ||
                (cursorItem != null && cursorItem.getType().name().contains("SHULKER_BOX"))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityExplode(EntityExplodeEvent event) {
        handleExplosion(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockExplode(BlockExplodeEvent event) {
        handleExplosion(event.blockList());
    }

    private void handleExplosion(List<Block> blocks) {
        Iterator<Block> iterator = blocks.iterator();
        while (iterator.hasNext()) {
            Block block = iterator.next();
            if (block.getState() instanceof ShulkerBox) {
                ShulkerBox shulker = (ShulkerBox) block.getState();
                if (isCustomShulker(shulker)) {
                    iterator.remove();

                    String locationKey = getLocationKey(shulker);
                    SharedShulkerInventory activeInv = activeInventories.get(locationKey);
                    if (activeInv != null && isShulkerInUse(activeInv)) {
                        continue;
                    }

                    try {
                        // Создаем копию предмета шалкера
                        ItemStack shulkerItem = plugin.getItemManager().getItem("shulker_54").clone();

                        // Загружаем данные из конфига
                        File dataFolder = new File(plugin.getDataFolder(), "playerdata");
                        File shulkerFile = new File(dataFolder, "scrooge_shulker.yml");

                        if (shulkerFile.exists()) {
                            YamlConfiguration config = YamlConfiguration.loadConfiguration(shulkerFile);
                            if (config.contains(locationKey + ".contents")) {
                                @SuppressWarnings("unchecked")
                                List<ItemStack> contentsList = (List<ItemStack>) config.get(locationKey + ".contents");

                                ItemMeta meta = shulkerItem.getItemMeta();
                                String serializedContents = serializeContents(contentsList);
                                if (serializedContents != null && meta != null) {
                                    meta.getPersistentDataContainer().set(
                                            new NamespacedKey(plugin, "shared_shulker_contents"),
                                            PersistentDataType.STRING,
                                            serializedContents
                                    );
                                    shulkerItem.setItemMeta(meta);
                                }

                                config.set(locationKey, null);
                                config.save(shulkerFile);
                            }
                        }

                        // Дроп предмета
                        block.getWorld().dropItemNaturally(block.getLocation(), shulkerItem);

                        // Удаляем блок после всех операций
                        Bukkit.getScheduler().runTask(plugin, () -> block.setType(Material.AIR));

                    } catch (Exception e) {
                        plugin.getLogger().severe("Error handling explosion of shulker: " + e.getMessage());
                        // В случае ошибки дропаем пустой шалкер
                        ItemStack defaultShulker = plugin.getItemManager().getItem("shulker_54").clone();
                        if (defaultShulker != null) {
                            block.getWorld().dropItemNaturally(block.getLocation(), defaultShulker);
                        }
                    }
                }
            }
        }
    }

    // Вспомогательные методы
    private boolean isCustomShulker(ItemStack item) {
        if (item == null || item.getType() != Material.WHITE_SHULKER_BOX) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasDisplayName() &&
                meta.getDisplayName().equals(customShulkerName);
    }

    private boolean isCustomShulker(ShulkerBox shulker) {
        return shulker.getCustomName() != null &&
                shulker.getCustomName().equals(customShulkerName);
    }

    private boolean isShulkerInUse(SharedShulkerInventory sharedInv) {
        return sharedInv != null && sharedInv.isOpen();
    }

    private String getLocationKey(ShulkerBox shulker) {
        if (shulker == null || shulker.getLocation() == null ||
                shulker.getLocation().getWorld() == null) {
            return null;
        }

        return shulker.getLocation().getWorld().getName() + "," +
                shulker.getLocation().getBlockX() + "," +
                shulker.getLocation().getBlockY() + "," +
                shulker.getLocation().getBlockZ();
    }

    private void saveShulkerContents(String locationKey, ItemStack[] contents) {
        if (locationKey == null || contents == null) return;

        File dataFolder = new File(plugin.getDataFolder(), "playerdata");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        File shulkerFile = new File(dataFolder, "scrooge_shulker.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(shulkerFile);

        config.set(locationKey + ".contents", Arrays.asList(contents));
        try {
            config.save(shulkerFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Error saving shulker contents: " + e.getMessage());
        }
    }

    private String serializeContents(List<ItemStack> contents) {
        try {
            if (contents == null) return null;

            YamlConfiguration config = new YamlConfiguration();
            config.set("contents", contents);
            return Base64.getEncoder().encodeToString(
                    config.saveToString().getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            plugin.getLogger().warning("Error serializing contents: " + e.getMessage());
            return null;
        }
    }

    private List<ItemStack> deserializeContents(String data) {
        try {
            if (data == null) return null;

            String decoded = new String(
                    Base64.getDecoder().decode(data),
                    StandardCharsets.UTF_8
            );
            YamlConfiguration config = new YamlConfiguration();
            config.loadFromString(decoded);

            @SuppressWarnings("unchecked")
            List<ItemStack> contents = (List<ItemStack>) config.get("contents");
            return contents;
        } catch (Exception e) {
            plugin.getLogger().warning("Error deserializing contents: " + e.getMessage());
            return null;
        }
    }
}