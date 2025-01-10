package com.dwitems.listeners;

import com.destroystokyo.paper.event.player.PlayerRecipeBookClickEvent;
import com.dwitems.DWItems;
import com.dwitems.managers.ItemManager;
import com.sk89q.commandbook.locations.WrappedSpawn;
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
import org.bukkit.block.Block;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.scheduler.BukkitRunnable;
import dev.espi.protectionstones.ProtectionStones;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class DwiTNTExplosivesListener implements Listener {
    private final DWItems plugin;
    private final Map<Location, ArmorStand> activeTimers = new HashMap<>();
    private final DecimalFormat df = new DecimalFormat("#.#");
    private final String[] TNT_TYPES = {"dwi_tnt", "tnt_bomb", "nuclear_tnt"};
    private final ItemManager itemManager;

    public DwiTNTExplosivesListener(DWItems plugin) {
        this.plugin = plugin;
        this.itemManager = plugin.getItemManager();
    }


    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTNTPlace(BlockPlaceEvent event) {
        if (event.getBlock().getType() != Material.TNT) return;

        ItemStack item = event.getItemInHand();

        ItemStack tntExplosives = plugin.getItemManager().getItem("tnt_explosives");
        if (tntExplosives != null && item.isSimilar(tntExplosives)) {
            return;
        }

        String tntId = getCustomTNTId(item);

        if (tntId == null) {
            event.setCancelled(true);
            return;
        }

        // Проверка блэклистов
        Player player = event.getPlayer();
        if (!itemManager.isItemAllowedInWorld("tnt_explosives", player.getWorld().getName())) {
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
                if (itemManager.hasWhitelistedRegion("tnt_explosives", region.getId())) {
                    hasWhitelistedRegion = true;
                    break;
                }
            }

            // Если нет региона из вайтлиста, проверяем блэклист
            if (!hasWhitelistedRegion) {
                for (ProtectedRegion region : regionSet) {
                    if (region.getId().equals("__global__")) continue;
                    if (!itemManager.isItemAllowedInRegion("tnt_explosives", region.getId())) {
                        event.setCancelled(true);
                        player.sendMessage("§6[§c✘§6] §cЗдесь этот предмет использовать нельзя!");
                        return;
                    }
                }
            }
        }

        event.setCancelled(false);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (event.getBlock().getType() == Material.TNT) {
                    plugin.getTNTManager().addTNTLocation(event.getBlock().getLocation(), tntId);
                }
            }
        }.runTask(plugin);
    }

    private String getCustomTNTId(ItemStack item) {
        if (item == null || item.getType() != Material.TNT) return null;

        ItemStack tntExplosives = plugin.getItemManager().getItem("tnt_explosives");
        if (tntExplosives != null && item.isSimilar(tntExplosives)) {
            return null;
        }

        for (String tntId : TNT_TYPES) {
            ItemStack customTNT = plugin.getItemManager().getItem(tntId);
            if (customTNT != null && item.isSimilar(customTNT)) {
                return tntId;
            }
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        Block block = event.getClickedBlock();

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && block != null &&
                block.getType() == Material.TNT &&
                plugin.getTNTManager().isCustomTNTLocation(block.getLocation())) {

            String typeName = item != null ? item.getType().name() : "";
            if (item != null && (item.getType() == Material.FLINT_AND_STEEL || typeName.equals("FIRE_CHARGE"))) {
                event.setCancelled(true);

                String tntId = plugin.getTNTManager().getTNTId(block.getLocation());
                if (tntId == null) return;

                int radius = plugin.getItemManager().getItemConfig(tntId, "radius", 3);
                int timer = plugin.getItemManager().getItemConfig(tntId, "timer", 5);

                block.setType(Material.AIR);
                TNTPrimed tnt = block.getWorld().spawn(block.getLocation().add(0.5, 0.0, 0.5), TNTPrimed.class);
                tnt.setFuseTicks(timer * 20);
                tnt.setYield(radius);

                tnt.setMetadata("tntId", new FixedMetadataValue(plugin, tntId));

                createTimerHologram(tnt, timer);

                plugin.getTNTManager().removeTNTLocation(block.getLocation());
            }
        }
    }

    private void createTimerHologram(TNTPrimed tnt, int timer) {
        Location hologramLoc = tnt.getLocation().add(0, 0.1, 0);
        ArmorStand hologram = (ArmorStand) tnt.getWorld().spawnEntity(hologramLoc, EntityType.ARMOR_STAND);
        hologram.setVisible(false);
        hologram.setGravity(false);
        hologram.setCustomNameVisible(true);
        hologram.setSmall(true);

        new BukkitRunnable() {
            double timeLeft = timer;

            @Override
            public void run() {
                if (timeLeft <= 0 || !tnt.isValid()) {
                    hologram.remove();
                    cancel();
                    return;
                }

                hologram.teleport(tnt.getLocation().add(0, 0.1, 0));
                hologram.setCustomName("§c" + df.format(timeLeft) + " сек");

                tnt.getWorld().spawnParticle(
                        Particle.FLAME,
                        tnt.getLocation().add(0, 0.5, 0),
                        3, 0.2, 0.2, 0.2, 0.01
                );

                timeLeft -= 0.1;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (event.getEntity() instanceof TNTPrimed) {
            TNTPrimed tnt = (TNTPrimed) event.getEntity();
            event.setCancelled(true);

            String tntId = tnt.hasMetadata("tntId") ? tnt.getMetadata("tntId").get(0).asString() : "dwi_tnt";

            createExplosion(tnt.getLocation(), (int) tnt.getYield(), tntId);
        }
    }

    private void createExplosion(Location location, int radius, String tntId) {
        location.getWorld().createExplosion(
                location.getX(),
                location.getY(),
                location.getZ(),
                0,
                true,
                false
        );

        Map<Location, String> chainReactionTNT = new HashMap<>();

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Location blockLoc = location.clone().add(x, y, z);
                    if (location.distance(blockLoc) <= radius) {
                        Block block = blockLoc.getBlock();

                        if (plugin.getServer().getPluginManager().isPluginEnabled("ProtectionStones")) {
                            if (ProtectionStones.isProtectBlock(block)) {
                                continue;
                            }
                        }

                        Material type = block.getType();
                        // Проверяем, не является ли блок частью защищенной арены
                        if (plugin.getServer().getPluginManager().isPluginEnabled("DWItems")) {
                            BedrockerListener bedrockerListener = null;
                            for (RegisteredListener listener : HandlerList.getRegisteredListeners(plugin)) {
                                if (listener.getListener() instanceof BedrockerListener) {
                                    bedrockerListener = (BedrockerListener) listener.getListener();
                                    break;
                                }
                            }

                            if (bedrockerListener != null && bedrockerListener.isLocationProtected(blockLoc)) {
                                continue; // Пропускаем защищенные блоки
                            }
                        }

                        if (type.name().contains("SHULKER_BOX")) {
                            ShulkerBox shulker = (ShulkerBox) block.getState();
                            String customName = shulker.getCustomName();

                            if (customName != null) {
                                if (customName.equals(plugin.getItemManager().getItem("shulker_36").getItemMeta().getDisplayName())) {
                                    block.setType(Material.AIR);
                                    plugin.getServer().getPluginManager().callEvent(
                                            new BlockExplodeEvent(block, new ArrayList<>(Collections.singletonList(block)), 0)
                                    );
                                    continue;
                                } else if (customName.equals(plugin.getItemManager().getItem("shulker_45").getItemMeta().getDisplayName())) {
                                    block.setType(Material.AIR);
                                    plugin.getServer().getPluginManager().callEvent(
                                            new BlockExplodeEvent(block, new ArrayList<>(Collections.singletonList(block)), 0)
                                    );
                                    continue;
                                } else if (customName.equals(plugin.getItemManager().getItem("shulker_54").getItemMeta().getDisplayName())) {
                                    block.setType(Material.AIR);
                                    plugin.getServer().getPluginManager().callEvent(
                                            new BlockExplodeEvent(block, new ArrayList<>(Collections.singletonList(block)), 0)
                                    );
                                    continue;
                                }
                            }

                            ItemStack[] contents = shulker.getInventory().getContents();
                            ItemStack shulkerItem = new ItemStack(type);
                            BlockStateMeta meta = (BlockStateMeta) shulkerItem.getItemMeta();

                            if (meta != null) {
                                ShulkerBox boxMeta = (ShulkerBox) meta.getBlockState();
                                boxMeta.getInventory().setContents(contents);
                                meta.setBlockState(boxMeta);
                                shulkerItem.setItemMeta(meta);

                                block.setType(Material.AIR);
                                location.getWorld().dropItemNaturally(blockLoc, shulkerItem);
                            }
                            continue;
                        }

                        if (type == Material.TNT) {
                            String nearbyTntId = plugin.getTNTManager().getTNTId(blockLoc);
                            if (nearbyTntId != null) {
                                chainReactionTNT.put(blockLoc.clone(), nearbyTntId);
                            }
                        }

                        switch(tntId) {
                            case "nuclear_tnt":
                                if (type != Material.BEDROCK &&
                                        type != Material.END_PORTAL_FRAME && type != Material.END_PORTAL) {
                                    block.setType(Material.AIR);
                                }
                                break;

                            case "tnt_bomb":
                                if (type != Material.BEDROCK
                                        && type != Material.CRYING_OBSIDIAN && type != Material.OBSIDIAN &&
                                        type != Material.END_PORTAL_FRAME && type != Material.END_PORTAL && type != Material.NETHER_PORTAL) {
                                    block.setType(Material.AIR);
                                }
                                break;

                            default: // dwi_tnt
                                if (type != Material.BEDROCK &&
                                        type != Material.CRYING_OBSIDIAN &&
                                        type != Material.OBSIDIAN &&
                                        type != Material.WATER &&
                                        type != Material.LAVA
                                        && type != Material.END_PORTAL_FRAME && type != Material.END_PORTAL && type != Material.NETHER_PORTAL) {
                                    block.setType(Material.AIR);
                                }
                                break;
                        }
                    }
                }
            }
        }

        // Визуальные эффекты взрыва
        switch(tntId) {
            case "nuclear_tnt":
                location.getWorld().spawnParticle(Particle.EXPLOSION_HUGE, location, 5);
                location.getWorld().spawnParticle(Particle.FLAME, location, 50);
                location.getWorld().spawnParticle(Particle.SMOKE_LARGE, location, 50);
                location.getWorld().playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 3.0f, 0.3f);
                location.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, location, 100, radius, radius, radius, 0.1);
                break;

            case "tnt_bomb":
                location.getWorld().spawnParticle(Particle.EXPLOSION_HUGE, location, 3);
                location.getWorld().spawnParticle(Particle.LAVA, location, 20);
                location.getWorld().playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.5f);
                break;

            default: // dwi_tnt
                location.getWorld().spawnParticle(Particle.EXPLOSION_HUGE, location, 1);
                location.getWorld().playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
                break;
        }

        //  цепная реакция с небольшой задержкой
        if (!chainReactionTNT.isEmpty()) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    for (Map.Entry<Location, String> entry : chainReactionTNT.entrySet()) {
                        Location tntLoc = entry.getKey();
                        String chainTntId = entry.getValue();

                        plugin.getTNTManager().removeTNTLocation(tntLoc);

                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                int chainRadius = plugin.getItemManager().getItemConfig(chainTntId, "radius", 3);
                                createExplosion(tntLoc, chainRadius, chainTntId);
                            }
                        }.runTaskLater(plugin, 5L); // 0.25 секунды задержки
                    }
                }
            }.runTaskLater(plugin, 5L); // 0.25 секунды задержки после основного взрыва
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() == Material.TNT) {
            Block block = event.getBlock();
            Location loc = block.getLocation();

            String tntId = plugin.getTNTManager().getTNTId(loc);
            if (tntId != null) {
                event.setDropItems(false);

                ItemStack customTNT = plugin.getItemManager().getItem(tntId);
                if (customTNT != null) {
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            loc.getWorld().dropItemNaturally(loc, customTNT);
                        }
                    }.runTask(plugin);
                }

                plugin.getTNTManager().removeTNTLocation(loc);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTNTIgnite(BlockIgniteEvent event) {
        if (event.getBlock().getType() == Material.TNT) {
            if (!plugin.getTNTManager().isCustomTNTLocation(event.getBlock().getLocation())) {
                event.setCancelled(true);
            }
        }
    }

}