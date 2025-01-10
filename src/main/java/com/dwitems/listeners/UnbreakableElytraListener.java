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
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class UnbreakableElytraListener implements Listener {
    private final DWItems plugin;

    public UnbreakableElytraListener(DWItems plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onAnvilPrepare(PrepareAnvilEvent event) {
        ItemStack firstItem = event.getInventory().getItem(0);
        if (firstItem == null) return;

        // Получаем образец неразрушимых элитр для сравнения
        ItemStack unbreakableElytra = plugin.getItemManager().getItem("unbreakable_elytra");
        if (unbreakableElytra == null) return;

        // Проверяем, являются ли элитры в наковальне нашими неразрушимыми элитрами
        if (isSimilarIgnoreAmount(firstItem, unbreakableElytra)) {
            // Отменяем результат крафта
            event.setResult(null);
        }
    }

    @EventHandler
    public void onPlayerGlide(EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();
        ItemStack chestplate = player.getInventory().getChestplate();
        if (chestplate == null || chestplate.getType().isAir()) return;

        ItemStack unbreakableElytra = plugin.getItemManager().getItem("unbreakable_elytra");
        if (unbreakableElytra == null) return;

        if (isSimilarIgnoreAmount(chestplate, unbreakableElytra)) {
            ItemManager itemManager = plugin.getItemManager();
            String worldName = player.getWorld().getName();

            // Проверка блэклиста миров
            if (!itemManager.isItemAllowedInWorld("unbreakable_elytra", player.getWorld().getName())) {
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
                    if (itemManager.hasWhitelistedRegion("unbreakable_elytra", region.getId())) {
                        hasWhitelistedRegion = true;
                        break;
                    }
                }

                // Если нет региона из вайтлиста, проверяем блэклист
                if (!hasWhitelistedRegion) {
                    for (ProtectedRegion region : regionSet) {
                        if (region.getId().equals("__global__")) continue;
                        if (!itemManager.isItemAllowedInRegion("unbreakable_elytra", region.getId())) {
                            event.setCancelled(true);
                            player.sendMessage("§6[§c✘§6] §cЗдесь этот предмет использовать нельзя!");
                            return;
                        }
                    }
                }
            }
        }
    }

    private boolean isSimilarIgnoreAmount(ItemStack item1, ItemStack item2) {
        if (item1.getType() != item2.getType()) return false;

        ItemMeta meta1 = item1.getItemMeta();
        ItemMeta meta2 = item2.getItemMeta();

        if (meta1 == null || meta2 == null) return false;

        return meta1.getDisplayName().equals(meta2.getDisplayName()) &&
                meta1.getLore().equals(meta2.getLore()) &&
                meta1.isUnbreakable() == meta2.isUnbreakable() &&
                meta1.hasEnchants() == meta2.hasEnchants();
    }
}