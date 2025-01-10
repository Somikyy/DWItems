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
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class SmokeJumpListener implements Listener {
    private final DWItems plugin;
    private final Map<UUID, Integer> consecutiveJumps = new HashMap<>();
    private final Map<UUID, Long> lastJumpTime = new HashMap<>();
    private final Map<UUID, Set<Location>> activeSmoke = new HashMap<>();
    private final ItemManager itemManager;

    public SmokeJumpListener(DWItems plugin) {
        this.plugin = plugin;
        this.itemManager = plugin.getItemManager();
    }

    private String getUsesPrefix() {
        return plugin.getMessageManager().getMessage("messages.smoke_jump.uses_prefix");
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null || item.getType() != Material.POPPED_CHORUS_FRUIT) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return;
        }

        // Проверяем название предмета
        String displayName = plugin.getItemManager().getItem("smoke_jump").getItemMeta().getDisplayName();
        if (!meta.getDisplayName().equals(displayName)) {
            return;
        }

        // Проверка блэклистов
        if (!itemManager.isItemAllowedInWorld("smoke_jump", player.getWorld().getName())) {
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
                if (itemManager.hasWhitelistedRegion("smoke_jump", region.getId())) {
                    hasWhitelistedRegion = true;
                    break;
                }
            }

            // Если нет региона из вайтлиста, проверяем блэклист
            if (!hasWhitelistedRegion) {
                for (ProtectedRegion region : regionSet) {
                    if (region.getId().equals("__global__")) continue;
                    if (!itemManager.isItemAllowedInRegion("smoke_jump", region.getId())) {
                        event.setCancelled(true);
                        player.sendMessage("§6[§c✘§6] §cЗдесь этот предмет использовать нельзя!");
                        return;
                    }
                }
            }
        }

        event.setCancelled(true);

        // Проверяем кулдаун после двойного прыжка
        if (consecutiveJumps.getOrDefault(player.getUniqueId(), 0) >= 2) {
            int cooldown = plugin.getItemManager().getItemConfig("smoke_jump", "double_jump_cooldown", 3);
            long timeLeft = (lastJumpTime.getOrDefault(player.getUniqueId(), 0L) + (cooldown * 1000)) - System.currentTimeMillis();
            if (timeLeft > 0) {
                player.sendMessage(plugin.getMessageManager().getMessage("messages.smoke_jump.cooldown",
                        "{time}", String.format("%.1f", timeLeft / 1000.0)));
                return;
            }
            consecutiveJumps.put(player.getUniqueId(), 0);
        }

        // Проверяем и обновляем количество использований
        int uses = getUses(item);
        if (uses <= 0) {
            player.getInventory().setItemInMainHand(null);

            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);

            player.getWorld().spawnParticle(Particle.ITEM_CRACK, player.getLocation().add(0, 1, 0),
                    20, 0.2, 0.2, 0.2, 0.05, item);
            return;
        }

        // Выполняем прыжок
        double jumpHeight = plugin.getItemManager().getItemConfig("smoke_jump", "jump_height", 1);
        Vector velocity = player.getVelocity();
        velocity.setY(jumpHeight);
        player.setVelocity(velocity);

        // Создаем дымовой след
        createSmoke(player);

        // Обновляем счетчик прыжков
        consecutiveJumps.merge(player.getUniqueId(), 1, Integer::sum);
        lastJumpTime.put(player.getUniqueId(), System.currentTimeMillis());

        // Проигрываем звук
        player.playSound(player.getLocation(), Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 0.5f, 1.0f);

        // Обновляем предмет
        updateUses(item, uses - 1);
    }

    private void createSmoke(Player player) {
        Location smokeLoc = player.getLocation();
        Set<Location> playerSmoke = activeSmoke.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>());
        playerSmoke.add(smokeLoc);

        int smokeDuration = plugin.getItemManager().getItemConfig("smoke_jump", "smoke_duration", 3);
        double damagePerTick = plugin.getItemManager().getItemConfig("smoke_jump", "damage_per_tick", 1);

        new BukkitRunnable() {
            int ticks = smokeDuration * 20;

            @Override
            public void run() {
                if (ticks <= 0) {
                    playerSmoke.remove(smokeLoc);
                    if (playerSmoke.isEmpty()) {
                        activeSmoke.remove(player.getUniqueId());
                    }
                    this.cancel();
                    return;
                }

                // Создаем эффект дыма
                smokeLoc.getWorld().spawnParticle(Particle.DRAGON_BREATH, smokeLoc, 10, 0.5, 0.2, 0.5, 0.01);

                // Наносим урон игрокам в дыму
                smokeLoc.getWorld().getNearbyEntities(smokeLoc, 1, 1, 1).forEach(entity -> {
                    if (entity instanceof Player && entity != player) {
                        Player target = (Player) entity;
                        target.damage(damagePerTick);
                    }
                });

                ticks--;
            }
        }.runTaskTimer(plugin, 0L, 1L);
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
                            return plugin.getItemManager().getItemConfig("smoke_jump", "default_uses", 30);
                        }
                    }
                }
            }
        }
        return plugin.getItemManager().getItemConfig("smoke_jump", "default_uses", 30);
    }

    private void updateUses(ItemStack item, int uses) {
        ItemStack template = plugin.getItemManager().getItem("smoke_jump");
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

            templateMeta.getEnchants().forEach((enchant, level) ->
                    meta.addEnchant(enchant, level, true));
            templateMeta.getItemFlags().forEach(meta::addItemFlags);

            item.setItemMeta(meta);
        }
    }
}