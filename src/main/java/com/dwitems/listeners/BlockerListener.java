package com.dwitems.listeners;

import com.dwitems.DWItems;
import com.dwitems.managers.ItemManager;
import com.dwitems.utils.BlockState;
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
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class BlockerListener implements Listener {
    private final DWItems plugin;
    private final HashMap<UUID, Long> cooldowns = new HashMap<>();
    private final List<Material> wallMaterials = Arrays.asList(
            Material.BEDROCK,
            Material.OBSIDIAN,
            Material.NETHERITE_BLOCK,
            Material.ANCIENT_DEBRIS
    );
    private final Random random = new Random();
    private static final String WALL_BLOCK_META = "blocker_wall";
    private final ItemManager itemManager;

    public BlockerListener(DWItems plugin) {
        this.plugin = plugin;
        this.itemManager = plugin.getItemManager();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (isWallBlock(block)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockDamage(BlockDamageEvent event) {
        if (isWallBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (isWallBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (isWallBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (isWallBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        if (isWallBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityBlockForm(EntityBlockFormEvent event) {
        if (isWallBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockFade(BlockFadeEvent event) {
        if (isWallBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (isWallBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    private boolean isWallBlock(Block block) {
        return block.hasMetadata(WALL_BLOCK_META);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null || item.getType() != Material.NETHERITE_INGOT) {
            return;
        }

        ItemStack blocker = plugin.getItemManager().getItem("blocker");
        if (!item.isSimilar(blocker)) {
            return;
        }

        if (!itemManager.isItemAllowedInWorld("blocker", player.getWorld().getName())) {
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
                if (itemManager.hasWhitelistedRegion("blocker", region.getId())) {
                    hasWhitelistedRegion = true;
                    break;
                }
            }

            // Если нет региона из вайтлиста, проверяем блэклист
            if (!hasWhitelistedRegion) {
                for (ProtectedRegion region : regionSet) {
                    if (region.getId().equals("__global__")) continue;
                    if (!itemManager.isItemAllowedInRegion("blocker", region.getId())) {
                        event.setCancelled(true);
                        player.sendMessage("§6[§c✘§6] §cЗдесь этот предмет использовать нельзя!");
                        return;
                    }
                }
            }
        }

        event.setCancelled(true);

        int cooldownTime = plugin.getItemManager().getItemConfig("blocker", "cooldown", 180);
        if (cooldowns.containsKey(player.getUniqueId())) {
            long timeLeft = cooldowns.get(player.getUniqueId()) - System.currentTimeMillis();
            if (timeLeft > 0) {
                player.sendMessage(plugin.getMessageManager().getMessage("messages.blocker.cooldown",
                        "{time}", String.valueOf(timeLeft / 1000)));
                return;
            }
        }

        int size = plugin.getItemManager().getItemConfig("blocker", "size", 5);
        int duration = plugin.getItemManager().getItemConfig("blocker", "duration", 30);
        int distance = plugin.getItemManager().getItemConfig("blocker", "distance", 2);

        Location wallLocation = getWallLocation(player, distance);
        if (wallLocation == null) {
            player.sendMessage(plugin.getMessageManager().getMessage("messages.blocker.invalid_location"));
            return;
        }

        if (isOverlapping(wallLocation, size, player.getLocation().getPitch())) {
            player.sendMessage(plugin.getMessageManager().getMessage("messages.blocker.overlap"));
            return;
        }

        List<BlockState> originalBlocks = createWall(wallLocation, size, player.getLocation().getPitch());

        cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (cooldownTime * 1000));

        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().removeItem(item);
        }

        player.sendMessage(plugin.getMessageManager().getMessage("messages.blocker.created"));

        new BukkitRunnable() {
            @Override
            public void run() {
                restoreBlocks(originalBlocks);
                player.sendMessage(plugin.getMessageManager().getMessage("messages.blocker.removed"));
            }
        }.runTaskLater(plugin, duration * 20L);
    }

    private Location getWallLocation(Player player, int distance) {
        Location playerLoc = player.getLocation();
        float pitch = playerLoc.getPitch();

        if (pitch <= -45) {
            return playerLoc.clone().add(0, 2, 0);
        } else if (pitch >= 45) {
            return playerLoc.clone().add(0, -1, 0);
        } else {
            Vector direction = player.getLocation().getDirection().normalize();
            return playerLoc.clone().add(direction.multiply(distance));
        }
    }

    private Set<Location> getWallLocations(Location center, int size, float pitch) {
        Set<Location> locations = new HashSet<>();
        int offset = size / 2;

        if (pitch <= -45 || pitch >= 45) {
            for (int x = -offset; x <= offset; x++) {
                for (int z = -offset; z <= offset; z++) {
                    locations.add(center.clone().add(x, 0, z));
                }
            }
        } else {
            Vector direction = center.getDirection();
            Vector right = new Vector(-direction.getZ(), 0, direction.getX()).normalize();

            for (int i = -offset; i <= offset; i++) {
                for (int j = -offset; j <= offset; j++) {
                    Location blockLoc = center.clone().add(
                            right.clone().multiply(i).add(new Vector(0, j, 0))
                    );
                    locations.add(blockLoc);
                }
            }
        }

        return locations;
    }

    private boolean isOverlapping(Location center, int size, float pitch) {
        Set<Location> wallLocations = getWallLocations(center, size, pitch);
        for (Location loc : wallLocations) {
            if (isWallBlock(loc.getBlock())) {
                return true;
            }
        }
        return false;
    }

    private List<BlockState> createWall(Location center, int size, float pitch) {
        List<BlockState> originalBlocks = new ArrayList<>();
        Set<Location> wallLocations = getWallLocations(center, size, pitch);

        for (Location loc : wallLocations) {
            Block block = loc.getBlock();
            originalBlocks.add(new BlockState(loc, block.getType(), block.getBlockData()));

            Material material = wallMaterials.get(random.nextInt(wallMaterials.size()));
            block.setType(material);
            block.setMetadata(WALL_BLOCK_META, new FixedMetadataValue(plugin, true));
        }

        return originalBlocks;
    }

    private void restoreBlocks(List<BlockState> blocks) {
        for (BlockState state : blocks) {
            Location loc = state.getLocation();
            Block block = loc.getBlock();
            if (block.hasMetadata(WALL_BLOCK_META)) {
                block.removeMetadata(WALL_BLOCK_META, plugin);
            }
            state.restore();
        }
    }
}