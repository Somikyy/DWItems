package com.dwitems.listeners;

import com.dwitems.DWItems;
import com.dwitems.managers.ItemManager;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class DestroyPickaxeListener implements Listener {
    private final DWItems plugin;
    private final ItemManager itemManager;

    public DestroyPickaxeListener(DWItems plugin) {
        this.plugin = plugin;
        this.itemManager = plugin.getItemManager();
    }

    private boolean isDestroyPickaxe(ItemStack item) {
        if (item == null || item.getType() != Material.NETHERITE_PICKAXE) return false;
        ItemStack destroyPickaxe = plugin.getItemManager().getItem("destroy_pickaxe");
        if (destroyPickaxe == null) return false;

        ItemMeta itemMeta = item.getItemMeta();
        ItemMeta destroyMeta = destroyPickaxe.getItemMeta();

        if (itemMeta == null || destroyMeta == null) return false;

        return itemMeta.hasDisplayName() &&
                destroyMeta.hasDisplayName() &&
                itemMeta.getDisplayName().equals(destroyMeta.getDisplayName()) &&
                itemMeta.hasLore() &&
                destroyMeta.hasLore() &&
                itemMeta.getLore().equals(destroyMeta.getLore());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (!isDestroyPickaxe(item)) return;

        // Проверка блэклистов
        if (!itemManager.isItemAllowedInWorld("destroy_pickaxe", player.getWorld().getName())) {
            event.setCancelled(true);
            player.sendMessage("§6[§c✘§6] §cЗдесь этот предмет использовать нельзя!");
            return;
        }

        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionManager regions = container.get(BukkitAdapter.adapt(player.getWorld()));

        if (regions != null) {
            // Проверяем позицию блока
            BlockVector3 blockLoc = BlockVector3.at(event.getBlock().getLocation().getX(),
                    event.getBlock().getLocation().getY(),
                    event.getBlock().getLocation().getZ());

            ApplicableRegionSet blockRegionSet = regions.getApplicableRegions(blockLoc);
            boolean blockInWhitelistedRegion = false;
            boolean blockInBlacklistedRegion = false;

            // Проверяем регионы блока
            for (ProtectedRegion region : blockRegionSet) {
                if (region.getId().equals("__global__")) continue;

                if (itemManager.hasWhitelistedRegion("destroy_pickaxe", region.getId())) {
                    blockInWhitelistedRegion = true;
                }
                if (!itemManager.isItemAllowedInRegion("destroy_pickaxe", region.getId())) {
                    blockInBlacklistedRegion = true;
                    break;
                }
            }

            // Если блок в блэклист регионе и не в вайтлист регионе - отменяем событие
            if (blockInBlacklistedRegion && !blockInWhitelistedRegion) {
                event.setCancelled(true);
                return;
            }

            // Если нет вайтлист региона и размер больше 1 (не только __global__), проверяем позицию игрока
            if (!blockInWhitelistedRegion && blockRegionSet.size() > 1) {
                BlockVector3 playerLoc = BlockVector3.at(player.getLocation().getX(),
                        player.getLocation().getY(),
                        player.getLocation().getZ());

                ApplicableRegionSet playerRegionSet = regions.getApplicableRegions(playerLoc);
                boolean hasWhitelistedRegion = false;

                // Сначала проверяем есть ли регион из вайтлиста
                for (ProtectedRegion region : playerRegionSet) {
                    if (region.getId().equals("__global__")) continue;
                    if (itemManager.hasWhitelistedRegion("destroy_pickaxe", region.getId())) {
                        hasWhitelistedRegion = true;
                        break;
                    }
                }

                // Если нет региона из вайтлиста, проверяем блэклист
                if (!hasWhitelistedRegion) {
                    for (ProtectedRegion region : playerRegionSet) {
                        if (region.getId().equals("__global__")) continue;
                        if (!itemManager.isItemAllowedInRegion("destroy_pickaxe", region.getId())) {
                            event.setCancelled(true);
                            return;
                        }
                    }
                }
            }
        }

        Block centerBlock = event.getBlock();
        int radius = 4; // Стандартный радиус

        // Если центральный блок не в списке разрешенных - устанавливаем радиус в 0
        if (!canBreak(centerBlock)) {
            radius = 0;
        }

        if (player.getWorld().getName().equalsIgnoreCase("spawn")) {
            if (regions != null) {
                BlockVector3 loc = BlockVector3.at(player.getLocation().getX(),
                        player.getLocation().getY(),
                        player.getLocation().getZ());

                for (ProtectedRegion region : regions.getApplicableRegions(loc)) {
                    if (region.getId().equalsIgnoreCase("automine") || region.getId().equalsIgnoreCase("spawn")) {
                        radius = radius == 0 ? 0 : 1; // Если радиус уже 0, оставляем 0, иначе ставим 1
                        break;
                    }
                }
            }
        }

        // Отменяем оригинальное событие
        event.setCancelled(true);

        // Проверяем прочность
        short durability = item.getDurability();
        if (durability >= item.getType().getMaxDurability() - 10) {
            player.getInventory().setItemInMainHand(null);
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
            return;
        }

        // Если радиус 0, просто ломаем центральный блок
        if (radius == 0) {
            centerBlock.breakNaturally(item);
        } else {
            // Иначе ломаем блоки в радиусе
            for (int x = -radius; x <= radius; x++) {
                for (int y = -radius; y <= radius; y++) {
                    for (int z = -radius; z <= radius; z++) {
                        Block relativeBlock = centerBlock.getRelative(x, y, z);

                        BlockVector3 relativeBlockLoc = BlockVector3.at(
                                relativeBlock.getLocation().getX(),
                                relativeBlock.getLocation().getY(),
                                relativeBlock.getLocation().getZ()
                        );

                        ApplicableRegionSet blockRegionSet = regions.getApplicableRegions(relativeBlockLoc);
                        boolean blockInWhitelistedRegion = false;
                        boolean blockInBlacklistedRegion = false;

                        for (ProtectedRegion region : blockRegionSet) {
                            if (region.getId().equals("__global__")) continue;

                            if (itemManager.hasWhitelistedRegion("destroy_pickaxe", region.getId())) {
                                blockInWhitelistedRegion = true;
                            }
                            if (!itemManager.isItemAllowedInRegion("destroy_pickaxe", region.getId())) {
                                blockInBlacklistedRegion = true;
                                break;
                            }
                        }

                        if (blockInBlacklistedRegion && !blockInWhitelistedRegion) {
                            continue;
                        }

                        if ((blockRegionSet.size() <= 1 || blockInWhitelistedRegion) && canBreak(relativeBlock)) {
                            relativeBlock.breakNaturally(item);
                        }
                    }
                }
            }
        }

        // Увеличиваем прочность
        item.setDurability((short) (durability + 10));
    }

    private boolean canBreak(Block block) {
        // Список блоков, которые МОЖНО ломать (только каменные блоки)
        Material type = block.getType();
        return type == Material.STONE ||
                type == Material.GRANITE ||
                type == Material.SANDSTONE ||
                type == Material.DIORITE ||
                type == Material.ANDESITE ||
                type == Material.SMOOTH_STONE ||
                type == Material.BLACKSTONE ||
                type == Material.BASALT ||
                type == Material.COBBLESTONE ||
                type == Material.MOSSY_COBBLESTONE ||
                type == Material.STONE_BRICKS ||
                type == Material.MOSSY_STONE_BRICKS ||
                type == Material.CRACKED_STONE_BRICKS ||
                type == Material.CHISELED_STONE_BRICKS ||
                type == Material.POLISHED_BLACKSTONE ||
                type == Material.POLISHED_BLACKSTONE_BRICKS ||
                type == Material.CRACKED_POLISHED_BLACKSTONE_BRICKS ||
                type == Material.CHISELED_POLISHED_BLACKSTONE ||
                type == Material.COAL_ORE ||
                type == Material.IRON_ORE ||
                type == Material.GOLD_ORE ||
                type == Material.DIAMOND_ORE ||
                type == Material.EMERALD_ORE ||
                type == Material.LAPIS_ORE ||
                type == Material.REDSTONE_ORE ||
                type == Material.NETHER_GOLD_ORE ||
                type == Material.NETHER_QUARTZ_ORE ||
                type == Material.NETHERRACK ||
                type == Material.OBSIDIAN;
    }

    // Запрещаем зачаровывание через стол зачарования
    @EventHandler(priority = EventPriority.HIGH)
    public void onEnchantItem(EnchantItemEvent event) {
        if (isDestroyPickaxe(event.getItem())) {
            event.setCancelled(true);
        }
    }

    // Запрещаем зачаровывание через наковальню
    @EventHandler(priority = EventPriority.HIGH)
    public void onAnvilUse(InventoryClickEvent event) {
        if (event.getInventory().getType() != InventoryType.ANVIL) return;

        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();

        if (isDestroyPickaxe(cursor) || isDestroyPickaxe(current)) {
            event.setCancelled(true);
        }
    }
}