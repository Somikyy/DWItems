package com.dwitems.listeners;

import com.dwitems.DWItems;
import com.dwitems.managers.ItemManager;
import com.dwitems.utils.ColorUtil;
import com.dwitems.utils.ColorConverter;
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
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.Damageable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ArmorArrowListener implements Listener {
    private final DWItems plugin;
    private final Map<UUID, Long> armorArrowCooldowns = new HashMap<>();
    private final Map<UUID, Arrow> armorArrows = new HashMap<>();
    private final Map<UUID, UUID> arrowShooters = new HashMap<>();
    private final ItemManager itemManager;

    public ArmorArrowListener(DWItems plugin) {
        this.plugin = plugin;
        this.itemManager = plugin.getItemManager();
    }

    @EventHandler
    public void onBowShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player) || !(event.getProjectile() instanceof Arrow)) {
            return;
        }

        Player player = (Player) event.getEntity();
        ItemStack arrowItem = event.getConsumable();

        if (arrowItem == null || !isArmorArrow(arrowItem)) {
            return;
        }

        // Проверка блэклиста миров
        if (!itemManager.isItemAllowedInWorld("armor_arrow", player.getWorld().getName())) {
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
                if (itemManager.hasWhitelistedRegion("armor_arrow", region.getId())) {
                    hasWhitelistedRegion = true;
                    break;
                }
            }

            // Если нет региона из вайтлиста, проверяем блэклист
            if (!hasWhitelistedRegion) {
                for (ProtectedRegion region : regionSet) {
                    if (region.getId().equals("__global__")) continue;
                    if (!itemManager.isItemAllowedInRegion("armor_arrow", region.getId())) {
                        event.setCancelled(true);
                        player.sendMessage("§6[§c✘§6] §cЗдесь этот предмет использовать нельзя!");
                        return;
                    }
                }
            }
        }

        if (!player.hasPermission("dwitems.nocooldown")) {
            if (armorArrowCooldowns.containsKey(player.getUniqueId())) {
                long cooldown = plugin.getItemManager().getItemConfig("armor_arrow", "cooldown", 60);
                long timeLeft = ((armorArrowCooldowns.get(player.getUniqueId()) / 1000) + cooldown)
                        - (System.currentTimeMillis() / 1000);
                if (timeLeft > 0) {
                    event.setCancelled(true);
                    player.sendMessage(plugin.getMessageManager().getMessage("messages.armor_arrow.cooldown",
                            "{time}", String.valueOf(timeLeft)));
                    return;
                }
            }
        }

        Arrow arrow = (Arrow) event.getProjectile();
        arrow.setGlowing(true);

        String colorHex = plugin.getItemManager().getItemConfigHexColor("armor_arrow", "arrow_color", "4A4A4A");
        arrow.setColor(ColorConverter.hexToColor(colorHex));

        armorArrows.put(arrow.getUniqueId(), arrow);
        arrowShooters.put(arrow.getUniqueId(), player.getUniqueId());
        armorArrowCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler
    public void onArmorDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Arrow) || !(event.getEntity() instanceof Player)) {
            return;
        }

        Arrow arrow = (Arrow) event.getDamager();
        if (!armorArrows.containsKey(arrow.getUniqueId())) {
            return;
        }

        Player target = (Player) event.getEntity();
        Player shooter = plugin.getServer().getPlayer(arrowShooters.get(arrow.getUniqueId()));

        if (shooter == null || !shooter.isOnline()) {
            return;
        }

        // Игнорируем блокировку щитом
        event.setCancelled(false);

        // Получаем множитель урона броне из конфига
        double armorDamageMultiplier = plugin.getItemManager()
                .getItemConfigDouble("armor_arrow", "armor_damage_multiplier", 0.3);

        // Наносим дополнительный урон броне
        ItemStack[] armor = target.getInventory().getArmorContents();
        boolean armorDamaged = false;

        for (ItemStack item : armor) {
            if (item != null && item.getItemMeta() instanceof Damageable) {
                ItemMeta itemMeta = item.getItemMeta();
                if (itemMeta instanceof Damageable) {
                    Damageable meta = (Damageable) itemMeta;
                    int currentDurability = meta.getDamage();
                    int maxDurability = item.getType().getMaxDurability();
                    int additionalDamage = (int) (maxDurability * armorDamageMultiplier);

                    meta.setDamage(Math.min(currentDurability + additionalDamage, maxDurability));
                    item.setItemMeta((ItemMeta) meta);
                    armorDamaged = true;
                }
            }
        }

        target.getInventory().setArmorContents(armor);

        armorArrows.remove(arrow.getUniqueId());
        arrowShooters.remove(arrow.getUniqueId());
        arrow.remove();
    }

    private ItemStack getArrowFromInventory(Player player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();

        if (isArmorArrow(mainHand)) {
            return mainHand;
        } else if (isArmorArrow(offHand)) {
            return offHand;
        }

        for (ItemStack item : player.getInventory().getContents()) {
            if (isArmorArrow(item)) {
                return item;
            }
        }
        return null;
    }

    private boolean isArmorArrow(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return false;
        }

        String displayName = ColorUtil.stripColor(meta.getDisplayName());
        String configName = ColorUtil.stripColor(plugin.getItemManager()
                .getItem("armor_arrow").getItemMeta().getDisplayName());

        return displayName.equals(configName);
    }
}