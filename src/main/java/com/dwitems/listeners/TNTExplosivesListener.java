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
import dev.espi.protectionstones.ProtectionStones;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class TNTExplosivesListener implements Listener {
    private final DWItems plugin;
    private final Set<Material> unbreakableBlocks;
    private final ItemManager itemManager;

    public TNTExplosivesListener(DWItems plugin) {
        this.plugin = plugin;
        this.unbreakableBlocks = new HashSet<>();
        this.itemManager = plugin.getItemManager();
        unbreakableBlocks.add(Material.OBSIDIAN);
        unbreakableBlocks.add(Material.CRYING_OBSIDIAN);
        unbreakableBlocks.add(Material.BEDROCK);
        unbreakableBlocks.add(Material.ANCIENT_DEBRIS);
        unbreakableBlocks.add(Material.NETHERITE_BLOCK);
        unbreakableBlocks.add(Material.END_PORTAL_FRAME);
        unbreakableBlocks.add(Material.END_PORTAL);
        unbreakableBlocks.add(Material.NETHER_PORTAL);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null || item.getType() != Material.TNT) {
            return;
        }

        // Проверяем, является ли предмет взрывчаткой
        ItemStack tntExplosives = plugin.getItemManager().getItem("tnt_explosives");
        if (!item.isSimilar(tntExplosives)) {
            return;
        }

        // Проверка блэклистов перед выполнением основной логики
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

        event.setCancelled(true);

        // Уменьшаем количество предметов в руке
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }

        // Создаем брошенный предмет
        Item droppedTNT = player.getWorld().dropItem(player.getEyeLocation(), new ItemStack(Material.TNT, 1));
        droppedTNT.setPickupDelay(Integer.MAX_VALUE);
        droppedTNT.setInvulnerable(true);
        Vector direction = player.getLocation().getDirection();
        droppedTNT.setVelocity(direction.multiply(0.5));

        int explosionDelay = plugin.getItemManager().getItemConfig("tnt_explosives", "explosion_delay", 10);
        int explosionRadius = plugin.getItemManager().getItemConfig("tnt_explosives", "explosion_radius", 3);

        new BukkitRunnable() {
            int timeLeft = explosionDelay;
            boolean isWarningStarted = false;

            @Override
            public void run() {
                if (!droppedTNT.isValid() || droppedTNT.isDead()) {
                    this.cancel();
                    return;
                }

                Location tntLoc = droppedTNT.getLocation();

                if (timeLeft <= 3) {
                    tntLoc.getWorld().spawnParticle(Particle.SMOKE_NORMAL, tntLoc, 10, 0.2, 0.2, 0.2, 0.05);
                    tntLoc.getWorld().playSound(tntLoc, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 1.0f);
                }

                if (timeLeft <= 3 && !isWarningStarted) {
                    isWarningStarted = true;
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (!droppedTNT.isValid() || droppedTNT.isDead() || timeLeft <= 0) {
                                this.cancel();
                                return;
                            }
                            tntLoc.getWorld().playSound(tntLoc, Sound.ENTITY_TNT_PRIMED, 1.5f, 0.5f);
                        }
                    }.runTaskTimer(plugin, 0L, 20L);
                }

                if (timeLeft <= 0) {
                    droppedTNT.remove();
                    Location explosionLoc = tntLoc.clone();

                    // Сначала проверяем мир
                    if (!itemManager.isItemAllowedInWorld("tnt_explosives", explosionLoc.getWorld().getName())) {
                        tntLoc.getWorld().spawnParticle(Particle.SMOKE_LARGE, explosionLoc, 20, 0.5, 0.5, 0.5, 0.1);
                        tntLoc.getWorld().playSound(explosionLoc, Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 1.0f);
                        this.cancel();
                        return;
                    }

                    RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
                    RegionManager regions = container.get(BukkitAdapter.adapt(explosionLoc.getWorld()));

                    // Проверяем все блоки в радиусе взрыва перед тем как что-то делать
                    boolean canExplode = true;
                    if (regions != null) {
                        for (int x = -explosionRadius; x <= explosionRadius && canExplode; x++) {
                            for (int y = -explosionRadius; y <= explosionRadius && canExplode; y++) {
                                for (int z = -explosionRadius; z <= explosionRadius && canExplode; z++) {
                                    Location blockLoc = explosionLoc.clone().add(x, y, z);
                                    if (blockLoc.distance(explosionLoc) <= explosionRadius) {
                                        BlockVector3 loc = BlockVector3.at(
                                                blockLoc.getX(),
                                                blockLoc.getY(),
                                                blockLoc.getZ()
                                        );

                                        ApplicableRegionSet regionSet = regions.getApplicableRegions(loc);
                                        boolean hasWhitelistedRegion = false;

                                        // Проверяем вайтлист
                                        for (ProtectedRegion region : regionSet) {
                                            if (region.getId().equals("__global__")) continue;
                                            if (itemManager.hasWhitelistedRegion("tnt_explosives", region.getId())) {
                                                hasWhitelistedRegion = true;
                                                break;
                                            }
                                        }

                                        // Если нет в вайтлисте, проверяем блэклист
                                        if (!hasWhitelistedRegion) {
                                            for (ProtectedRegion region : regionSet) {
                                                if (region.getId().equals("__global__")) continue;
                                                if (!itemManager.isItemAllowedInRegion("tnt_explosives", region.getId())) {
                                                    canExplode = false;
                                                    break;
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Если хоть один блок в запрещенном регионе - отменяем весь взрыв
                    if (!canExplode) {
                        tntLoc.getWorld().spawnParticle(Particle.SMOKE_LARGE, explosionLoc, 20, 0.5, 0.5, 0.5, 0.1);
                        tntLoc.getWorld().playSound(explosionLoc, Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 1.0f);
                        this.cancel();
                        return;
                    }

                    tntLoc.getWorld().spawnParticle(Particle.EXPLOSION_HUGE, explosionLoc, 1);
                    tntLoc.getWorld().playSound(explosionLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);

                    for (int x = -explosionRadius; x <= explosionRadius; x++) {
                        for (int y = -explosionRadius; y <= explosionRadius; y++) {
                            for (int z = -explosionRadius; z <= explosionRadius; z++) {
                                Location blockLoc = explosionLoc.clone().add(x, y, z);
                                if (blockLoc.distance(explosionLoc) <= explosionRadius) {
                                    Block block = blockLoc.getBlock();

                                    if (plugin.getServer().getPluginManager().isPluginEnabled("ProtectionStones")) {
                                        if (ProtectionStones.isProtectBlock(block)) {
                                            continue;
                                        }
                                    }

                                    Material blockType = block.getType();

                                    if (blockType.name().contains("SHULKER_BOX")) {
                                        ShulkerBox shulker = (ShulkerBox) block.getState();
                                        String customName = shulker.getCustomName();

                                        if (customName != null) {
                                            ItemStack shulker36 = plugin.getItemManager().getItem("shulker_36");
                                            ItemStack shulker45 = plugin.getItemManager().getItem("shulker_45");
                                            ItemStack shulker54 = plugin.getItemManager().getItem("shulker_54");

                                            if (shulker36 != null && customName.equals(shulker36.getItemMeta().getDisplayName())) {
                                                plugin.getServer().getPluginManager().callEvent(
                                                        new BlockExplodeEvent(block, new ArrayList<>(Collections.singletonList(block)), 0)
                                                );
                                                new BukkitRunnable() {
                                                    @Override
                                                    public void run() {
                                                        block.setType(Material.AIR);
                                                    }
                                                }.runTaskLater(plugin, 1L);
                                                continue;
                                            } else if (shulker45 != null && customName.equals(shulker45.getItemMeta().getDisplayName())) {
                                                plugin.getServer().getPluginManager().callEvent(
                                                        new BlockExplodeEvent(block, new ArrayList<>(Collections.singletonList(block)), 0)
                                                );
                                                new BukkitRunnable() {
                                                    @Override
                                                    public void run() {
                                                        block.setType(Material.AIR);
                                                    }
                                                }.runTaskLater(plugin, 1L);
                                                continue;
                                            } else if (shulker54 != null && customName.equals(shulker54.getItemMeta().getDisplayName())) {
                                                plugin.getServer().getPluginManager().callEvent(
                                                        new BlockExplodeEvent(block, new ArrayList<>(Collections.singletonList(block)), 0)
                                                );
                                                new BukkitRunnable() {
                                                    @Override
                                                    public void run() {
                                                        block.setType(Material.AIR);
                                                    }
                                                }.runTaskLater(plugin, 1L);
                                                continue;
                                            }
                                        }

                                        ItemStack[] contents = shulker.getInventory().getContents();
                                        ItemStack shulkerItem = new ItemStack(blockType);
                                        BlockStateMeta meta = (BlockStateMeta) shulkerItem.getItemMeta();

                                        if (meta != null) {
                                            ShulkerBox boxMeta = (ShulkerBox) meta.getBlockState();
                                            boxMeta.getInventory().setContents(contents);
                                            meta.setBlockState(boxMeta);
                                            shulkerItem.setItemMeta(meta);

                                            block.setType(Material.AIR);
                                            blockLoc.getWorld().dropItemNaturally(blockLoc, shulkerItem);
                                        }
                                        continue;
                                    }

                                    if (!unbreakableBlocks.contains(blockType) &&
                                            !blockType.name().contains("WATER") &&
                                            !blockType.name().contains("LAVA")) {
                                        block.setType(Material.AIR);
                                    }
                                }
                            }
                        }
                    }

                    double damage = 10.0;
                    explosionLoc.getWorld().getNearbyEntities(explosionLoc, explosionRadius, explosionRadius, explosionRadius).forEach(entity -> {
                        if (entity instanceof Player) {
                            Player player = (Player) entity;
                            double distance = player.getLocation().distance(explosionLoc);
                            double finalDamage = damage * (1 - (distance / explosionRadius));
                            player.damage(finalDamage);
                        }
                    });

                    this.cancel();
                    return;
                }
            }
        };
    }
}