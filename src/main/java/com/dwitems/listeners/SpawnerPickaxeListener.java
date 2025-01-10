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
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;

public class SpawnerPickaxeListener implements Listener {
    private final DWItems plugin;
    private final ItemManager itemManager;

    public SpawnerPickaxeListener(DWItems plugin) {
        this.plugin = plugin;
        this.itemManager = plugin.getItemManager();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();

        // Проверяем, является ли блок спавнером
        if (block.getType() != Material.SPAWNER) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        // Проверка блэклистов
        if (!itemManager.isItemAllowedInWorld("spawner_pickaxe", player.getWorld().getName())) {
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
                if (itemManager.hasWhitelistedRegion("spawner_pickaxe", region.getId())) {
                    hasWhitelistedRegion = true;
                    break;
                }
            }

            // Если нет региона из вайтлиста, проверяем блэклист
            if (!hasWhitelistedRegion) {
                for (ProtectedRegion region : regionSet) {
                    if (region.getId().equals("__global__")) continue;
                    if (!itemManager.isItemAllowedInRegion("spawner_pickaxe", region.getId())) {
                        event.setCancelled(true);
                        player.sendMessage("§6[§c✘§6] §cЗдесь этот предмет использовать нельзя!");
                        return;
                    }
                }
            }
        }

        // Проверяем, является ли предмет киркой хранителя
        ItemStack spawnerPickaxe = plugin.getItemManager().getItem("spawner_pickaxe");
        if (item.isSimilar(spawnerPickaxe)) {
            event.setCancelled(true);

            // Получаем тип спавнера
            CreatureSpawner spawner = (CreatureSpawner) block.getState();
            EntityType entityType = spawner.getSpawnedType();

            // Создаем спавнер с сохраненным типом
            ItemStack spawnerItem = new ItemStack(Material.SPAWNER);
            BlockStateMeta meta = (BlockStateMeta) spawnerItem.getItemMeta();
            if (meta != null) {
                CreatureSpawner spawnerState = (CreatureSpawner) meta.getBlockState();
                spawnerState.setSpawnedType(entityType);
                meta.setBlockState(spawnerState);
                spawnerItem.setItemMeta(meta);
            }

            // Создаем яйцо призыва
            ItemStack spawnEgg = new ItemStack(Material.valueOf(entityType.name() + "_SPAWN_EGG"));

            // Удаляем спавнер
            block.setType(Material.AIR);

            // Выдаем предметы
            block.getWorld().dropItemNaturally(block.getLocation(), spawnerItem);
            block.getWorld().dropItemNaturally(block.getLocation(), spawnEgg);


            // Удаляем кирку
            if (item.getAmount() > 1) {
                item.setAmount(item.getAmount() - 1);
            } else {
                player.getInventory().setItemInMainHand(null);
            }
        } else {
            // Если ломают не киркой хранителя, просто ломаем спавнер без дропа
            event.setExpToDrop(0);
            // Отменяем стандартный дроп
            event.setDropItems(false);
        }
    }
}