package com.dwitems.items;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CustomItem {
    private final String id;
    private final String configId;
    private final String displayName;
    private final List<String> lore;
    private final ItemStack item;

    public CustomItem(String id, String displayName, List<String> lore, ItemStack item, String configId) {
        this.id = id;
        this.displayName = displayName;
        this.lore = lore;
        this.item = item;
        this.configId = configId;
    }

    public ItemStack create() {
        ItemStack customItem = item.clone();
        ItemMeta meta = customItem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(formatHex(displayName));
            meta.setLore(formatHexList(lore));

            String numberStr = configId.replaceAll("[^0-9]", "");
            if (!numberStr.isEmpty()) {
                int customModelId = Integer.parseInt(numberStr);
                meta.setCustomModelData(customModelId);
            }

            NamespacedKey key = new NamespacedKey("dwitems", "custom_id");
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, configId);

            customItem.setItemMeta(meta);
        }
        return customItem;
    }

    private String formatHex(String message) {
        if (message == null) return "";
        Pattern pattern = Pattern.compile("&#[a-fA-F0-9]{6}");
        Matcher matcher = pattern.matcher(message);
        StringBuilder buffer = new StringBuilder(message.length() + 4 * 8);
        while (matcher.find()) {
            String group = matcher.group();
            matcher.appendReplacement(buffer, ChatColor.of(group.substring(1)).toString());
        }
        matcher.appendTail(buffer);
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }

    private List<String> formatHexList(List<String> list) {
        if (list == null) return null;
        list.replaceAll(this::formatHex);
        return list;
    }
}