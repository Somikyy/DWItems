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
import org.bukkit.entity.Entity;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

import java.util.*;

public class HarpoonListener implements Listener {
    private final DWItems plugin;
    private final Map<UUID, Long> lastUseTime = new HashMap<>();
    private final Map<UUID, FishHook> activeHooks = new HashMap<>();
    private final Map<UUID, Location> hookLocations = new HashMap<>();
    private final Map<UUID, Entity> hookedEntities = new HashMap<>();
    private final Map<UUID, Long> combatTime = new HashMap<>();
    private final ItemManager itemManager;

    private String getUsesPrefix() {
        return plugin.getMessageManager().getMessage("messages.harpoon.uses_prefix");
    }

    public HarpoonListener(DWItems plugin) {
        this.plugin = plugin;
        this.itemManager = plugin.getItemManager();
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player) || !(event.getDamager() instanceof Player)) {
            return;
        }

        Player damaged = (Player) event.getEntity();
        Player damager = (Player) event.getDamager();

        // Устанавливаем боевой режим обоим игрокам
        setCombatTime(damaged);
        setCombatTime(damager);
    }

    @EventHandler
    public void onPlayerFish(PlayerFishEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (!isHarpoon(item)) {
            return;
        }

        String harpoonType = getHarpoonType(item);
        if (harpoonType == null) return;

        // Проверка блэклистов
        if (!itemManager.isItemAllowedInWorld(harpoonType, player.getWorld().getName())) {
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
                if (itemManager.hasWhitelistedRegion(harpoonType, region.getId())) {
                    hasWhitelistedRegion = true;
                    break;
                }
            }

            // Если нет региона из вайтлиста, проверяем блэклист
            if (!hasWhitelistedRegion) {
                for (ProtectedRegion region : regionSet) {
                    if (region.getId().equals("__global__")) continue;
                    if (!itemManager.isItemAllowedInRegion(harpoonType, region.getId())) {
                        event.setCancelled(true);
                        player.sendMessage("§6[§c✘§6] §cЗдесь этот предмет использовать нельзя!");
                        return;
                    }
                }
            }
        }

        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH){
            event.setCancelled(true);
            return;
        }

        // Проверяем количество использований
        int uses = getUses(item);
        if (uses <= 0) {
            player.sendMessage(plugin.getMessageManager().getMessage("messages.harpoon.no_uses"));
            event.setCancelled(true);
            return;
        }

        // Проверяем КД в пвп
        if ((event.getState() == PlayerFishEvent.State.FISHING ||
                event.getState() == PlayerFishEvent.State.REEL_IN) &&
                isInCombat(player)) {
            if (!player.hasPermission("dwitems.nocooldown")) {
                long cooldown = plugin.getItemManager().getItemConfig(harpoonType, "combat_cooldown", 5);
                long timeLeft = (lastUseTime.getOrDefault(player.getUniqueId(), 0L) + (cooldown * 1000)) - System.currentTimeMillis();
                if (timeLeft > 0) {
                    player.sendMessage(plugin.getMessageManager().getMessage("messages.harpoon.combat_cooldown",
                            "{time}", String.format("%.1f", timeLeft / 1000.0)));
                    event.setCancelled(true);
                    return;
                }
            }
        }

        switch (event.getState()) {
            case FISHING:
                // Бросок крюка
                FishHook hook = event.getHook();
                activeHooks.put(player.getUniqueId(), hook);
                break;

            case IN_GROUND:
                // Крюк попал в блок
                handleHookLanding(player, event.getHook().getLocation());
                break;


            case CAUGHT_ENTITY:
                // Крюк зацепил сущность
                if (event.getCaught() instanceof Player) {
                    Player caught = (Player) event.getCaught();
                    hookedEntities.put(player.getUniqueId(), caught);
                }
                break;

            case FAILED_ATTEMPT:
                // Крюк пролетает мимо - проверяем близость к блокам
                if (isNearBlock(event.getHook().getLocation(), 1.5)) {
                    handleHookLanding(player, event.getHook().getLocation());
                }
                break;

            case REEL_IN:
                // Когда игрок пытается притянуться
                FishHook activeHook = activeHooks.get(player.getUniqueId());
                if (activeHook != null && isNearWall(activeHook.getLocation())) {
                    handleHookLanding(player, activeHook.getLocation());
                }
                break;
        }

        lastUseTime.put(player.getUniqueId(), System.currentTimeMillis());
    }


    private void handleHookLanding(Player player, Location hookLocation) {
        // Проверяем, не попал ли крюк в жидкость
        if (hookLocation.getBlock().isLiquid()) {
            return;
        }

        hookLocations.put(player.getUniqueId(), hookLocation);
        pullPlayerToLocation(player, hookLocation);

        // Уменьшаем количество использований
        ItemStack item = player.getInventory().getItemInMainHand();
        int uses = getUses(item) - 1;
        updateUses(item, uses);

        // Устанавливаем время последнего использования только когда игрок реально притянулся
        if (isInCombat(player)) {
            lastUseTime.put(player.getUniqueId(), System.currentTimeMillis());
        }

        if (uses <= 0) {
            player.getInventory().setItemInMainHand(null);

            player.getWorld().spawnParticle(Particle.ITEM_CRACK, player.getLocation().add(0, 1, 0),
                    20, 0.2, 0.2, 0.2, 0.05, item);
        }
    }

    private boolean isVerticalSurface(Location location) {
        // Проверяем, есть ли проходимые блоки по бокам от данного блока
        return location.clone().add(1, 0, 0).getBlock().isPassable() ||
                location.clone().add(-1, 0, 0).getBlock().isPassable() ||
                location.clone().add(0, 0, 1).getBlock().isPassable() ||
                location.clone().add(0, 0, -1).getBlock().isPassable();
    }

    private boolean isNearWall(Location location) {
        // Проверяем блоки вокруг крюка с небольшим радиусом
        for (double x = -0.3; x <= 0.3; x += 0.3) {
            for (double y = -0.3; y <= 0.3; y += 0.3) {
                for (double z = -0.3; z <= 0.3; z += 0.3) {
                    Location checkLoc = location.clone().add(x, y, z);
                    if (!checkLoc.getBlock().isPassable()) {
                        // Проверяем, является ли блок "стеной" (вертикальной поверхностью)
                        if (isVerticalSurface(checkLoc.getBlock().getLocation())) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }


    private boolean isNearBlock(Location location, double radius) {
        // Проверяем блок прямо перед крюком
        Location forward = location.clone().add(location.getDirection().multiply(0.1));
        if (!forward.getBlock().isPassable()) {
            return true;
        }

        double radiusSquared = radius * radius;
        int blockX = location.getBlockX();
        int blockY = location.getBlockY();
        int blockZ = location.getBlockZ();

        // Проверяем блоки вокруг крюка
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    Location blockLoc = new Location(location.getWorld(),
                            blockX + x, blockY + y, blockZ + z);

                    if (!blockLoc.getBlock().isPassable() &&
                            location.distanceSquared(blockLoc) <= radiusSquared) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @EventHandler
    public void onPlayerChangeItem(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        clearHookData(player);
    }

    @EventHandler
    public void onAnvilPrepare(PrepareAnvilEvent event) {
        ItemStack[] items = event.getInventory().getContents();
        for (ItemStack item : items) {
            if (item != null && isHarpoon(item)) {
                event.setResult(null);
                return;
            }
        }
    }

    private void pullPlayerToLocation(Player player, Location target) {
        Location playerLoc = player.getLocation();
        String harpoonType = getHarpoonType(player.getInventory().getItemInMainHand());
        double distance = playerLoc.distance(target);

        int maxDistance = plugin.getItemManager().getItemConfig(harpoonType, "max_distance", 15);
        int baseSpeed = plugin.getItemManager().getItemConfig(harpoonType, "pull_speed", 5);

        if (distance > maxDistance) {
            player.sendMessage(plugin.getMessageManager().getMessage("messages.harpoon.too_far"));
            return;
        }

        Vector direction = target.toVector().subtract(playerLoc.toVector()).normalize();
        double speed = baseSpeed / 10.0;

        player.setVelocity(direction.multiply(speed));
        // Добавляем особые эффекты для гарпуна Скруджа
        if (harpoonType.equals("harpoon_scrooge")) {
            player.getWorld().spawnParticle(Particle.FLAME, player.getLocation(), 20, 0.3, 0.3, 0.3, 0.05);
            player.getWorld().spawnParticle(Particle.FLASH, player.getLocation(), 1);
        }
    }

    private String getHarpoonType(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            if (meta.getDisplayName().equals(plugin.getItemManager().getItem("harpoon_1").getItemMeta().getDisplayName())) {
                return "harpoon_1";
            } else if (meta.getDisplayName().equals(plugin.getItemManager().getItem("harpoon_2").getItemMeta().getDisplayName())) {
                return "harpoon_2";
            } else if (meta.getDisplayName().equals(plugin.getItemManager().getItem("harpoon_scrooge").getItemMeta().getDisplayName())) {
                return "harpoon_scrooge";
            }
        }
        return "harpoon_1";
    }

    private void clearHookData(Player player) {
        UUID uuid = player.getUniqueId();
        activeHooks.remove(uuid);
        hookLocations.remove(uuid);
        hookedEntities.remove(uuid);
    }

    private boolean isHarpoon(ItemStack item) {
        if (item == null || item.getType() != Material.FISHING_ROD) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return false;
        }
        String name = meta.getDisplayName();
        return name.equals(plugin.getItemManager().getItem("harpoon_1").getItemMeta().getDisplayName()) ||
                name.equals(plugin.getItemManager().getItem("harpoon_2").getItemMeta().getDisplayName()) ||
                name.equals(plugin.getItemManager().getItem("harpoon_scrooge").getItemMeta().getDisplayName());
    }

    private int getDefaultUses(String harpoonType) {
        switch (harpoonType) {
            case "harpoon_2":
                return 50;
            case "harpoon_scrooge":
                return 75;
            default:
                return 30;
        }
    }

    private int getUses(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasLore()) {
            List<String> lore = meta.getLore();
            if (lore != null) {
                String prefix = getUsesPrefix();
                for (String line : lore) {
                    if (line.startsWith(prefix)) {
                        try {
                            return Integer.parseInt(line.substring(prefix.length()));
                        } catch (NumberFormatException e) {
                            return getDefaultUses(getHarpoonType(item));
                        }
                    }
                }
            }
        }
        return getDefaultUses(getHarpoonType(item));
    }

    private void updateUses(ItemStack item, int uses) {
        String harpoonType = getHarpoonType(item);
        ItemStack template = plugin.getItemManager().getItem(harpoonType);
        ItemMeta templateMeta = template.getItemMeta();
        ItemMeta meta = item.getItemMeta();

        if (meta != null && templateMeta != null) {
            meta.setDisplayName(templateMeta.getDisplayName());

            List<String> lore = new ArrayList<>();
            String prefix = getUsesPrefix();

            for (String line : templateMeta.getLore()) {
                if (!line.startsWith(prefix)) {
                    lore.add(line);
                }
            }

            lore.add(prefix + uses);

            meta.setLore(lore);
            item.setItemMeta(meta);
        }
    }

    private void setCombatTime(Player player) {
        String harpoonType = getHarpoonType(player.getInventory().getItemInMainHand());
        combatTime.put(player.getUniqueId(), System.currentTimeMillis() +
                (plugin.getItemManager().getItemConfig(harpoonType, "combat_time", 30) * 1000));
    }

    private boolean isInCombat(Player player) {
        return combatTime.containsKey(player.getUniqueId()) &&
                combatTime.get(player.getUniqueId()) > System.currentTimeMillis();
    }
}