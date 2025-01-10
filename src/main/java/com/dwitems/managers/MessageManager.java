package com.dwitems.managers;

import com.dwitems.DWItems;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageManager {
    private final DWItems plugin;
    private FileConfiguration langConfig;
    private final Pattern hexPattern = Pattern.compile("&#[a-fA-F0-9]{6}");

    public MessageManager(DWItems plugin) {
        this.plugin = plugin;
        loadMessages();
    }

    private void loadMessages() {
        File langFile = new File(plugin.getDataFolder(), "lang.yml");
        if (!langFile.exists()) {
            plugin.saveResource("lang.yml", false);
        }
        langConfig = YamlConfiguration.loadConfiguration(langFile);
    }

    public String getMessage(String path) {
        String message = langConfig.getString(path);
        if (message == null) {
            return "Message not found: " + path;
        }
        return formatMessage(message);
    }

    public String getMessage(String path, String... placeholders) {
        String message = getMessage(path);

        // Заменяем {prefix} на значение из конфига
        if (message.contains("{prefix}")) {
            message = message.replace("{prefix}", getMessage("messages.prefix"));
        }

        // Заменяем остальные плейсхолдеры
        for (int i = 0; i < placeholders.length; i += 2) {
            if (i + 1 < placeholders.length) {
                message = message.replace(placeholders[i], placeholders[i + 1]);
            }
        }

        return formatMessage(message);
    }

    private String formatMessage(String message) {
        if (message == null) return "";

        // Форматируем HEX цвета
        Matcher matcher = hexPattern.matcher(message);
        StringBuilder buffer = new StringBuilder(message.length() + 4 * 8);
        while (matcher.find()) {
            String group = matcher.group();
            matcher.appendReplacement(buffer, net.md_5.bungee.api.ChatColor.of(group.substring(1)).toString());
        }
        matcher.appendTail(buffer);

        // Форматируем стандартные цвета
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }

    public void reloadMessages() {
        loadMessages();
    }
}