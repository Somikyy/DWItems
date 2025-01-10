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
import org.bukkit.entity.Egg;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.UUID;

public class PoisonEggListener implements Listener {
    private final DWItems plugin;
    private final HashMap<UUID, Long> cooldowns = new HashMap<>();
    private final ItemManager itemManager;

    public PoisonEggListener(DWItems plugin) {
        this.plugin = plugin;
        this.itemManager = plugin.getItemManager();
    }

    @EventHandler
    public void onEggThrow(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Egg) || !(event.getEntity().getShooter() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getEntity().getShooter();
        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        // Проверяем, является ли предмет тухлым яйцом
        ItemStack poisonEgg = plugin.getItemManager().getItem("poison_egg");
        if (!itemInHand.isSimilar(poisonEgg)) {
            return;
        }

        // Проверка блэклистов
        if (!itemManager.isItemAllowedInWorld("poison_egg", player.getWorld().getName())) {
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
                if (itemManager.hasWhitelistedRegion("poison_egg", region.getId())) {
                    hasWhitelistedRegion = true;
                    break;
                }
            }

            // Если нет региона из вайтлиста, проверяем блэклист
            if (!hasWhitelistedRegion) {
                for (ProtectedRegion region : regionSet) {
                    if (region.getId().equals("__global__")) continue;
                    if (!itemManager.isItemAllowedInRegion("poison_egg", region.getId())) {
                        event.setCancelled(true);
                        player.sendMessage("§6[§c✘§6] §cЗдесь этот предмет использовать нельзя!");
                        return;
                    }
                }
            }
        }

        // Проверка кулдауна
        int cooldownTime = plugin.getItemManager().getItemConfig("poison_egg", "cooldown", 120);
        if (cooldowns.containsKey(player.getUniqueId())) {
            long timeLeft = cooldowns.get(player.getUniqueId()) - System.currentTimeMillis();
            if (timeLeft > 0) {
                event.setCancelled(true);
                player.sendMessage(plugin.getMessageManager().getMessage("messages.poison_egg.cooldown",
                        "{time}", String.valueOf(timeLeft / 1000)));
                return;
            }
        }

        // Помечаем яйцо как тухлое
        event.getEntity().setMetadata("poison_egg", new FixedMetadataValue(plugin, true));

        // Устанавливаем кулдаун
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (cooldownTime * 1000));

        // Удаляем предмет из руки
        if (itemInHand.getAmount() > 1) {
            itemInHand.setAmount(itemInHand.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }
    }

    @EventHandler
    public void onEggHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Egg) || !event.getEntity().hasMetadata("poison_egg")) {
            return;
        }

        if (event.getHitEntity() instanceof Player) {
            Player target = (Player) event.getHitEntity();

            // Получаем настройки эффектов
            int duration = plugin.getItemManager().getItemConfig("poison_egg", "effect_duration", 5);
            int poisonAmplifier = plugin.getItemManager().getItemConfig("poison_egg", "poison_amplifier", 1);
            int slownessAmplifier = plugin.getItemManager().getItemConfig("poison_egg", "slowness_amplifier", 1);

            // Применяем эффекты
            target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, duration * 20, poisonAmplifier));
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, duration * 20, slownessAmplifier));

            // Отправляем сообщение
            if (event.getEntity().getShooter() instanceof Player) {
                Player shooter = (Player) event.getEntity().getShooter();
                shooter.sendMessage(plugin.getMessageManager().getMessage("messages.poison_egg.hit",
                        "{player}", target.getName()));
            }
        }
    }
}