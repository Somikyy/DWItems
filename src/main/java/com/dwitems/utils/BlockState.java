package com.dwitems.utils;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

public class BlockState {
    private final Location location;
    private final Material material;
    private final BlockData blockData;

    public BlockState(Location location, Material material, BlockData blockData) {
        this.location = location;
        this.material = material;
        this.blockData = blockData;
    }

    public Location getLocation() {
        return location;
    }

    public void restore() {
        location.getBlock().setType(material);
        location.getBlock().setBlockData(blockData);
    }
}