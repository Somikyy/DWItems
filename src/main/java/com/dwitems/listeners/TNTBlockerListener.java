package com.dwitems.listeners;

import com.dwitems.DWItems;
import com.dwitems.managers.ItemManager;
import com.dwitems.managers.TNTManager;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class TNTBlockerListener implements Listener {
    private final DWItems plugin;
    private final TNTManager tntManager;
    private final ItemManager itemManager;

    public TNTBlockerListener(DWItems plugin) {
        this.plugin = plugin;
        this.tntManager = plugin.getTNTManager();
        this.itemManager = plugin.getItemManager();
    }

    private boolean isCustomTNT(ItemStack item) {
        if (item == null || item.getType() != Material.TNT) return false;

        // Проверяем все возможные типы TNT
        String[] tntTypes = {"dwi_tnt", "tnt_bomb", "nuclear_tnt"};

        for (String tntId : tntTypes) {
            ItemStack customTNT = plugin.getItemManager().getItem(tntId);
            if (customTNT != null && item.isSimilar(customTNT)) {
                return true;
            }
        }

        return false;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTNTCraft(PrepareItemCraftEvent event) {
        ItemStack result = event.getInventory().getResult();
        if (result != null && result.getType() == Material.TNT) {
            event.getInventory().setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTNTPlace(BlockPlaceEvent event) {
        if (event.getBlock().getType() == Material.TNT) {
            ItemStack itemInHand = event.getItemInHand();

            if (isCustomTNT(itemInHand)) {
                String tntId = null;
                String[] tntTypes = {"dwi_tnt", "tnt_bomb", "nuclear_tnt"};

                for (String type : tntTypes) {
                    ItemStack customTNT = plugin.getItemManager().getItem(type);
                    if (customTNT != null && itemInHand.isSimilar(customTNT)) {
                        tntId = type;
                        break;
                    }
                }

                if (tntId != null) {
                    event.setCancelled(false); // Явно разрешаем установку
                    tntManager.addTNTLocation(event.getBlock().getLocation(), tntId);
                    return;
                }
            }

            Player player = event.getPlayer();
            // Проверка блэклистов
            if (!itemManager.isItemAllowedInWorld("tntTypes", player.getWorld().getName())) {
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
                    if (itemManager.hasWhitelistedRegion("tntTypes", region.getId())) {
                        hasWhitelistedRegion = true;
                        break;
                    }
                }

                // Если нет региона из вайтлиста, проверяем блэклист
                if (!hasWhitelistedRegion) {
                    for (ProtectedRegion region : regionSet) {
                        if (region.getId().equals("__global__")) continue;
                        if (!itemManager.isItemAllowedInRegion("tntTypes", region.getId())) {
                            event.setCancelled(true);
                            player.sendMessage("§6[§c✘§6] §cЗдесь этот предмет использовать нельзя!");
                            return;
                        }
                    }
                }
            }

            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTNTIgnite(BlockIgniteEvent event) {
        if (event.getBlock().getType() == Material.TNT) {
            if (!tntManager.isCustomTNTLocation(event.getBlock().getLocation())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() == Material.TNT) {
            Location loc = event.getBlock().getLocation();
            if (tntManager.isCustomTNTLocation(loc)) {
                tntManager.removeTNTLocation(loc);
            }
        }
    }
}