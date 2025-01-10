package com.dwitems;

import com.dwitems.commands.DWItemsCommand;
import com.dwitems.listeners.*;
import com.dwitems.managers.ItemManager;
import com.dwitems.managers.MessageManager;
import com.dwitems.managers.TNTManager;
import com.dwitems.utils.Reloadable;
import org.bukkit.Material;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.Recipe;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.Iterator;

public class DWItems extends JavaPlugin implements Reloadable {
    private ItemManager itemManager;
    private MessageManager messageManager;
    private TNTManager tntManager;

    @Override
    public void onEnable() {
        // Сохраняем конфиги
        saveDefaultConfig();
        saveResource("items.yml", false);

        // Инициализируем менеджер предметов
        messageManager = new MessageManager(this);
        itemManager = new ItemManager(this);
        tntManager = new TNTManager(this);

        // Регистрируем команду
        removeVanillaTNTRecipe();
        DWItemsCommand dwiCommand = new DWItemsCommand(this);
        getCommand("dwi").setExecutor(dwiCommand);
        getCommand("dwi").setTabCompleter(dwiCommand);

        // Регистрируем слушатели
        getServer().getPluginManager().registerEvents(new ThermalImagerListener(this), this);
        getServer().getPluginManager().registerEvents(new RandomEggListener(this), this);
        getServer().getPluginManager().registerEvents(new UnbreakableElytraListener(this), this);
        getServer().getPluginManager().registerEvents(new BlindListener(this), this);
        getServer().getPluginManager().registerEvents(new BedrockerListener(this), this);
        getServer().getPluginManager().registerEvents(new BlockerListener(this), this);
        getServer().getPluginManager().registerEvents(new PoisonEggListener(this), this);
        getServer().getPluginManager().registerEvents(new RepairAnvilListener(this), this);
        getServer().getPluginManager().registerEvents(new TeleporterListener(this), this);
        getServer().getPluginManager().registerEvents(new SpawnerPickaxeListener(this), this);
        getServer().getPluginManager().registerEvents(new FeatherAngelListener(this), this);
        getServer().getPluginManager().registerEvents(new FireAuraListener(this), this);
        getServer().getPluginManager().registerEvents(new SmokeJumpListener(this), this);
        getServer().getPluginManager().registerEvents(new HarpoonListener(this), this);
        getServer().getPluginManager().registerEvents(new ShulkerListener(this), this);
        getServer().getPluginManager().registerEvents(new GiantShulkerListener(this), this);
        getServer().getPluginManager().registerEvents(new ScroogeShulkerListener(this), this);
        getServer().getPluginManager().registerEvents(new WeaponSharpenerListener(this), this);
        getServer().getPluginManager().registerEvents(new DwiTNTExplosivesListener(this), this);
        getServer().getPluginManager().registerEvents(new TNTExplosivesListener(this), this);
        getServer().getPluginManager().registerEvents(new DestroyPickaxeListener(this), this);

        getServer().getPluginManager().registerEvents(new DropArrowListener(this), this);
        getServer().getPluginManager().registerEvents(new MoneyArrowListener(this), this);
        getServer().getPluginManager().registerEvents(new ArmorArrowListener(this), this);
        getServer().getPluginManager().registerEvents(new IceArrowListener(this), this);
    

        getLogger().info("Плагин DWItems успешно запущен!");
    }

    @Override
    public void onDisable() {
        getLogger().info("Плагин DWItems выключен!");
    }

    public ItemManager getItemManager() {
        return itemManager;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    @Override
    public void reload() {
        for (RegisteredListener listener : HandlerList.getRegisteredListeners(this)) {
            if (listener.getListener() instanceof Reloadable) {
                ((Reloadable) listener.getListener()).reload();
            }
        }
    }

    private void removeVanillaTNTRecipe() {
        Iterator<Recipe> recipeIterator = getServer().recipeIterator();
        while (recipeIterator.hasNext()) {
            Recipe recipe = recipeIterator.next();
            if (recipe.getResult().getType() == Material.TNT) {
                recipeIterator.remove();
            }
        }
    }

    public TNTManager getTNTManager(){
        return tntManager;
    }
}