package com.dwitems.utils;

import org.bukkit.Color;

public class ColorConverter {
    public static Color hexToColor(String hex) {
        try {
            hex = hex.replace("#", "");
            int r = Integer.parseInt(hex.substring(0, 2), 16);
            int g = Integer.parseInt(hex.substring(2, 4), 16);
            int b = Integer.parseInt(hex.substring(4, 6), 16);
            return Color.fromRGB(r, g, b);
        } catch (Exception e) {
            return Color.WHITE; // Цвет по умолчанию в случае ошибки
        }
    }
}