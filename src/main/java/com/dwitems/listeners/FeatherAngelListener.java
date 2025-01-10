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
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;

public class FeatherAngelListener implements Listener {
    private final DWItems plugin;
    private final HashMap<UUID, Long> cooldowns = new HashMap<>();
    private final Set<UUID> noFallDamage = new HashSet<>();
    private final ItemManager itemManager;

    public FeatherAngelListener(DWItems plugin) {
        this.plugin = plugin;
        this.itemManager = plugin.getItemManager();
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();



        if (item == null || item.getType() != Material.FEATHER) {
            return;
        }

        // Проверяем, является ли предмет пёрышком ангела
        ItemStack featherAngel = plugin.getItemManager().getItem("feather_angel");
        if (!item.isSimilar(featherAngel)) {
            return;
        }

        // Проверка блэклистов
        Player player = event.getPlayer();
        if (!itemManager.isItemAllowedInWorld("feather_angel", player.getWorld().getName())) {
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
                if (itemManager.hasWhitelistedRegion("feather_angel", region.getId())) {
                    hasWhitelistedRegion = true;
                    break;
                }
            }

            // Если нет региона из вайтлиста, проверяем блэклист
            if (!hasWhitelistedRegion) {
                for (ProtectedRegion region : regionSet) {
                    if (region.getId().equals("__global__")) continue;
                    if (!itemManager.isItemAllowedInRegion("feather_angel", region.getId())) {
                        event.setCancelled(true);
                        player.sendMessage("§6[§c✘§6] §cЗдесь этот предмет использовать нельзя!");
                        return;
                    }
                }
            }
        }

        event.setCancelled(true);

        // Проверка кулдауна
        int cooldownTime = plugin.getItemManager().getItemConfig("feather_angel", "cooldown", 180);
        if (cooldowns.containsKey(player.getUniqueId())) {
            long timeLeft = cooldowns.get(player.getUniqueId()) - System.currentTimeMillis();
            if (timeLeft > 0) {
                player.sendMessage(plugin.getMessageManager().getMessage("messages.feather_angel.cooldown",
                        "{time}", String.valueOf(timeLeft / 1000)));
                return;
            }
        }

        // Получаем настройки
        int jumpHeight = plugin.getItemManager().getItemConfig("feather_angel", "jump_height", 8);
        int regenDuration = plugin.getItemManager().getItemConfig("feather_angel", "regeneration_duration", 3);
        int regenAmplifier = plugin.getItemManager().getItemConfig("feather_angel", "regeneration_amplifier", 1);

        // Подбрасываем игрока
        Vector velocity = player.getVelocity();
        velocity.setY(jumpHeight * 0.3); // Множитель для более плавного подъема
        player.setVelocity(velocity);

        // Добавляем эффект регенерации
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,
                regenDuration * 20, regenAmplifier));

        // Добавляем игрока в список защищенных от урона падения
        noFallDamage.add(player.getUniqueId());

        // Проигрываем звук
        player.playSound(player.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 1.0f, 1.0f);

        // Устанавливаем кулдаун
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (cooldownTime * 1000));

        // Удаляем предмет
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }

        // Отправляем сообщение
        player.sendMessage(plugin.getMessageManager().getMessage("messages.feather_angel.activated"));
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getEntity();

        // Проверяем, является ли урон уроном от падения
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL &&
                noFallDamage.contains(player.getUniqueId())) {
            event.setCancelled(true);
            noFallDamage.remove(player.getUniqueId());
        }
    }
}