package com.dwitems.commands;

import com.dwitems.DWItems;
import com.dwitems.utils.ColorUtil;
import com.dwitems.utils.Reloadable;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class DWItemsCommand implements CommandExecutor, TabCompleter {
    private final DWItems plugin;

    public DWItemsCommand(DWItems plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(plugin.getMessageManager().getMessage("messages.commands.usage"));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "give":
                handleGiveCommand(sender, args);
                break;
            case "reload":
                handleReloadCommand(sender);
                break;
            default:
                sender.sendMessage(plugin.getMessageManager().getMessage("messages.commands.unknown-subcommand"));
                break;
        }

        return true;
    }

    private void handleGiveCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.getMessageManager().getMessage("messages.commands.usage"));
            return;
        }

        if (!sender.hasPermission("dwitems.give")) {
            sender.sendMessage(plugin.getMessageManager().getMessage("messages.no-permission"));
            return;
        }

        String itemId = args[1];
        Player target;
        int amount = 1;

        // Определяем целевого игрока
        if (args.length >= 3) {
            target = Bukkit.getPlayer(args[2]);
            if (target == null) {
                sender.sendMessage(plugin.getMessageManager().getMessage("messages.player-not-found",
                        "{player}", args[2]));
                return;
            }
        } else {
            if (!(sender instanceof Player)) {
                sender.sendMessage(plugin.getMessageManager().getMessage("messages.commands.specify-player"));
                return;
            }
            target = (Player) sender;
        }

        // Определяем количество
        if (args.length >= 4) {
            try {
                amount = Integer.parseInt(args[3]);
                if (amount <= 0 || amount > 64) {
                    sender.sendMessage(plugin.getMessageManager().getMessage("messages.commands.invalid-amount"));
                    return;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(plugin.getMessageManager().getMessage("messages.commands.invalid-number"));
                return;
            }
        }

        // Получаем предмет
        ItemStack item = plugin.getItemManager().getItem(itemId);
        if (item == null) {
            sender.sendMessage(plugin.getMessageManager().getMessage("messages.item-not-found",
                    "{item}", itemId));
            return;
        }

        // Выдаём предметы
        item.setAmount(amount);
        target.getInventory().addItem(item);

        // Отправляем сообщения
        if (sender != target) {
            sender.sendMessage(plugin.getMessageManager().getMessage("messages.item-given",
                    "{amount}", String.valueOf(amount),
                    "{item}", itemId,
                    "{player}", target.getName()));
        }
    }

    private void handleReloadCommand(CommandSender sender) {
        if (!sender.hasPermission("dwitems.reload")) {
            sender.sendMessage(ColorUtil.colorize("&cУ вас нет прав для использования этой команды!"));
            return;
        }

        try {
            // Перезагружаем основной конфиг
            plugin.reloadConfig();

            // Пересоздаем файл items.yml если он не существует
            File itemsFile = new File(plugin.getDataFolder(), "items.yml");
            if (!itemsFile.exists()) {
                plugin.saveResource("items.yml", false);
            }

            // Перезагружаем все менеджеры
            plugin.getItemManager().reloadItems();
            plugin.getMessageManager().reloadMessages();

            // Перезагружаем все слушатели через основной класс плагина
            if (plugin instanceof Reloadable) {
                ((Reloadable) plugin).reload();
            }

            sender.sendMessage(ColorUtil.colorize("&aПлагин успешно перезагружен!"));
        } catch (Exception e) {
            sender.sendMessage(ColorUtil.colorize("&cОшибка при перезагрузке плагина: " + e.getMessage()));
            plugin.getLogger().severe("Ошибка при перезагрузке плагина:");
            e.printStackTrace();
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("give");
            if (sender.hasPermission("dwitems.reload")) {
                completions.add("reload");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            completions.addAll(plugin.getItemManager().getItemIds());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .collect(Collectors.toList());
        }

        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                .collect(Collectors.toList());
    }
}