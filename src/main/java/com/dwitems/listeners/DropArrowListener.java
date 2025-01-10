package com.dwitems.listeners;

import com.dwitems.DWItems;
import com.dwitems.managers.ItemManager;
import com.dwitems.utils.ColorConverter;
import com.dwitems.utils.ColorUtil;
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
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DropArrowListener implements Listener {
    private final DWItems plugin;
    private final Map<String, Map<UUID, Long>> cooldowns = new HashMap<>();
    private final Map<UUID, Arrow> dropArrows = new HashMap<>();
    private final ItemManager itemManager;

    public DropArrowListener(DWItems plugin) {
        this.plugin = plugin;
        this.itemManager = plugin.getItemManager();
    }

    @EventHandler
    public void onBowShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player) || !(event.getProjectile() instanceof Arrow arrow)) {
            return;
        }

        ItemStack arrowItem = event.getConsumable();



        if (!isDropArrow(arrowItem)) {
            return;
        }

        // Проверка блэклистов
        if (!itemManager.isItemAllowedInWorld("drop_arrow", player.getWorld().getName())) {
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
                if (itemManager.hasWhitelistedRegion("drop_arrow", region.getId())) {
                    hasWhitelistedRegion = true;
                    break;
                }
            }

            // Если нет региона из вайтлиста, проверяем блэклист
            if (!hasWhitelistedRegion) {
                for (ProtectedRegion region : regionSet) {
                    if (region.getId().equals("__global__")) continue;
                    if (!itemManager.isItemAllowedInRegion("drop_arrow", region.getId())) {
                        event.setCancelled(true);
                        player.sendMessage("§6[§c✘§6] §cЗдесь этот предмет использовать нельзя!");
                        return;
                    }
                }
            }
        }

        // Проверка кулдауна
        if (isOnCooldown(player)) {
            event.setCancelled(true);
            long timeLeft = getCooldownTimeLeft(player);
            player.sendMessage(plugin.getMessageManager().getMessage("messages.drop_arrow.cooldown",
                    "{time}", String.valueOf(timeLeft)));
            return;
        }

        arrow.setGlowing(true);

        String colorHex = plugin.getItemManager().getItemConfigHexColor("drop_arrow", "arrow_color", "FB8408");
        arrow.setColor(ColorConverter.hexToColor(colorHex));

        dropArrows.put(arrow.getUniqueId(), arrow);
        setCooldown(player);
    }

    private boolean isOnCooldown(Player player) {
        Map<UUID, Long> arrowCooldowns = cooldowns.computeIfAbsent("drop_arrow", k -> new HashMap<>());
        return arrowCooldowns.containsKey(player.getUniqueId()) &&
                arrowCooldowns.get(player.getUniqueId()) > System.currentTimeMillis();
    }

    private long getCooldownTimeLeft(Player player) {
        Map<UUID, Long> arrowCooldowns = cooldowns.computeIfAbsent("drop_arrow", k -> new HashMap<>());
        return (arrowCooldowns.get(player.getUniqueId()) - System.currentTimeMillis()) / 1000;
    }

    private void setCooldown(Player player) {
        Map<UUID, Long> arrowCooldowns = cooldowns.computeIfAbsent("drop_arrow", k -> new HashMap<>());
        long cooldownTime = plugin.getItemManager().getItemConfig("drop_arrow", "cooldown", 90);
        arrowCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (cooldownTime * 1000));
    }

    @EventHandler
    public void onArrowHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Arrow arrow) || !(event.getHitEntity() instanceof Player target)) {
            return;
        }

        if (!dropArrows.containsKey(arrow.getUniqueId())) {
            return;
        }

        ItemStack mainHandItem = target.getInventory().getItemInMainHand();

        if (mainHandItem.getType().isAir()) {
            return;
        }

        double throwMultiplier = plugin.getItemManager().getItemConfigDouble("drop_arrow", "throw_multiplier", 0.5);
        org.bukkit.util.Vector direction = target.getLocation().getDirection();

        double randomX = (Math.random() - 0.5) * 0.5;
        double randomY = Math.random() * 0.5;
        double randomZ = (Math.random() - 0.5) * 0.5;

        direction.add(new org.bukkit.util.Vector(randomX, randomY, randomZ))
                .normalize()
                .multiply(throwMultiplier);

        target.getWorld().dropItemNaturally(target.getLocation().add(0, 1, 0), mainHandItem)
                .setVelocity(direction);

        target.getInventory().setItemInMainHand(null);

        dropArrows.remove(arrow.getUniqueId());
        arrow.remove();
    }

    private ItemStack getArrowFromInventory(Player player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();

        if (isDropArrow(mainHand)) {
            return mainHand;
        } else if (isDropArrow(offHand)) {
            return offHand;
        }

        for (ItemStack item : player.getInventory().getContents()) {
            if (isDropArrow(item)) {
                return item;
            }
        }
        return null;
    }

    private boolean isDropArrow(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return false;
        }

        String displayName = ColorUtil.stripColor(meta.getDisplayName());
        String configName = ColorUtil.stripColor(plugin.getItemManager()
                .getItem("drop_arrow").getItemMeta().getDisplayName());

        return displayName.equals(configName);
    }
}