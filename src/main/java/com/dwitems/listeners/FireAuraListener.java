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
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.UUID;

public class FireAuraListener implements Listener {
    private final DWItems plugin;
    private final HashMap<UUID, Long> cooldowns = new HashMap<>();
    private final ItemManager itemManager;

    public FireAuraListener(DWItems plugin) {
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

        if (item == null || item.getType() != Material.FIRE_CHARGE) {
            return;
        }

        // Проверяем, является ли предмет огненной аурой
        ItemStack fireAura = plugin.getItemManager().getItem("fire_aura");
        if (!item.isSimilar(fireAura)) {
            return;
        }

        // Проверка блэклистов
        if (!itemManager.isItemAllowedInWorld("fire_aura", player.getWorld().getName())) {
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
                if (itemManager.hasWhitelistedRegion("fire_aura", region.getId())) {
                    hasWhitelistedRegion = true;
                    break;
                }
            }

            // Если нет региона из вайтлиста, проверяем блэклист
            if (!hasWhitelistedRegion) {
                for (ProtectedRegion region : regionSet) {
                    if (region.getId().equals("__global__")) continue;
                    if (!itemManager.isItemAllowedInRegion("fire_aura", region.getId())) {
                        event.setCancelled(true);
                        player.sendMessage("§6[§c✘§6] §cЗдесь этот предмет использовать нельзя!");
                        return;
                    }
                }
            }
        }

        event.setCancelled(true);

        // Проверка кулдауна
        int cooldownTime = plugin.getItemManager().getItemConfig("fire_aura", "cooldown", 180);
        if (!player.hasPermission("dwitems.nocooldown")) {
            if (cooldowns.containsKey(player.getUniqueId())) {
                long timeLeft = cooldowns.get(player.getUniqueId()) - System.currentTimeMillis();
                if (timeLeft > 0) {
                    player.sendMessage(plugin.getMessageManager().getMessage("messages.fire_aura.cooldown",
                            "{time}", String.valueOf(timeLeft / 1000)));
                    return;
                }
            }
        }

        // Получаем настройки
        int radius = plugin.getItemManager().getItemConfig("fire_aura", "radius", 10);
        int fireDuration = plugin.getItemManager().getItemConfig("fire_aura", "fire_duration", 5);

        // Создаем эффект огненной ауры
        Location playerLoc = player.getLocation();
        playerLoc.getWorld().spawnParticle(Particle.FLAME, playerLoc, 100, radius/2.0, 0.5, radius/2.0, 0.1);
        playerLoc.getWorld().spawnParticle(Particle.LAVA, playerLoc, 50, radius/2.0, 0.5, radius/2.0, 0.1);

        // Проигрываем звук
        player.playSound(playerLoc, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.5f);
        player.playSound(playerLoc, Sound.BLOCK_FIRE_AMBIENT, 1.0f, 1.0f);

        // Поджигаем ближайших игроков
        int affectedPlayers = 0;
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof Player && entity != player) {
                Player target = (Player) entity;
                target.setFireTicks(fireDuration * 20); // переводим секунды в тики
                affectedPlayers++;

                // Отправляем сообщение подожженному игроку
                target.sendMessage(plugin.getMessageManager().getMessage("messages.fire_aura.burned",
                        "{player}", player.getName()));
            }
        }

        // Отправляем сообщение использовавшему
        player.sendMessage(plugin.getMessageManager().getMessage("messages.fire_aura.success",
                "{count}", String.valueOf(affectedPlayers)));

        // Устанавливаем кулдаун
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (cooldownTime * 1000));

        // Удаляем предмет
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }
    }
}