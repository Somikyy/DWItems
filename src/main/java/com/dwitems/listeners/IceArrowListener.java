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
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class IceArrowListener implements Listener {
    private final DWItems plugin;
    private final Map<String, Map<UUID, Long>> cooldowns = new HashMap<>();
    private final ItemManager itemManager;

    public IceArrowListener(DWItems plugin) {
        this.plugin = plugin;
        this.itemManager = plugin.getItemManager();
    }

    @EventHandler
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player) || !(event.getProjectile() instanceof Arrow)) {
            return;
        }

        Player player = (Player) event.getEntity();
        ItemStack arrow = event.getConsumable();

        if (arrow == null || !isIceArrow(arrow)) {
            return;
        }

        if (arrow == null || !isIceArrow(arrow)) {
            arrow = player.getInventory().getItemInOffHand();
            if (!isIceArrow(arrow)) {
                for (ItemStack item : player.getInventory().getContents()) {
                    if (item != null && isIceArrow(item)) {
                        arrow = item;
                        break;
                    }
                }
            }
        }

        if (arrow == null || !isIceArrow(arrow)) {
            return;
        }

        // Проверка блэклистов
        if (!itemManager.isItemAllowedInWorld("ice_arrow", player.getWorld().getName())) {
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
                if (itemManager.hasWhitelistedRegion("ice_arrow", region.getId())) {
                    hasWhitelistedRegion = true;
                    break;
                }
            }

            // Если нет региона из вайтлиста, проверяем блэклист
            if (!hasWhitelistedRegion) {
                for (ProtectedRegion region : regionSet) {
                    if (region.getId().equals("__global__")) continue;
                    if (!itemManager.isItemAllowedInRegion("ice_arrow", region.getId())) {
                        event.setCancelled(true);
                        player.sendMessage("§6[§c✘§6] §cЗдесь этот предмет использовать нельзя!");
                        return;
                    }
                }
            }
        }

        if (isOnCooldown(player)) {
            event.setCancelled(true);
            Map<UUID, Long> arrowCooldowns = cooldowns.computeIfAbsent("ice_arrow", k -> new HashMap<>());
            long timeLeft = (arrowCooldowns.get(player.getUniqueId()) - System.currentTimeMillis()) / 1000;
            player.sendMessage(plugin.getMessageManager().getMessage("messages.ice_arrow.cooldown",
                    "{time}", String.valueOf(timeLeft)));
            return;
        }

        Arrow projectile = (Arrow) event.getProjectile();
        projectile.setMetadata("ice_arrow", new FixedMetadataValue(plugin, true));
        setCooldown(player);
    }

    private boolean isIceArrow(ItemStack item) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) {
            return false;
        }
        ItemStack iceArrow = plugin.getItemManager().getItem("ice_arrow");
        return iceArrow != null && item.isSimilar(iceArrow);
    }

    @EventHandler
    public void onHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Arrow) || !(event.getHitEntity() instanceof Player)) {
            return;
        }

        Arrow arrow = (Arrow) event.getEntity();
        if (!arrow.hasMetadata("ice_arrow")) {
            return;
        }

        Player target = (Player) event.getHitEntity();
        Player shooter = (Player) arrow.getShooter();

        if (shooter == null) {
            return;
        }

        int slownessDuration = plugin.getItemManager().getItemConfig("ice_arrow", "slowness_duration", 2) * 20;
        int slownessAmplifier = plugin.getItemManager().getItemConfig("ice_arrow", "slowness_amplifier", 49);
        int weaknessDuration = plugin.getItemManager().getItemConfig("ice_arrow", "weakness_duration", 3) * 20;
        int weaknessAmplifier = plugin.getItemManager().getItemConfig("ice_arrow", "weakness_amplifier", 0);

        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, slownessDuration, slownessAmplifier));
        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, weaknessDuration, weaknessAmplifier));

    }

    private boolean isOnCooldown(Player player) {
        Map<UUID, Long> arrowCooldowns = cooldowns.computeIfAbsent("ice_arrow", k -> new HashMap<>());
        if (!arrowCooldowns.containsKey(player.getUniqueId())) {
            return false;
        }
        return arrowCooldowns.get(player.getUniqueId()) > System.currentTimeMillis();
    }

    private void setCooldown(Player player) {
        Map<UUID, Long> arrowCooldowns = cooldowns.computeIfAbsent("ice_arrow", k -> new HashMap<>());
        long cooldown = plugin.getItemManager().getItemConfig("ice_arrow", "cooldown", 15);
        arrowCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (cooldown * 1000));
    }

    private void cleanupOldCooldowns() {
        long currentTime = System.currentTimeMillis();
        cooldowns.get("ice_arrow").entrySet().removeIf(entry -> entry.getValue() < currentTime);
    }
}