package com.dwitems.listeners;

import com.dwitems.DWItems;
import com.dwitems.managers.ItemManager;
import com.dwitems.utils.BlockState;
import com.dwitems.utils.Reloadable;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
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
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.FileInputStream;
import java.util.*;

public class BedrockerListener implements Listener, Reloadable {
    private final DWItems plugin;
    private final HashMap<UUID, Long> cooldowns = new HashMap<>();
    private final Set<Location> activeArenas = new HashSet<>();
    private final int minDistance;
    private Clipboard schematic;
    private boolean schematicLoaded = false;
    private final ItemManager itemManager;

    public BedrockerListener(DWItems plugin) {
        this.plugin = plugin;
        this.minDistance = plugin.getItemManager().getItemConfig("trapka", "min_distance", 10);
        loadSchematic();
        this.itemManager = plugin.getItemManager();
    }

    private void loadSchematic() {
        File schematicFile = new File(plugin.getDataFolder(), "schematics/trapka.schem");
        if (!schematicFile.exists()) {
            plugin.getLogger().warning("Trapka schematic not found!");
            return;
        }

        try {
            ClipboardFormat format = ClipboardFormats.findByFile(schematicFile);
            try (ClipboardReader reader = format.getReader(new FileInputStream(schematicFile))) {
                schematic = reader.read();
                schematicLoaded = true;
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load trapka schematic: " + e.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        if (isLocationProtected(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockDamage(BlockDamageEvent event) {
        if (isLocationProtected(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    private boolean isNearExistingArena(Location newArenaCenter) {
        for (Location existingArenaLoc : activeArenas) {
            if (!existingArenaLoc.getWorld().equals(newArenaCenter.getWorld())) {
                continue;
            }
            double distance = existingArenaLoc.distance(newArenaCenter);
            if (distance < minDistance) {
                return true;
            }
        }
        return false;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        Location center = player.getLocation().clone();

        if (item == null || item.getType() != Material.SHULKER_SHELL) {
            return;
        }

        ItemStack bedrocker = plugin.getItemManager().getItem("trapka");
        if (!item.isSimilar(bedrocker)) {
            return;
        }

        if (!schematicLoaded) {
            player.sendMessage(plugin.getMessageManager().getMessage("messages.trapka.error_loading"));
            return;
        }

        if (!itemManager.isItemAllowedInWorld("trapka", player.getWorld().getName())) {
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
                if (itemManager.hasWhitelistedRegion("trapka", region.getId())) {
                    hasWhitelistedRegion = true;
                    break;
                }
            }

            // Если нет региона из вайтлиста, проверяем блэклист
            if (!hasWhitelistedRegion) {
                for (ProtectedRegion region : regionSet) {
                    if (region.getId().equals("__global__")) continue;
                    if (!itemManager.isItemAllowedInRegion("trapka", region.getId())) {
                        event.setCancelled(true);
                        player.sendMessage("§6[§c✘§6] §cЗдесь этот предмет использовать нельзя!");
                        return;
                    }
                }
            }
        }

        event.setCancelled(true);

        if (isNearExistingArena(center)) {
            player.sendMessage(plugin.getMessageManager().getMessage("messages.trapka.too_close"));
            return;
        }

        if (isOverlapping(center)) {
            player.sendMessage(plugin.getMessageManager().getMessage("messages.trapka.overlap"));
            return;
        }

        int cooldownTime = plugin.getItemManager().getItemConfig("trapka", "cooldown", 180);
        if (cooldowns.containsKey(player.getUniqueId())) {
            long timeLeft = cooldowns.get(player.getUniqueId()) - System.currentTimeMillis();
            if (timeLeft > 0) {
                player.sendMessage(plugin.getMessageManager().getMessage("messages.trapka.cooldown",
                        "{time}", String.valueOf(timeLeft / 1000)));
                return;
            }
        }

        List<BlockState> originalBlocks = pasteSchematic(center);
        if (originalBlocks.isEmpty()) {
            player.sendMessage(plugin.getMessageManager().getMessage("messages.trapka.error_placing"));
            return;
        }

        cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (cooldownTime * 1000));

        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().removeItem(item);
        }

        player.sendMessage(plugin.getMessageManager().getMessage("messages.trapka.created"));

        int duration = plugin.getItemManager().getItemConfig("trapka", "duration", 25);
        new BukkitRunnable() {
            @Override
            public void run() {
                restoreBlocks(originalBlocks);
                activeArenas.remove(center);
                player.sendMessage(plugin.getMessageManager().getMessage("messages.trapka.removed"));
            }
        }.runTaskLater(plugin, duration * 20L);
    }

    private List<BlockState> pasteSchematic(Location center) {
        List<BlockState> originalBlocks = new ArrayList<>();
        try (EditSession editSession = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(center.getWorld()))) {
            BlockVector3 to = BlockVector3.at(center.getX(), center.getY(), center.getZ());

            // Сохраняем оригинальные блоки
            BlockVector3 min = to.subtract(schematic.getDimensions().divide(2));
            BlockVector3 max = min.add(schematic.getDimensions());

            for (int x = min.getX(); x <= max.getX(); x++) {
                for (int y = min.getY(); y <= max.getY(); y++) {
                    for (int z = min.getZ(); z <= max.getZ(); z++) {
                        Location loc = new Location(center.getWorld(), x, y, z);
                        Block block = loc.getBlock();
                        originalBlocks.add(new BlockState(loc, block.getType(), block.getBlockData()));
                    }
                }
            }

            Operation operation = new ClipboardHolder(schematic)
                    .createPaste(editSession)
                    .to(to)
                    .ignoreAirBlocks(false)
                    .build();

            Operations.complete(operation);
            activeArenas.add(center);

            return originalBlocks;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to paste schematic: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    private boolean isOverlapping(Location center) {
        if (schematic == null) return true;

        BlockVector3 dimensions = schematic.getDimensions();
        int halfWidth = dimensions.getX() / 2 + 1;
        int halfHeight = dimensions.getY() / 2 + 1;
        int halfLength = dimensions.getZ() / 2 + 1;

        for (Location areneLoc : activeArenas) {
            if (!areneLoc.getWorld().equals(center.getWorld())) continue;

            double dx = Math.abs(center.getX() - areneLoc.getX());
            double dy = Math.abs(center.getY() - areneLoc.getY());
            double dz = Math.abs(center.getZ() - areneLoc.getZ());

            if (dx < halfWidth + 1 && dy < halfHeight + 1 && dz < halfLength + 1) {
                return true;
            }
        }
        return false;
    }

    private void restoreBlocks(List<BlockState> blocks) {
        blocks.forEach(BlockState::restore);
    }

    @Override
    public void reload() {
        cooldowns.clear();
        activeArenas.clear();
        loadSchematic();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (isLocationProtected(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (isLocationProtected(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (isLocationProtected(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        if (isLocationProtected(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityBlockForm(EntityBlockFormEvent event) {
        if (isLocationProtected(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (isLocationProtected(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockSpread(BlockSpreadEvent event) {
        if (isLocationProtected(event.getBlock().getLocation()) ||
                isLocationProtected(event.getSource().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBurn(BlockBurnEvent event) {
        if (isLocationProtected(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) {
            if (isLocationProtected(block.getLocation()) ||
                    isLocationProtected(block.getRelative(event.getDirection()).getLocation())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block block : event.getBlocks()) {
            if (isLocationProtected(block.getLocation()) ||
                    isLocationProtected(block.getRelative(event.getDirection()).getLocation())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockFade(BlockFadeEvent event) {
        if (isLocationProtected(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    public boolean isLocationProtected(Location location) {
        if (location == null || activeArenas.isEmpty() || schematic == null) {
            return false;
        }

        BlockVector3 dimensions = schematic.getDimensions();
        int halfWidth = dimensions.getX() / 2 + 1;  // +1 для дополнительного блока
        int halfHeight = dimensions.getY() / 2 + 1;  // +1 для потолка
        int halfLength = dimensions.getZ() / 2 + 1;  // +1 для north стороны

        for (Location center : activeArenas) {
            if (!center.getWorld().equals(location.getWorld())) continue;

            // Проверяем, находится ли точка внутри расширенного кубоида схематики
            double dx = location.getX() - center.getX();
            double dy = location.getY() - center.getY();
            double dz = location.getZ() - center.getZ();

            if (Math.abs(dx) <= halfWidth + 0.5 &&
                    Math.abs(dy) <= halfHeight + 0.5 &&
                    Math.abs(dz) <= halfLength + 0.5) {
                return true;
            }
        }
        return false;
    }
}