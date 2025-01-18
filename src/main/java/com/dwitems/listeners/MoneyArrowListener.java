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
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MoneyArrowListener implements Listener {
    private final DWItems plugin;
    private final Map<String, Map<UUID, Long>> cooldowns = new HashMap<>();
    private final Map<UUID, Arrow> moneyArrows = new HashMap<>();
    private final Map<UUID, UUID> arrowShooters = new HashMap<>();
    private final ItemManager itemManager;
    private Economy economy;

    public MoneyArrowListener(DWItems plugin) {
        this.plugin = plugin;
        this.itemManager = plugin.getItemManager();
        setupEconomy();
        cooldowns.put("money_arrow", new HashMap<>());
    }

    private boolean setupEconomy() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return true;
    }

    @EventHandler
    public void onBowShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player) || !(event.getProjectile() instanceof Arrow)) {
            return;
        }

        Player player = (Player) event.getEntity();
        ItemStack arrowItem = event.getConsumable();

        if (arrowItem == null || !isMoneyArrow(arrowItem)) {
            return;
        }

        // Проверка блэклистов
        if (!itemManager.isItemAllowedInWorld("money_arrow", player.getWorld().getName())) {
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
                if (itemManager.hasWhitelistedRegion("money_arrow", region.getId())) {
                    hasWhitelistedRegion = true;
                    break;
                }
            }

            // Если нет региона из вайтлиста, проверяем блэклист
            if (!hasWhitelistedRegion) {
                for (ProtectedRegion region : regionSet) {
                    if (region.getId().equals("__global__")) continue;
                    if (!itemManager.isItemAllowedInRegion("money_arrow", region.getId())) {
                        event.setCancelled(true);
                        player.sendMessage("§6[§c✘§6] §cЗдесь этот предмет использовать нельзя!");
                        return;
                    }
                }
            }
        }

        // Проверка кулдауна
        if (!player.hasPermission("dwitems.nocooldown")) {
            if (isOnCooldown(player)) {
                event.setCancelled(true);
                long timeLeft = getTimeLeft(player);
                player.sendMessage(plugin.getMessageManager().getMessage("messages.money_arrow.cooldown",
                        "{time}", String.valueOf(timeLeft)));
                return;
            }
        }

        Arrow arrow = (Arrow) event.getProjectile();
        arrow.setGlowing(true);

        String colorHex = plugin.getItemManager().getItemConfigHexColor("money_arrow", "arrow_color", "FFE500");
        arrow.setColor(ColorConverter.hexToColor(colorHex));

        moneyArrows.put(arrow.getUniqueId(), arrow);
        arrowShooters.put(arrow.getUniqueId(), player.getUniqueId());
        setCooldown(player);
    }

    @EventHandler
    public void onArrowHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Arrow) || !(event.getHitEntity() instanceof Player)) {
            return;
        }

        Arrow arrow = (Arrow) event.getEntity();
        if (!moneyArrows.containsKey(arrow.getUniqueId())) {
            return;
        }

        Player target = (Player) event.getHitEntity();
        Player shooter = plugin.getServer().getPlayer(arrowShooters.get(arrow.getUniqueId()));

        if (shooter == null || !shooter.isOnline()) {
            return;
        }

        // Получаем баланс цели
        double targetBalance = economy.getBalance(target);
        if (targetBalance <= 0) {
            shooter.sendMessage(plugin.getMessageManager().getMessage("messages.money_arrow.no_money"));
            return;
        }

        // Вычисляем 1% от баланса
        double amountToSteal = Math.round(targetBalance * 0.01 * 100.0) / 100.0;

        // Снимаем деньги у цели
        economy.withdrawPlayer(target, amountToSteal);
        // Даем деньги стрелку
        economy.depositPlayer(shooter, amountToSteal);

        // Отправляем сообщения
        shooter.sendMessage(plugin.getMessageManager().getMessage("messages.money_arrow.stolen",
                "{amount}", String.format("%.2f", amountToSteal),
                "{player}", target.getName()));

        target.sendMessage(plugin.getMessageManager().getMessage("messages.money_arrow.lost",
                "{amount}", String.format("%.2f", amountToSteal),
                "{player}", shooter.getName()));

        moneyArrows.remove(arrow.getUniqueId());
        arrowShooters.remove(arrow.getUniqueId());
        arrow.remove();
    }

    private ItemStack getArrowFromInventory(Player player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();

        if (isMoneyArrow(mainHand)) {
            return mainHand;
        } else if (isMoneyArrow(offHand)) {
            return offHand;
        }

        for (ItemStack item : player.getInventory().getContents()) {
            if (isMoneyArrow(item)) {
                return item;
            }
        }
        return null;
    }

    private boolean isMoneyArrow(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return false;
        }

        String displayName = ColorUtil.stripColor(meta.getDisplayName());
        String configName = ColorUtil.stripColor(plugin.getItemManager()
                .getItem("money_arrow").getItemMeta().getDisplayName());

        return displayName.equals(configName);
    }

    private boolean isOnCooldown(Player player) {
        Map<UUID, Long> arrowCooldowns = cooldowns.get("money_arrow");
        return arrowCooldowns.containsKey(player.getUniqueId()) &&
                arrowCooldowns.get(player.getUniqueId()) > System.currentTimeMillis();
    }

    private long getTimeLeft(Player player) {
        Map<UUID, Long> arrowCooldowns = cooldowns.get("money_arrow");
        return (arrowCooldowns.get(player.getUniqueId()) - System.currentTimeMillis()) / 1000;
    }

    private void setCooldown(Player player) {
        Map<UUID, Long> arrowCooldowns = cooldowns.get("money_arrow");
        long cooldownTime = plugin.getItemManager().getItemConfig("money_arrow", "cooldown", 90);
        arrowCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (cooldownTime * 1000));
    }

    private void cleanupOldCooldowns() {
        long currentTime = System.currentTimeMillis();
        cooldowns.get("money_arrow").entrySet().removeIf(entry -> entry.getValue() < currentTime);
    }
}