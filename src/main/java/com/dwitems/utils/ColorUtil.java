package com.dwitems.utils;

import net.md_5.bungee.api.ChatColor;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ColorUtil {
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    public static String colorize(String message) {
        if (message == null) return null;

        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuffer buffer = new StringBuffer();

        while (matcher.find()) {
            String color = matcher.group(1);
            matcher.appendReplacement(buffer, ChatColor.of("#" + color).toString());
        }

        matcher.appendTail(buffer);
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }

    public static List<String> colorize(List<String> lines) {
        return lines.stream()
                .map(ColorUtil::colorize)
                .collect(Collectors.toList());
    }

    public static String stripColor(String text) {
        if (text == null) return null;

        // Удаляем hex цвета
        text = HEX_PATTERN.matcher(text).replaceAll("");

        // Удаляем стандартные цветовые коды
        return ChatColor.stripColor(text);
    }
}