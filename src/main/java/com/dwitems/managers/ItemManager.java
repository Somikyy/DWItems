package com.dwitems.managers;

import com.dwitems.DWItems;
import com.dwitems.items.CustomItem;
import com.dwitems.utils.ColorConverter;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class ItemManager {
    private final DWItems plugin;
    private final Map<String, CustomItem> items;
    private final Map<String, List<String>> itemBlacklistedRegions;
    private final Map<String, List<String>> itemWhitelistedRegions;
    private final Map<String, List<String>> itemBlacklistedWorlds;
    private final File itemsFile;
    private FileConfiguration itemsConfig;

    public ItemManager(DWItems plugin) {
        this.plugin = plugin;
        this.items = new HashMap<>();
        this.itemBlacklistedRegions = new HashMap<>();
        this.itemWhitelistedRegions = new HashMap<>();
        this.itemBlacklistedWorlds = new HashMap<>();
        this.itemsFile = new File(plugin.getDataFolder(), "items.yml");
        loadItems();
    }

    public boolean isItemAllowedInRegion(String itemId, String regionId) {
        if (!items.containsKey(itemId)) {
            return true;
        }

        // Если регион в вайтлисте - разрешаем всегда (байпас блэклиста)
        if (itemWhitelistedRegions.containsKey(itemId) &&
                itemWhitelistedRegions.get(itemId).contains(regionId.toLowerCase())) {
            return true;
        }

        // Если регион в блэклисте и не в вайтлисте - запрещаем
        return !itemBlacklistedRegions.containsKey(itemId) ||
                !itemBlacklistedRegions.get(itemId).contains(regionId.toLowerCase());
    }

    public boolean isItemAllowedInWorld(String itemId, String worldName) {
        if (!items.containsKey(itemId)) {
            return true;
        }
        return !itemBlacklistedWorlds.containsKey(itemId) ||
                !itemBlacklistedWorlds.get(itemId).contains(worldName.toLowerCase());
    }

    public boolean hasWhitelistedRegion(String itemId, String regionId) {
        return itemWhitelistedRegions.containsKey(itemId) &&
                itemWhitelistedRegions.get(itemId).contains(regionId.toLowerCase());
    }

    public List<String> getItemIds() {
        return new ArrayList<>(items.keySet());
    }

    private void loadItems() {
        if (!itemsFile.exists()) {
            plugin.saveResource("items.yml", false);
        }

        itemsConfig = YamlConfiguration.loadConfiguration(itemsFile);

        items.clear();
        itemBlacklistedRegions.clear();
        itemBlacklistedWorlds.clear();

        for (String itemKey : itemsConfig.getConfigurationSection("items").getKeys(false)) {
            if (itemsConfig.contains("items." + itemKey + ".blacklist-regions")) {
                List<String> blacklistedRegions = itemsConfig.getStringList("items." + itemKey + ".blacklist-regions");
                itemBlacklistedRegions.put(itemKey, blacklistedRegions.stream()
                        .map(String::toLowerCase)
                        .collect(Collectors.toList()));
            }

            if (itemsConfig.contains("items." + itemKey + ".blacklist-worlds")) {
                List<String> blacklistedWorlds = itemsConfig.getStringList("items." + itemKey + ".blacklist-worlds");
                itemBlacklistedWorlds.put(itemKey, blacklistedWorlds.stream()
                        .map(String::toLowerCase)
                        .collect(Collectors.toList()));
            }

            if (itemsConfig.contains("items." + itemKey + ".whitelist-regions")) {
                List<String> whitelistedRegions = itemsConfig.getStringList("items." + itemKey + ".whitelist-regions");
                itemWhitelistedRegions.put(itemKey, whitelistedRegions.stream()
                        .map(String::toLowerCase)
                        .collect(Collectors.toList()));
            }
        }

        // Загрузка неразрушимых элитр
        if (itemsConfig.contains("items.unbreakable_elytra")) {
            ItemStack elytra = new ItemStack(Material.ELYTRA);
            ItemMeta meta = elytra.getItemMeta();
            if (meta != null) {
                meta.setUnbreakable(true);
                meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
                meta.addEnchant(Enchantment.DURABILITY, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                elytra.setItemMeta(meta);
            }

            String displayName = itemsConfig.getString("items.unbreakable_elytra.display_name");
            String configId = itemsConfig.getString("items.unbreakable_elytra.id");
            List<String> lore = itemsConfig.getStringList("items.unbreakable_elytra.lore");

            items.put("unbreakable_elytra", new CustomItem(
                    "unbreakable_elytra",
                    displayName,
                    lore,
                    elytra,
                    configId
            ));
        }

        // Загрузка тепловизора
        if (itemsConfig.contains("items.thermal_imager")) {
            ItemStack thermalImager = new ItemStack(Material.NETHER_STAR);
            ItemMeta meta = thermalImager.getItemMeta();
            String displayName = itemsConfig.getString("items.thermal_imager.display_name");
            List<String> lore = itemsConfig.getStringList("items.thermal_imager.lore");
            String configId = itemsConfig.getString("items.thermal_imager.id");

            if (meta != null) {
                meta.setDisplayName(displayName);
                meta.setLore(lore);
                thermalImager.setItemMeta(meta);
            }

            items.put("thermal_imager", new CustomItem(
                    "thermal_imager",
                    displayName,
                    lore,
                    thermalImager,
                    configId
            ));
        }

        // Загрузка случайного яйца
        if (itemsConfig.contains("items.random_egg")) {
            ItemStack randomEgg = new ItemStack(Material.PHANTOM_SPAWN_EGG);
            ItemMeta meta = randomEgg.getItemMeta();
            String displayName = itemsConfig.getString("items.random_egg.display_name");
            List<String> lore = itemsConfig.getStringList("items.random_egg.lore");
            String configId = itemsConfig.getString("items.random_egg.id");

            if (meta != null) {
                meta.setDisplayName(displayName);
                meta.setLore(lore);
                meta.addEnchant(Enchantment.DURABILITY, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                randomEgg.setItemMeta(meta);
            }

            items.put("random_egg", new CustomItem(
                    "random_egg",
                    displayName,
                    lore,
                    randomEgg,
                    configId
            ));
        }

        // Загрузка вспышки
        if (itemsConfig.contains("items.blind")) {
            ItemStack blind = new ItemStack(Material.SUGAR);
            ItemMeta meta = blind.getItemMeta();
            String displayName = itemsConfig.getString("items.blind.display_name");
            List<String> lore = itemsConfig.getStringList("items.blind.lore");
            String configId = itemsConfig.getString("items.blind.id");

            if (meta != null) {
                meta.setDisplayName(displayName);
                meta.setLore(lore);
                meta.addEnchant(Enchantment.DURABILITY, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                blind.setItemMeta(meta);
            }

            items.put("blind", new CustomItem(
                    "blind",
                    displayName,
                    lore,
                    blind,
                    configId
            ));
        }

        // Загрузка трапки
        if (itemsConfig.contains("items.trapka")) {
            ItemStack trapka = new ItemStack(Material.SHULKER_SHELL);
            ItemMeta meta = trapka.getItemMeta();
            String displayName = itemsConfig.getString("items.trapka.display_name");
            List<String> lore = itemsConfig.getStringList("items.trapka.lore");
            String configId = itemsConfig.getString("items.trapka.id");

            if (meta != null) {
                meta.setDisplayName(displayName);
                meta.setLore(lore);
                meta.addEnchant(Enchantment.DURABILITY, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                trapka.setItemMeta(meta);
            }

            items.put("trapka", new CustomItem(
                    "trapka",
                    displayName,
                    lore,
                    trapka,
                    configId
            ));
        }

        // Загрузка блокера
        if (itemsConfig.contains("items.blocker")) {
            ItemStack blocker = new ItemStack(Material.NETHERITE_INGOT);
            ItemMeta meta = blocker.getItemMeta();
            String displayName = itemsConfig.getString("items.blocker.display_name");
            List<String> lore = itemsConfig.getStringList("items.blocker.lore");
            String configId = itemsConfig.getString("items.blocker.id");

            if (meta != null) {
                meta.setDisplayName(displayName);
                meta.setLore(lore);
                meta.addEnchant(Enchantment.DURABILITY, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                blocker.setItemMeta(meta);
            }

            items.put("blocker", new CustomItem(
                    "blocker",
                    displayName,
                    lore,
                    blocker,
                    configId
            ));
        }

        // Загрузка тухлого яйца
        if (itemsConfig.contains("items.poison_egg")) {
            ItemStack poisonEgg = new ItemStack(Material.EGG);
            ItemMeta meta = poisonEgg.getItemMeta();
            String displayName = itemsConfig.getString("items.poison_egg.display_name");
            List<String> lore = itemsConfig.getStringList("items.poison_egg.lore");
            String configId = itemsConfig.getString("items.poison_egg.id");

            if (meta != null) {
                meta.setDisplayName(displayName);
                meta.setLore(lore);
                meta.addEnchant(Enchantment.DURABILITY, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                poisonEgg.setItemMeta(meta);
            }

            items.put("poison_egg", new CustomItem(
                    "poison_egg",
                    displayName,
                    lore,
                    poisonEgg,
                    configId
            ));
        }

        // Загрузка переносной наковальни
        if (itemsConfig.contains("items.repair_anvil")) {
            ItemStack repairAnvil = new ItemStack(Material.ANVIL);
            ItemMeta meta = repairAnvil.getItemMeta();
            String displayName = itemsConfig.getString("items.repair_anvil.display_name");
            List<String> lore = itemsConfig.getStringList("items.repair_anvil.lore");
            String configId = itemsConfig.getString("items.repair_anvil.id");

            if (meta != null) {
                meta.setDisplayName(displayName);
                meta.setLore(lore);
                meta.addEnchant(Enchantment.DURABILITY, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                repairAnvil.setItemMeta(meta);
            }

            items.put("repair_anvil", new CustomItem(
                    "repair_anvil",
                    displayName,
                    lore,
                    repairAnvil,
                    configId
            ));
        }

        // Загрузка перемещателя
        if (itemsConfig.contains("items.teleporter")) {
            ItemStack teleporter = new ItemStack(Material.ENDER_EYE);
            ItemMeta meta = teleporter.getItemMeta();
            String displayName = itemsConfig.getString("items.teleporter.display_name");
            List<String> lore = itemsConfig.getStringList("items.teleporter.lore");
            String configId = itemsConfig.getString("items.teleporter.id");

            if (meta != null) {
                meta.setDisplayName(displayName);
                meta.setLore(lore);
                meta.addEnchant(Enchantment.DURABILITY, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                teleporter.setItemMeta(meta);
            }

            items.put("teleporter", new CustomItem(
                    "teleporter",
                    displayName,
                    lore,
                    teleporter,
                    configId
            ));
        }

        // Загрузка кирки хранителя
        if (itemsConfig.contains("items.spawner_pickaxe")) {
            ItemStack spawnerPickaxe = new ItemStack(Material.GOLDEN_PICKAXE);
            ItemMeta meta = spawnerPickaxe.getItemMeta();
            String displayName = itemsConfig.getString("items.spawner_pickaxe.display_name");
            List<String> lore = itemsConfig.getStringList("items.spawner_pickaxe.lore");
            String configId = itemsConfig.getString("items.spawner_pickaxe.id");

            if (meta != null) {
                meta.setDisplayName(displayName);
                meta.setLore(lore);
                meta.addEnchant(Enchantment.DURABILITY, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                spawnerPickaxe.setItemMeta(meta);
            }

            items.put("spawner_pickaxe", new CustomItem(
                    "spawner_pickaxe",
                    displayName,
                    lore,
                    spawnerPickaxe,
                    configId
            ));
        }

        // Загрузка пёрышка ангела
        if (itemsConfig.contains("items.feather_angel")) {
            ItemStack featherAngel = new ItemStack(Material.FEATHER);
            ItemMeta meta = featherAngel.getItemMeta();
            String displayName = itemsConfig.getString("items.feather_angel.display_name");
            List<String> lore = itemsConfig.getStringList("items.feather_angel.lore");
            String configId = itemsConfig.getString("items.feather_angel.id");

            if (meta != null) {
                meta.setDisplayName(displayName);
                meta.setLore(lore);
                meta.addEnchant(Enchantment.DURABILITY, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                featherAngel.setItemMeta(meta);
            }

            items.put("feather_angel", new CustomItem(
                    "feather_angel",
                    displayName,
                    lore,
                    featherAngel,
                    configId
            ));
        }

        // Загрузка огненной ауры
        if (itemsConfig.contains("items.fire_aura")) {
            ItemStack fireAura = new ItemStack(Material.FIRE_CHARGE);
            ItemMeta meta = fireAura.getItemMeta();
            String displayName = itemsConfig.getString("items.fire_aura.display_name");
            List<String> lore = itemsConfig.getStringList("items.fire_aura.lore");
            String configId = itemsConfig.getString("items.fire_aura.id");

            if (meta != null) {
                meta.setDisplayName(displayName);
                meta.setLore(lore);
                meta.addEnchant(Enchantment.DURABILITY, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                fireAura.setItemMeta(meta);
            }

            items.put("fire_aura", new CustomItem(
                    "fire_aura",
                    displayName,
                    lore,
                    fireAura,
                    configId
            ));
        }

        // Загрузка дымопрыга
        if (itemsConfig.contains("items.smoke_jump")) {
            ItemStack smokeJump = new ItemStack(Material.POPPED_CHORUS_FRUIT);
            ItemMeta meta = smokeJump.getItemMeta();
            String displayName = itemsConfig.getString("items.smoke_jump.display_name");
            List<String> lore = new ArrayList<>(itemsConfig.getStringList("items.smoke_jump.lore"));
            String configId = itemsConfig.getString("items.smoke_jump.id");

            if (meta != null) {
                meta.setDisplayName(displayName);
                meta.setLore(lore);
                meta.addEnchant(Enchantment.DURABILITY, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                smokeJump.setItemMeta(meta);
            }

            items.put("smoke_jump", new CustomItem(
                    "smoke_jump",
                    displayName,
                    lore,
                    smokeJump,
                    configId
            ));
        }

        // Загрузка взрывчатки
        if (itemsConfig.contains("items.tnt_explosives")) {
            ItemStack tntExplosives = new ItemStack(Material.TNT);
            ItemMeta meta = tntExplosives.getItemMeta();
            String displayName = itemsConfig.getString("items.tnt_explosives.display_name");
            List<String> lore = itemsConfig.getStringList("items.tnt_explosives.lore");
            String configId = itemsConfig.getString("items.tnt_explosives.id");

            if (meta != null) {
                meta.setDisplayName(displayName);
                meta.setLore(lore);
                meta.addEnchant(Enchantment.DURABILITY, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                tntExplosives.setItemMeta(meta);
            }

            items.put("tnt_explosives", new CustomItem(
                    "tnt_explosives",
                    displayName,
                    lore,
                    tntExplosives,
                    configId
            ));
        }

        // Загрузка Динамита
        if (itemsConfig.contains("items.dwi_tnt")) {
            ItemStack dwiTnt = new ItemStack(Material.TNT);
            ItemMeta meta = dwiTnt.getItemMeta();
            String displayName = itemsConfig.getString("items.dwi_tnt.display_name");
            List<String> lore = itemsConfig.getStringList("items.dwi_tnt.lore");
            String configId = itemsConfig.getString("items.dwi_tnt.id");

            if (meta != null) {
                meta.setDisplayName(displayName);
                meta.setLore(lore);
                dwiTnt.setItemMeta(meta);
            }

            items.put("dwi_tnt", new CustomItem(
                    "dwi_tnt",
                    displayName,
                    lore,
                    dwiTnt,
                    configId
            ));
        }

        // Загрузка Бомбы
        if (itemsConfig.contains("items.tnt_bomb")) {
            ItemStack tntBomb = new ItemStack(Material.TNT);
            ItemMeta meta = tntBomb.getItemMeta();
            String displayName = itemsConfig.getString("items.tnt_bomb.display_name");
            List<String> lore = itemsConfig.getStringList("items.tnt_bomb.lore");
            String configId = itemsConfig.getString("items.tnt_bomb.id");

            if (meta != null) {
                meta.setDisplayName(displayName);
                meta.setLore(lore);
                meta.addEnchant(Enchantment.DURABILITY, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                tntBomb.setItemMeta(meta);
            }

            items.put("tnt_bomb", new CustomItem(
                    "tnt_bomb",
                    displayName,
                    lore,
                    tntBomb,
                    configId
            ));
        }

        // Загрузка ядерки
        if (itemsConfig.contains("items.nuclear_tnt")) {
            ItemStack nucleartnt = new ItemStack(Material.TNT);
            ItemMeta meta = nucleartnt.getItemMeta();
            String displayName = itemsConfig.getString("items.nuclear_tnt.display_name");
            List<String> lore = itemsConfig.getStringList("items.nuclear_tnt.lore");
            String configId = itemsConfig.getString("items.nuclear_tnt.id");

            if (meta != null) {
                meta.setDisplayName(displayName);
                meta.setLore(lore);
                meta.addEnchant(Enchantment.DURABILITY, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                nucleartnt.setItemMeta(meta);
            }

            items.put("nuclear_tnt", new CustomItem(
                    "nuclear_tnt",
                    displayName,
                    lore,
                    nucleartnt,
                    configId
            ));
        }

        // Загрузка гарпуна
        if (itemsConfig.contains("items.harpoon_1")) {
            ItemStack harpoon = new ItemStack(Material.FISHING_ROD);
            ItemMeta meta = harpoon.getItemMeta();
            String displayName = itemsConfig.getString("items.harpoon_1.display_name");
            List<String> lore = new ArrayList<>(itemsConfig.getStringList("items.harpoon_1.lore"));
            String configId = itemsConfig.getString("items.harpoon_1.id");

            if (meta != null) {
                meta.setDisplayName(displayName);
                meta.setLore(lore);
                meta.setUnbreakable(true);
                meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                meta.addEnchant(Enchantment.LUCK, 1, true);
                harpoon.setItemMeta(meta);
            }

            items.put("harpoon_1", new CustomItem(
                    "harpoon_1",
                    displayName,
                    lore,
                    harpoon,
                    configId
            ));
        }

        // Загрузка улучшенного гарпуна
        if (itemsConfig.contains("items.harpoon_2")) {
            ItemStack harpoon = new ItemStack(Material.FISHING_ROD);
            ItemMeta meta = harpoon.getItemMeta();
            String displayName = itemsConfig.getString("items.harpoon_2.display_name");
            List<String> lore = new ArrayList<>(itemsConfig.getStringList("items.harpoon_2.lore"));
            String configId = itemsConfig.getString("items.harpoon_2.id");

            if (meta != null) {
                meta.setDisplayName(displayName);
                meta.setLore(lore);
                meta.setUnbreakable(true);
                meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                meta.addEnchant(Enchantment.LUCK, 1, true);
                harpoon.setItemMeta(meta);
            }

            items.put("harpoon_2", new CustomItem(
                    "harpoon_2",
                    displayName,
                    lore,
                    harpoon,
                    configId
            ));
        }

        // Загрузка гарпуна Скруджа
        if (itemsConfig.contains("items.harpoon_scrooge")) {
            ItemStack harpoon = new ItemStack(Material.FISHING_ROD);
            ItemMeta meta = harpoon.getItemMeta();
            String displayName = itemsConfig.getString("items.harpoon_scrooge.display_name");
            List<String> lore = new ArrayList<>(itemsConfig.getStringList("items.harpoon_scrooge.lore"));
            String configId = itemsConfig.getString("items.harpoon_scrooge.id");

            if (meta != null) {
                meta.setDisplayName(displayName);
                meta.setLore(lore);
                meta.setUnbreakable(true);
                meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                meta.addEnchant(Enchantment.LUCK, 1, true);
                harpoon.setItemMeta(meta);
            }

            items.put("harpoon_scrooge", new CustomItem(
                    "harpoon_scrooge",
                    displayName,
                    lore,
                    harpoon,
                    configId
            ));
        }

        // Загрузка большого шалкера
        if (itemsConfig.contains("items.shulker_36")) {
            ItemStack shulker = new ItemStack(Material.WHITE_SHULKER_BOX);
            ItemMeta meta = shulker.getItemMeta();
            String displayName = itemsConfig.getString("items.shulker_36.display_name");
            List<String> lore = itemsConfig.getStringList("items.shulker_36.lore");
            String configId = itemsConfig.getString("items.shulker_36.id");

            if (meta != null) {
                meta.setDisplayName(displayName);
                meta.setLore(lore);
                meta.addEnchant(Enchantment.DURABILITY, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                shulker.setItemMeta(meta);
            }

            items.put("shulker_36", new CustomItem(
                    "shulker_36",
                    displayName,
                    lore,
                    shulker,
                    configId
            ));
        }

        // Загрузка гиганского шалкера
        if (itemsConfig.contains("items.shulker_45")) {
            ItemStack shulker = new ItemStack(Material.WHITE_SHULKER_BOX);
            ItemMeta meta = shulker.getItemMeta();
            String displayName = itemsConfig.getString("items.shulker_45.display_name");
            List<String> lore = itemsConfig.getStringList("items.shulker_45.lore");
            String configId = itemsConfig.getString("items.shulker_45.id");

            if (meta != null) {
                meta.setDisplayName(displayName);
                meta.setLore(lore);
                meta.addEnchant(Enchantment.DURABILITY, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                shulker.setItemMeta(meta);
            }

            items.put("shulker_45", new CustomItem(
                    "shulker_45",
                    displayName,
                    lore,
                    shulker,
                    configId
            ));
        }

        // Загрузка шалкера скруджа
        if (itemsConfig.contains("items.shulker_54")) {
            ItemStack shulker = new ItemStack(Material.WHITE_SHULKER_BOX);
            ItemMeta meta = shulker.getItemMeta();
            String displayName = itemsConfig.getString("items.shulker_54.display_name");
            List<String> lore = itemsConfig.getStringList("items.shulker_54.lore");
            String configId = itemsConfig.getString("items.shulker_54.id");

            if (meta != null) {
                meta.setDisplayName(displayName);
                meta.setLore(lore);
                meta.addEnchant(Enchantment.DURABILITY, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                shulker.setItemMeta(meta);
            }

            items.put("shulker_54", new CustomItem(
                    "shulker_54",
                    displayName,
                    lore,
                    shulker,
                    configId
            ));
        }

        // Загрузка заточки
        if (itemsConfig.contains("items.weapon_sharpener")) {
            ItemStack sharpener = new ItemStack(Material.PRISMARINE_SHARD);
            ItemMeta meta = sharpener.getItemMeta();
            String displayName = itemsConfig.getString("items.weapon_sharpener.display_name");
            List<String> lore = itemsConfig.getStringList("items.weapon_sharpener.lore");
            String configId = itemsConfig.getString("items.weapon_sharpener.id");

            if (meta != null) {
                meta.setDisplayName(displayName);
                meta.setLore(lore);
                meta.addEnchant(Enchantment.DURABILITY, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                sharpener.setItemMeta(meta);
            }

            items.put("weapon_sharpener", new CustomItem(
                    "weapon_sharpener",
                    displayName,
                    lore,
                    sharpener,
                    configId
            ));
        }

        // Загрузка кирки 9 на 9
        if (itemsConfig.contains("items.destroy_pickaxe")) {
            ItemStack destroypickaxe = new ItemStack(Material.NETHERITE_PICKAXE);
            ItemMeta meta = destroypickaxe.getItemMeta();
            String displayName = itemsConfig.getString("items.destroy_pickaxe.display_name");
            List<String> lore = itemsConfig.getStringList("items.destroy_pickaxe.lore");
            String configId = itemsConfig.getString("items.destroy_pickaxe.id");

            if (meta != null) {
                meta.setDisplayName(displayName);
                meta.setLore(lore);
                meta.addEnchant(Enchantment.DURABILITY, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                meta.setUnbreakable(false);
                destroypickaxe.setItemMeta(meta);
            }

            items.put("destroy_pickaxe", new CustomItem(
                    "destroy_pickaxe",
                    displayName,
                    lore,
                    destroypickaxe,
                    configId
            ));
        }

        //drop_arrow:
        if (itemsConfig.contains("items.drop_arrow")) {
            ItemStack dropArrow = new ItemStack(Material.TIPPED_ARROW);
            ItemMeta meta = dropArrow.getItemMeta();
            String displayName = itemsConfig.getString("items.drop_arrow.display_name");
            List<String> lore = itemsConfig.getStringList("items.drop_arrow.lore");
            String configId = itemsConfig.getString("items.drop_arrow.id");

            if (meta != null && meta instanceof PotionMeta) {
                PotionMeta potionMeta = (PotionMeta) meta;
                potionMeta.setDisplayName(displayName);
                potionMeta.setLore(lore);
                potionMeta.addEnchant(Enchantment.DURABILITY, 1, true);
                potionMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                potionMeta.addItemFlags(ItemFlag.HIDE_POTION_EFFECTS);

                // Устанавливаем цвет
                String colorHex = getItemConfigHexColor("drop_arrow", "arrow_color", "FB8408");
                potionMeta.setColor(ColorConverter.hexToColor(colorHex));

                dropArrow.setItemMeta(potionMeta);
            }

            items.put("drop_arrow", new CustomItem(
                    "drop_arrow",
                    displayName,
                    lore,
                    dropArrow,
                    configId
            ));
        }

        // money_arrow:
        if (itemsConfig.contains("items.money_arrow")) {
            ItemStack moneyArrow = new ItemStack(Material.TIPPED_ARROW);
            ItemMeta meta = moneyArrow.getItemMeta();
            String displayName = itemsConfig.getString("items.money_arrow.display_name");
            List<String> lore = itemsConfig.getStringList("items.money_arrow.lore");
            String configId = itemsConfig.getString("items.money_arrow.id");

            if (meta != null && meta instanceof PotionMeta) {
                PotionMeta potionMeta = (PotionMeta) meta;
                potionMeta.setDisplayName(displayName);
                potionMeta.setLore(lore);
                potionMeta.addEnchant(Enchantment.DURABILITY, 1, true);
                potionMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                potionMeta.addItemFlags(ItemFlag.HIDE_POTION_EFFECTS);

                // Устанавливаем цвет
                String colorHex = getItemConfigHexColor("money_arrow", "arrow_color", "FFE500");
                potionMeta.setColor(ColorConverter.hexToColor(colorHex));

                moneyArrow.setItemMeta(potionMeta);
            }

            items.put("money_arrow", new CustomItem(
                    "money_arrow",
                    displayName,
                    lore,
                    moneyArrow,
                    configId
            ));
        }

        //armor_arrow:
        if (itemsConfig.contains("items.armor_arrow")) {
            ItemStack armorArrow = new ItemStack(Material.TIPPED_ARROW);
            ItemMeta meta = armorArrow.getItemMeta();
            String displayName = itemsConfig.getString("items.armor_arrow.display_name");
            List<String> lore = itemsConfig.getStringList("items.armor_arrow.lore");
            String configId = itemsConfig.getString("items.armor_arrow.id");

            if (meta != null && meta instanceof PotionMeta) {
                PotionMeta potionMeta = (PotionMeta) meta;
                potionMeta.setDisplayName(displayName);
                potionMeta.setLore(lore);
                potionMeta.addEnchant(Enchantment.DURABILITY, 1, true);
                potionMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                potionMeta.addItemFlags(ItemFlag.HIDE_POTION_EFFECTS);

                // Устанавливаем цвет
                String colorHex = getItemConfigHexColor("armor_arrow", "arrow_color", "4A4A4A");
                potionMeta.setColor(ColorConverter.hexToColor(colorHex));

                armorArrow.setItemMeta(potionMeta);
            }

            items.put("armor_arrow", new CustomItem(
                    "armor_arrow",
                    displayName,
                    lore,
                    armorArrow,
                    configId
            ));
        }

        // замерзшая стрела
        if (itemsConfig.contains("items.ice_arrow")) {
            ItemStack iceArrow = new ItemStack(Material.TIPPED_ARROW);
            ItemMeta meta = iceArrow.getItemMeta();
            String displayName = itemsConfig.getString("items.ice_arrow.display_name");
            List<String> lore = itemsConfig.getStringList("items.ice_arrow.lore");
            String configId = itemsConfig.getString("items.ice_arrow.id");

            if (meta != null && meta instanceof PotionMeta) {
                PotionMeta potionMeta = (PotionMeta) meta;
                potionMeta.setDisplayName(displayName);
                potionMeta.setLore(lore);
                potionMeta.addEnchant(Enchantment.DURABILITY, 1, true);
                potionMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                potionMeta.addItemFlags(ItemFlag.HIDE_POTION_EFFECTS);

                // Устанавливаем цвет
                String colorHex = getItemConfigHexColor("ice_arrow", "arrow_color", "00D7FF");
                potionMeta.setColor(ColorConverter.hexToColor(colorHex));

                iceArrow.setItemMeta(potionMeta);
            }

            items.put("ice_arrow", new CustomItem(
                    "ice_arrow",
                    displayName,
                    lore,
                    iceArrow,
                    configId
            ));
        }
    }


    public ItemStack getItem(String id) {
        CustomItem customItem = items.get(id);
        return customItem != null ? customItem.create() : null;
    }

    public int getItemConfig(String itemId, String path, int defaultValue) {
        return itemsConfig.getInt("items." + itemId + "." + path, defaultValue);
    }

    public double getItemConfigDouble(String itemId, String path, double defaultValue) {
        return itemsConfig.getDouble("items." + itemId + "." + path, defaultValue);
    }

    public String getItemConfigHexColor(String itemId, String path, String defaultColor) {
        String color = itemsConfig.getString("items." + itemId + "." + path, defaultColor);
        return color.startsWith("#") ? color : "#" + color;
    }

    public List<String> getBlacklistedRegions(String itemId) {
        return itemBlacklistedRegions.getOrDefault(itemId, Collections.emptyList());
    }

    public List<String> getBlacklistedWorlds(String itemId) {
        return itemBlacklistedWorlds.getOrDefault(itemId, Collections.emptyList());
    }

    public void reloadItems() {
        items.clear();

        File itemsFile = new File(plugin.getDataFolder(), "items.yml");

        itemsConfig = YamlConfiguration.loadConfiguration(itemsFile);

        try {
            Reader reader = new InputStreamReader(new FileInputStream(itemsFile), StandardCharsets.UTF_8);
            itemsConfig.load(reader);
        } catch (Exception e) {
            plugin.getLogger().severe("Ошибка при загрузке items.yml: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        // Загружаем все предметы заново
        loadItems();
    }

}