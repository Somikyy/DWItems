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
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class TeleporterListener implements Listener {
    private final DWItems plugin;
    private final HashMap<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, UUID> lastAttacker = new HashMap<>();
    private final Map<UUID, Long> lastCombatTime = new HashMap<>();
    private final ItemManager itemManager;

    public TeleporterListener(DWItems plugin) {
        this.plugin = plugin;
        this.itemManager = plugin.getItemManager();
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player) || !(event.getDamager() instanceof Player)) {
            return;
        }

        Player victim = (Player) event.getEntity();
        Player attacker = (Player) event.getDamager();

        // Обновляем информацию о последнем атакующем
        lastAttacker.put(victim.getUniqueId(), attacker.getUniqueId());
        lastAttacker.put(attacker.getUniqueId(), victim.getUniqueId());

        // Обновляем время последнего боя
        long currentTime = System.currentTimeMillis();
        lastCombatTime.put(victim.getUniqueId(), currentTime);
        lastCombatTime.put(attacker.getUniqueId(), currentTime);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null || item.getType() != Material.ENDER_EYE) {
            return;
        }

        // Проверяем, является ли предмет перемещателем
        ItemStack teleporter = plugin.getItemManager().getItem("teleporter");
        if (!item.isSimilar(teleporter)) {
            return;
        }

        // Проверка блэклистов
        if (!itemManager.isItemAllowedInWorld("teleporter", player.getWorld().getName())) {
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
                if (itemManager.hasWhitelistedRegion("teleporter", region.getId())) {
                    hasWhitelistedRegion = true;
                    break;
                }
            }

            // Если нет региона из вайтлиста, проверяем блэклист
            if (!hasWhitelistedRegion) {
                for (ProtectedRegion region : regionSet) {
                    if (region.getId().equals("__global__")) continue;
                    if (!itemManager.isItemAllowedInRegion("teleporter", region.getId())) {
                        event.setCancelled(true);
                        player.sendMessage("§6[§c✘§6] §cЗдесь этот предмет использовать нельзя!");
                        return;
                    }
                }
            }
        }

        event.setCancelled(true);

        // Проверка кулдауна
        int cooldownTime = plugin.getItemManager().getItemConfig("teleporter", "cooldown", 180);
        if (cooldowns.containsKey(player.getUniqueId())) {
            long timeLeft = cooldowns.get(player.getUniqueId()) - System.currentTimeMillis();
            if (timeLeft > 0) {
                player.sendMessage(plugin.getMessageManager().getMessage("messages.teleporter.cooldown",
                        "{time}", String.valueOf(timeLeft / 1000)));
                return;
            }
        }

        // Проверяем, находится ли игрок в PvP режиме
        if (!isInCombat(player)) {
            player.sendMessage(plugin.getMessageManager().getMessage("messages.teleporter.no_combat"));
            return;
        }

        // Получаем последнего противника
        UUID lastAttackerUUID = lastAttacker.get(player.getUniqueId());
        if (lastAttackerUUID == null) {
            player.sendMessage(plugin.getMessageManager().getMessage("messages.teleporter.no_target"));
            return;
        }

        Player target = plugin.getServer().getPlayer(lastAttackerUUID);
        if (target == null || !target.isOnline() || !isInCombat(target)) {
            player.sendMessage(plugin.getMessageManager().getMessage("messages.teleporter.invalid_target"));
            return;
        }

        // Проверяем дистанцию
        int maxDistance = plugin.getItemManager().getItemConfig("teleporter", "max_distance", 50);
        if (player.getLocation().distance(target.getLocation()) > maxDistance) {
            player.sendMessage(plugin.getMessageManager().getMessage("messages.teleporter.too_far"));
            return;
        }

        // Меняем игроков местами
        Location playerLoc = player.getLocation();
        Location targetLoc = target.getLocation();

        player.teleport(targetLoc);
        target.teleport(playerLoc);

        // Проигрываем звук
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
        target.playSound(target.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);

        // Отправляем сообщения
        player.sendMessage(plugin.getMessageManager().getMessage("messages.teleporter.success",
                "{player}", target.getName()));
        target.sendMessage(plugin.getMessageManager().getMessage("messages.teleporter.swapped",
                "{player}", player.getName()));

        // Устанавливаем кулдаун
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (cooldownTime * 1000));

        // Удаляем предмет
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }
    }

    private boolean isInCombat(Player player) {
        Long lastCombat = lastCombatTime.get(player.getUniqueId());
        if (lastCombat == null) return false;

        // Получаем время PvP режима из конфигурации (в секундах)
        int combatTime = plugin.getItemManager().getItemConfig("teleporter", "combat_time", 30);

        // Переводим в миллисекунды для сравнения
        return System.currentTimeMillis() - lastCombat < (combatTime * 1000L);
    }
}