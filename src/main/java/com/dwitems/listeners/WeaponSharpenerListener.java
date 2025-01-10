package com.dwitems.listeners;

import com.dwitems.DWItems;
import com.dwitems.managers.ItemManager;
import com.dwitems.utils.ColorUtil;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;

import java.util.*;
import java.util.stream.Collectors;

public class WeaponSharpenerListener implements Listener {
    private final DWItems plugin;
    private final Random random = new Random();
    private final ItemManager itemManager;

    public WeaponSharpenerListener(DWItems plugin) {
        this.plugin = plugin;
        this.itemManager = plugin.getItemManager();
    }


    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        if (item == null || !isWeaponSharpener(item)) {
            return;
        }

        // Проверка блэклистов перед выполнением основной логики
        Player player = event.getPlayer();
        if (!itemManager.isItemAllowedInWorld("weapon_sharpener", player.getWorld().getName())) {
            event.setCancelled(true);
            player.sendMessage("§6[§c✘§6] §cЗдесь этот предмет использовать нельзя!");
            return;
        }

        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionManager regions = container.get(BukkitAdapter.adapt(player.getWorld()));

        if (regions != null) {
            BlockVector3 loc = BlockVector3.at(player.getLocation().getX(),
                    player.getLocation().getY(),
                    player.getLocation().getZ());

            ApplicableRegionSet regionSet = regions.getApplicableRegions(loc);
            boolean hasWhitelistedRegion = false;

            // Сначала проверяем есть ли регион из вайтлиста
            for (ProtectedRegion region : regionSet) {
                if (region.getId().equals("__global__")) continue;
                if (itemManager.hasWhitelistedRegion("weapon_sharpener", region.getId())) {
                    hasWhitelistedRegion = true;
                    break;
                }
            }

            // Если нет региона из вайтлиста, проверяем блэклист
            if (!hasWhitelistedRegion) {
                for (ProtectedRegion region : regionSet) {
                    if (region.getId().equals("__global__")) continue;
                    if (!itemManager.isItemAllowedInRegion("weapon_sharpener", region.getId())) {
                        event.setCancelled(true);
                        player.sendMessage("§6[§c✘§6] §cЗдесь этот предмет использовать нельзя!");
                        return;
                    }
                }
            }
        }

