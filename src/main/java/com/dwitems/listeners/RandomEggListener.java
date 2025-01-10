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
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class RandomEggListener implements Listener {
    private final DWItems plugin;
    private final Random random = new Random();
    private final Map<String, EntityType> entityTypes = new HashMap<>();
    private final ItemManager itemManager;

    public RandomEggListener(DWItems plugin) {
        this.plugin = plugin;
        this.itemManager = plugin.getItemManager();
        initEntityTypes();
    }

    private void initEntityTypes() {
        entityTypes.put("ZOMBIE", EntityType.ZOMBIE);
        entityTypes.put("CREEPER", EntityType.CREEPER);
        entityTypes.put("SKELETON", EntityType.SKELETON);
        entityTypes.put("SPIDER", EntityType.SPIDER);
        entityTypes.put("BLAZE", EntityType.BLAZE);
        entityTypes.put("WITHER_SKELETON", EntityType.WITHER_SKELETON);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEggPreSpawn(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item != null && item.getType() == Material.PHANTOM_SPAWN_EGG) {
            ItemStack randomEgg = plugin.getItemManager().getItem("random_egg");
            if (item.isSimilar(randomEgg)) {
                event.setCancelled(true);
                event.setUseInteractedBlock(Event.Result.DENY);
                event.setUseItemInHand(Event.Result.DENY);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        Block clickedBlock = event.getClickedBlock();
        if (item == null) {
            return;
        }

        if (item.getType() != Material.PHANTOM_SPAWN_EGG) {
            return;
        }

        ItemStack randomEgg = plugin.getItemManager().getItem("random_egg");
        if (!item.isSimilar(randomEgg)) {
            return;
        }

        if (!itemManager.isItemAllowedInWorld("random_egg", player.getWorld().getName())) {
            event.setCancelled(true);
            player.sendMessage("§6[§c✘§6] §cЗдесь этот предмет использовать нельзя!");
            return;
        }
        event.setCancelled(true);

        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionManager regions = container.get(BukkitAdapter.adapt(player.getWorld()));

        if (regions != null) {
            BlockVector3 loc = BlockVector3.at(player.getLocation().getX(),
                    player.getLocation().getY(),
                    player.getLocation().getZ());

            ApplicableRegionSet regionSet = regions.getApplicableRegions(loc);

            for (ProtectedRegion region : regionSet) {
                if (region.getId().equals("__global__")) continue;
                if (!itemManager.isItemAllowedInRegion("random_egg", region.getId())) {
                    event.setCancelled(true);
                    player.sendMessage("§6[§c✘§6] §cЗдесь этот предмет использовать нельзя!");
                    return;
                }
            }
        }

        event.setCancelled(true);

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK &&
                clickedBlock != null &&
                clickedBlock.getType() == Material.SPAWNER) {
            handleSpawnerPlacement(clickedBlock, player, item);
            return;
        }

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            handleMobSpawn(player, event.getClickedBlock(), item, event);
        }
    }

    private void handleSpawnerPlacement(Block spawnerBlock, Player player, ItemStack item) {
        EntityType selectedType = getRandomEntityType();

        CreatureSpawner spawner = (CreatureSpawner) spawnerBlock.getState();
        spawner.setSpawnedType(selectedType);
        spawner.update();

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (item.getAmount() > 1) {
                ItemStack newItem = item.clone();
                newItem.setAmount(item.getAmount() - 1);
                player.getInventory().setItemInMainHand(newItem);
            } else {
                player.getInventory().setItemInMainHand(null);
            }
            player.updateInventory();
        });
    }

    private void handleMobSpawn(Player player, Block clickedBlock, ItemStack item, PlayerInteractEvent event) {
            event.setCancelled(true);

            Location spawnLocation;
            if (clickedBlock != null) {
                spawnLocation = clickedBlock.getLocation().add(0, 1, 0);
            } else {
                spawnLocation = player.getLocation().add(player.getLocation().getDirection().multiply(2));
            }
            EntityType selectedType = getRandomEntityType();
            spawnLocation.getWorld().spawnEntity(spawnLocation, selectedType);

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (item.getAmount() > 1) {
                    ItemStack newItem = item.clone();
                    newItem.setAmount(item.getAmount() - 1);
                    player.getInventory().setItemInMainHand(newItem);
                } else {
                    player.getInventory().setItemInMainHand(null);
                }
                player.updateInventory();
            });
    }

    private EntityType getRandomEntityType() {
        int totalChance = 0;
        Map<String, Integer> chances = new HashMap<>();

        for (String entityName : entityTypes.keySet()) {
            int chance = plugin.getItemManager().getItemConfig("random_egg", "chances." + entityName, 0);
            chances.put(entityName, chance);
            totalChance += chance;
        }
        if (totalChance == 0) {
            return EntityType.ZOMBIE;
        }

        int random = this.random.nextInt(totalChance);

        int currentSum = 0;
        for (Map.Entry<String, Integer> entry : chances.entrySet()) {
            currentSum += entry.getValue();
            if (random < currentSum) {
                return entityTypes.get(entry.getKey());
            }
        }
        return EntityType.ZOMBIE;

    }
}