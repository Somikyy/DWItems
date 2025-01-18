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
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.UUID;

public class BlindListener implements Listener {
    private final DWItems plugin;
    private final HashMap<UUID, Long> cooldowns = new HashMap<>();
    private final ItemManager itemManager;

    public BlindListener(DWItems plugin) {
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

        if (item == null || item.getType() != Material.SUGAR) {
            return;
        }

        // Проверяем, является ли предмет вспышкой
        ItemStack blind = plugin.getItemManager().getItem("blind");
        if (!item.isSimilar(blind)) {
            return;
        }

        // Проверка блэклистов
        if (!itemManager.isItemAllowedInWorld("blind", player.getWorld().getName())) {
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
                if (itemManager.hasWhitelistedRegion("blind", region.getId())) {
                    hasWhitelistedRegion = true;
                    break;
                }
            }

            // Если нет региона из вайтлиста, проверяем блэклист
            if (!hasWhitelistedRegion) {
                for (ProtectedRegion region : regionSet) {
                    if (region.getId().equals("__global__")) continue;
                    if (!itemManager.isItemAllowedInRegion("blind", region.getId())) {
                        event.setCancelled(true);
                        player.sendMessage("§6[§c✘§6] §cЗдесь этот предмет использовать нельзя!");
                        return;
                    }
                }
            }
        }

        event.setCancelled(true);

        int cooldownTime = plugin.getItemManager().getItemConfig("blind", "cooldown", 180);
        // Проверка кулдауна
        if (!player.hasPermission("dwitems.nocooldown")) {
            if (cooldowns.containsKey(player.getUniqueId())) {
                long timeLeft = cooldowns.get(player.getUniqueId()) - System.currentTimeMillis();
                if (timeLeft > 0) {
                    player.sendMessage(plugin.getMessageManager().getMessage("messages.blind.cooldown",
                            "{time}", String.valueOf(timeLeft / 1000)));
                    return;
                }
            }
        }

        // Получаем настройки из конфига
        int radius = plugin.getItemManager().getItemConfig("blind", "radius", 10);
        int duration = plugin.getItemManager().getItemConfig("blind", "effect_duration", 15);
        int slownessAmplifier = plugin.getItemManager().getItemConfig("blind", "slowness_amplifier", 1);

        // Применяем эффекты к ближайшим игрокам
        player.getNearbyEntities(radius, radius, radius).stream()
                .filter(entity -> entity instanceof Player)
                .map(entity -> (Player) entity)
                .forEach(target -> {
                    // Эффект слепоты
                    target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, duration * 20, 0));
                    // Эффект замедления
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, duration * 20, slownessAmplifier));
                });

        // Устанавливаем кулдаун
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (cooldownTime * 1000));

        // Удаляем предмет
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().removeItem(item);
        }

        player.sendMessage(plugin.getMessageManager().getMessage("messages.blind.activated"));
    }
}