        event.setCancelled(true);
        openWeaponMenu(event.getPlayer());
    }

    private ItemStack createGlassPane(Material material) {
        ItemStack glass = new ItemStack(material);
        ItemMeta meta = glass.getItemMeta();
        meta.setDisplayName(ColorUtil.colorize("&6Duck&fWorld.ru"));
        glass.setItemMeta(meta);
        return glass;
    }

    private void openWeaponMenu(Player player) {
        Inventory menu = Bukkit.createInventory(null, 54, ColorUtil.colorize("§e§l🗡 &#FF9100&lВыберите оружие"));

        // Создаем стёкла
        ItemStack yellowGlass = createGlassPane(Material.YELLOW_STAINED_GLASS_PANE);
        ItemStack orangeGlass = createGlassPane(Material.ORANGE_STAINED_GLASS_PANE);
        ItemStack whiteGlass = createGlassPane(Material.WHITE_STAINED_GLASS_PANE);

        // Заполняем края меню
        for (int i = 0; i < menu.getSize(); i++) {
            // Верхняя и нижняя строка
            if (i < 9 || i >= 45) {
                if (i == 0 || i == 8 || i == 45 || i == 53) {
                    menu.setItem(i, orangeGlass);
                } else if (i == 1 || i == 7 || i == 46 || i == 52) {
                    menu.setItem(i, yellowGlass);
                }
            }
            // Боковые стороны
            else if (i % 9 == 0 || i % 9 == 8) {
                if (i % 9 == 0) {
                    menu.setItem(i, yellowGlass);
                } else {
                    menu.setItem(i, yellowGlass);
                }
            }
        }

        // Добавляем белые стёкла
        menu.setItem(2, whiteGlass);
        menu.setItem(3, whiteGlass);
        menu.setItem(4, whiteGlass);
        menu.setItem(5, whiteGlass);
        menu.setItem(6, whiteGlass);
        menu.setItem(18, whiteGlass);
        menu.setItem(26, whiteGlass);
        menu.setItem(27, whiteGlass);
        menu.setItem(35, whiteGlass);
        menu.setItem(47, whiteGlass);
        menu.setItem(48, whiteGlass);
        menu.setItem(50, whiteGlass);
        menu.setItem(51, whiteGlass);


        // Добавляем оружие из инвентаря игрока
        List<ItemStack> weapons = getWeaponsFromInventory(player);
        int slot = 10; // Начинаем с первого слота второго ряда
        for (int i = 0; i < weapons.size() && i < 28; i++) { // Увеличиваем до 28 слотов (4 ряда по 7)
            ItemStack weapon = weapons.get(i);
            ItemStack displayWeapon = weapon.clone();
            ItemMeta meta = displayWeapon.getItemMeta();

            double damage = getWeaponDamage(weapon);
            int damageLevel = (int) damage;

            List<String> lore = new ArrayList<>();
            lore.add("&6➥ &eНажмите&f, чтобы рискнуть");
            lore.add("&#FF7200&l╔");
            lore.add("&#FF8700&l╠ &fУрон оружия: &6" + damage);
            lore.add("&#FF9B00&l╠");
            lore.add("&#FFB000&l╠ &fШанс улучшения: &a" + getSuccessChance(damageLevel) + "%");
            lore.add("&#FFC400&l╠ &fШанс пропажи: &c" + getFailChance(damageLevel) + "%");
            lore.add("&#FFD900&l╚");

            meta.setLore(ColorUtil.colorize(lore));
            displayWeapon.setItemMeta(meta);

            // Пропускаем крайние слоты
            if (slot % 9 == 8) {
                slot += 2;
            }
            menu.setItem(slot++, displayWeapon);
        }

        // Добавляем кнопку закрытия по центру последнего ряда
        ItemStack barrier = new ItemStack(Material.BARRIER);
        ItemMeta barrierMeta = barrier.getItemMeta();
        barrierMeta.setDisplayName(ColorUtil.colorize("&c&lЗакрыть"));
        List<String> barrierLore = new ArrayList<>();
        barrierLore.add(ColorUtil.colorize("&6➥ &eНажмите&f, чтобы закрыть"));
        barrierMeta.setLore(barrierLore);
        barrier.setItemMeta(barrierMeta);
        menu.setItem(49, barrier); // Центр последнего ряда

        player.openInventory(menu);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(ColorUtil.colorize("§e§l🗡 &#FF9100&lВыберите оружие"))) {
            return;
        }

        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();

        if (clicked == null) return;

        if (clicked.getType() == Material.BARRIER) {
            player.closeInventory();
            return;
        }

        if (clicked.getType().name().endsWith("STAINED_GLASS_PANE")) {
            player.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.0f, 1.0f);
            return;
        }

        if (isWeapon(clicked)) {
            handleWeaponUpgrade(player, clicked);
        }
    }

    private boolean isSameWeapon(ItemStack item1, ItemStack item2) {
        if (item1 == null || item2 == null) return false;
        if (item1.getType() != item2.getType()) return false;

        ItemMeta meta1 = item1.getItemMeta();
        ItemMeta meta2 = item2.getItemMeta();
        if (meta1 == null || meta2 == null) return false;

        // Получаем базовые имена (без уровней улучшения)
        String name1 = getBaseItemName(meta1);
        String name2 = getBaseItemName(meta2);

        return name1.equals(name2);
    }

    private String getBaseItemName(ItemMeta meta) {
        if (!meta.hasDisplayName()) {
            return "";
        }
        String displayName = ColorUtil.colorize(meta.getDisplayName());
        if (displayName.contains(" (+")) {
            return displayName.substring(0, displayName.indexOf(" (+")).trim();
        }
        return displayName.trim();
    }

    private void handleWeaponUpgrade(Player player, ItemStack weapon) {
        // Находим оригинальное оружие в инвентаре игрока
        ItemStack originalWeapon = null;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && isSameWeapon(item, weapon)) {
                originalWeapon = item;
                break;
            }
        }

        if (originalWeapon == null) {
            player.sendMessage(ColorUtil.colorize("&cОшибка: оружие не найдено в инвентаре!"));
            player.closeInventory();
            return;
        }

        // Находим заточку в инвентаре игрока
        ItemStack sharpener = null;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && isWeaponSharpener(item)) {
                sharpener = item;
                break;
            }
        }

        if (sharpener == null) {
            player.sendMessage(ColorUtil.colorize("&cОшибка: заточка не найдена в инвентаре!"));
            player.closeInventory();
            return;
        }

        double currentDamage = getWeaponDamage(originalWeapon);
        int damageLevel = (int) currentDamage;

        int failChance = getFailChance(damageLevel);
        int roll = random.nextInt(100);

        // Уменьшаем количество заточек на 1
        if (sharpener.getAmount() > 1) {
            sharpener.setAmount(sharpener.getAmount() - 1);
        } else {
            player.getInventory().remove(sharpener);
        }

        if (roll < failChance) {
            // Неудача - удаляем оружие
            player.getInventory().removeItem(originalWeapon);

            player.sendTitle(
                    ColorUtil.colorize("&#A70303&lП&#A70303&lр&#D30202&lо&#FF0000&lк&#FF2F00&lа&#FF5D00&lч&#FF5D00&lк&#FF5D00&lа"),
                    ColorUtil.colorize("&fВаше оружие пропало :("),
                    10, 70, 20
            );
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_DESTROY, 1.0f, 1.0f);
        } else {
            // Успех
            // Создаем улучшенную копию оригинального оружия
            ItemStack upgradedWeapon = originalWeapon.clone();
            increaseWeaponDamage(upgradedWeapon);

            // Удаляем старое оружие и даем новое
            player.getInventory().removeItem(originalWeapon);
            player.getInventory().addItem(upgradedWeapon);

            player.sendTitle(
                    ColorUtil.colorize("&#03A736&lП&#03A736&lр&#03D31B&lо&#03FF00&lк&#66FF00&lа&#C8FF00&lч&#C8FF00&lк&#C8FF00&lа"),
                    ColorUtil.colorize("&eВы улучшили урон!"),
                    10, 70, 20
            );
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        }

        player.closeInventory();
    }

    private int getFailChance(int damageLevel) {
        return switch (damageLevel) {
            case 1 -> 35;
            case 2 -> 37;
            case 3 -> 39;
            case 4 -> 41;
            case 5 -> 43;
            case 6 -> 45;
            case 7 -> 47;
            case 8 -> 49;
            case 9 -> 51;
            case 10 -> 53;
            case 11 -> 55;
            case 12 -> 60;
            case 13 -> 65;
            case 14 -> 70;
            default -> 75;
        };
    }

    private int getSuccessChance(int damageLevel) {
        return 100 - getFailChance(damageLevel);
    }

    private boolean isWeaponSharpener(ItemStack item) {
        if (item == null || item.getType() != Material.PRISMARINE_SHARD) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return false;
        return meta.getDisplayName().equals(plugin.getItemManager().getItem("weapon_sharpener").getItemMeta().getDisplayName());
    }

    private boolean isWeapon(ItemStack item) {
        if (item == null) return false;
        Material type = item.getType();
        return type.name().endsWith("_SWORD") ||
                type.name().endsWith("_AXE") ||
                type == Material.BOW ||
                type == Material.CROSSBOW;
    }

    private List<ItemStack> getWeaponsFromInventory(Player player) {
        List<ItemStack> weapons = new ArrayList<>();
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && isWeapon(item)) {
                weapons.add(item);
            }
        }
        return weapons;
    }

    private double getWeaponDamage(ItemStack weapon) {
        if (weapon == null) return 0;
        ItemMeta meta = weapon.getItemMeta();
        if (meta == null) return 0;

        // Проверяем атрибуты
        if (!meta.hasAttributeModifiers()) return getBaseWeaponDamage(weapon.getType());

        Collection<AttributeModifier> modifiers = meta.getAttributeModifiers(Attribute.GENERIC_ATTACK_DAMAGE);
        if (modifiers == null || modifiers.isEmpty()) return getBaseWeaponDamage(weapon.getType());

        double totalDamage = 0;
        for (AttributeModifier modifier : modifiers) {
            totalDamage += modifier.getAmount();
        }

        return Math.round(totalDamage * 10.0) / 10.0; // Округляем до 1 знака после запятой
    }

    private double getBaseWeaponDamage(Material type) {
        return switch (type) {
            case WOODEN_SWORD -> 4;
            case STONE_SWORD -> 5;
            case IRON_SWORD -> 6;
            case DIAMOND_SWORD -> 7;
            case NETHERITE_SWORD -> 8;
            case WOODEN_AXE -> 7;
            case STONE_AXE -> 9;
            case IRON_AXE -> 9;
            case DIAMOND_AXE -> 9;
            case NETHERITE_AXE -> 10;
            case BOW, CROSSBOW -> 1;
            default -> 1;
        };
    }

    private void increaseWeaponDamage(ItemStack weapon) {
        ItemMeta meta = weapon.getItemMeta();
        if (meta == null) return;

        // Получаем текущий урон
        double currentDamage = getWeaponDamage(weapon);

        // Парсим имя и уровень
        WeaponNameData nameData = parseWeaponName(meta, weapon);

        // Устанавливаем новое имя
        meta.setDisplayName(nameData.baseName + ColorUtil.colorize(" &6(+" + nameData.upgradeLevel + ")"));

        // Создаем новый модификатор атрибута
        AttributeModifier newModifier = new AttributeModifier(
                UUID.nameUUIDFromBytes(("weapon_upgrade_" + weapon.getType().name()).getBytes()),
                "generic.attackDamage",
                currentDamage + 1,
                AttributeModifier.Operation.ADD_NUMBER
        );

        // Обновляем модификаторы
        updateAttributeModifiers(meta, newModifier);

        weapon.setItemMeta(meta);
    }

    private static class WeaponNameData {
        final String baseName;
        final int upgradeLevel;

        WeaponNameData(String baseName, int upgradeLevel) {
            this.baseName = baseName;
            this.upgradeLevel = upgradeLevel;
        }
    }

    private WeaponNameData parseWeaponName(ItemMeta meta, ItemStack weapon) {
        String baseName;
        int upgradeLevel = 1;

        if (meta.hasDisplayName()) {
            String displayName = meta.getDisplayName();
            String strippedName = ChatColor.stripColor(displayName);

            int upgradeIndex = strippedName.lastIndexOf(" (+");

            if (upgradeIndex != -1) {
                baseName = displayName.substring(0, displayName.length() - (strippedName.length() - upgradeIndex));
                try {
                    String levelStr = strippedName.substring(
                            upgradeIndex + 3,
                            strippedName.indexOf(")", upgradeIndex)
                    );
                    upgradeLevel = Integer.parseInt(levelStr) + 1;
                } catch (NumberFormatException | IndexOutOfBoundsException e) {
                    upgradeLevel = 1;
                }
            } else {
                baseName = displayName;
            }
        } else {
            baseName = ColorUtil.colorize("&f" + formatMaterialName(weapon.getType()));
        }

        return new WeaponNameData(baseName, upgradeLevel);
    }

    private void updateAttributeModifiers(ItemMeta meta, AttributeModifier newModifier) {
        if (meta.hasAttributeModifiers() && meta.getAttributeModifiers(Attribute.GENERIC_ATTACK_DAMAGE) != null) {
            meta.removeAttributeModifier(Attribute.GENERIC_ATTACK_DAMAGE);
        }

        meta.addAttributeModifier(Attribute.GENERIC_ATTACK_DAMAGE, newModifier);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
    }

    private String formatMaterialName(Material material) {
        return Arrays.stream(material.name().toLowerCase().split("_"))
                .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1))
                .collect(Collectors.joining(" "));
    }

}