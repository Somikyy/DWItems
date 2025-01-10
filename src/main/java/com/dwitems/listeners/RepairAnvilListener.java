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
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.UUID;

public class RepairAnvilListener implements Listener {
    private final DWItems plugin;
    private final HashMap<UUID, Long> cooldowns = new HashMap<>();
    private final ItemManager itemManager;

    public RepairAnvilListener(DWItems plugin) {
        this.plugin = plugin;
        this.itemManager = plugin.getItemManager();
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null || item.getType() != Material.ANVIL) {
            return;
        }

        // Проверяем, является ли предмет переносной наковальней
        ItemStack repairAnvil = plugin.getItemManager().getItem("repair_anvil");
        if (!item.isSimilar(repairAnvil)) {
            return;
        }

        // Проверка блэклистов
        if (!itemManager.isItemAllowedInWorld("repair_anvil", player.getWorld().getName())) {
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
                if (itemManager.hasWhitelistedRegion("repair_anvil", region.getId())) {
                    hasWhitelistedRegion = true;
                    break;
                }
            }

            // Если нет региона из вайтлиста, проверяем блэклист
            if (!hasWhitelistedRegion) {
                for (ProtectedRegion region : regionSet) {
                    if (region.getId().equals("__global__")) continue;
                    if (!itemManager.isItemAllowedInRegion("repair_anvil", region.getId())) {
                        event.setCancelled(true);
                        player.sendMessage("§6[§c✘§6] §cЗдесь этот предмет использовать нельзя!");
                        return;
                    }
                }
            }
        }

        event.setCancelled(true);

        // Проверка кулдауна
        int cooldownTime = plugin.getItemManager().getItemConfig("repair_anvil", "cooldown", 180);
        if (cooldowns.containsKey(player.getUniqueId())) {
            long timeLeft = cooldowns.get(player.getUniqueId()) - System.currentTimeMillis();
            if (timeLeft > 0) {
                player.sendMessage(plugin.getMessageManager().getMessage("messages.repair_anvil.cooldown",
                        "{time}", String.valueOf(timeLeft / 1000)));
                return;
            }
        }

        // Чиним все предметы в инвентаре
        int repairedItems = repairAllItems(player);

        if (repairedItems > 0) {
            // Проигрываем звук наковальни
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);

            // Отправляем сообщение
            player.sendMessage(plugin.getMessageManager().getMessage("messages.repair_anvil.success",
                    "{count}", String.valueOf(repairedItems)));

            // Устанавливаем кулдаун
            cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (cooldownTime * 1000));

            // Удаляем наковальню
            if (item.getAmount() > 1) {
                item.setAmount(item.getAmount() - 1);
            } else {
                player.getInventory().setItemInMainHand(null);
            }
        } else {
            player.sendMessage(plugin.getMessageManager().getMessage("messages.repair_anvil.no_items"));
        }
    }

    private int repairAllItems(Player player) {
        int repairedCount = 0;

        // Проверяем основной инвентарь
        for (ItemStack item : player.getInventory().getContents()) {
            if (repairItem(item)) {
                repairedCount++;
            }
        }

        // Проверяем надетую броню
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (repairItem(armor)) {
                repairedCount++;
            }
        }

        // Проверяем предмет в левой руке
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (repairItem(offhand)) {
            repairedCount++;
        }

        return repairedCount;
    }

    private boolean repairItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable)) {
            return false;
        }

        Damageable damageable = (Damageable) meta;
        if (!damageable.hasDamage()) {
            return false;
        }

        damageable.setDamage(0);
        item.setItemMeta(meta);
        return true;
    }
}