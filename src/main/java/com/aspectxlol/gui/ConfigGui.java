package com.aspectxlol.gui;

import com.aspectxlol.model.SyncSettings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class ConfigGui {

    public static final String GUI_TITLE = "Synced - Configuration";

    // Slot layout in a 54-slot chest (6 rows):
    // Row 1: label items (icons)
    // Row 2: toggle items (dyes)
    // We use two rows per toggle pair, one slot each, in a 9-wide chest.
    // Layout: slot 0-6 = icon items, slot 9-15 = toggle dyes

    public static final int[] ICON_SLOTS  = {10, 12, 14, 16, 20, 22, 24};
    public static final int[] TOGGLE_SLOTS = {11, 13, 15, 17, 21, 23, 25};

    public static final String[] OPTION_NAMES = {
        "Inventory Sync",
        "Health Sync",
        "Hunger Sync",
        "XP Sync",
        "Potion Effects Sync",
        "Ender Chest Sync",
        "Position Sync"
    };

    public static final Material[] ICON_MATERIALS = {
        Material.COMPASS,
        Material.RED_DYE,
        Material.COOKED_BEEF,
        Material.EXPERIENCE_BOTTLE,
        Material.POTION,
        Material.ENDER_CHEST,
        Material.ENDER_PEARL
    };

    public static Inventory build(SyncSettings settings) {
        Inventory inv = Bukkit.createInventory(null, 54,
                Component.text(GUI_TITLE, NamedTextColor.DARK_AQUA).decoration(TextDecoration.ITALIC, false));

        boolean[] values = getValues(settings);

        for (int i = 0; i < OPTION_NAMES.length; i++) {
            // Icon item
            ItemStack icon = new ItemStack(ICON_MATERIALS[i]);
            ItemMeta iconMeta = icon.getItemMeta();
            iconMeta.displayName(Component.text(OPTION_NAMES[i], NamedTextColor.WHITE)
                    .decoration(TextDecoration.ITALIC, false));
            icon.setItemMeta(iconMeta);
            inv.setItem(ICON_SLOTS[i], icon);

            // Toggle dye
            inv.setItem(TOGGLE_SLOTS[i], buildToggleDye(i, values[i]));
        }

        // Fill empty slots with gray glass panes
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.displayName(Component.empty());
        filler.setItemMeta(fillerMeta);
        for (int i = 0; i < 54; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, filler);
            }
        }

        return inv;
    }

    public static ItemStack buildToggleDye(int optionIndex, boolean enabled) {
        Material mat = enabled ? Material.GREEN_DYE : Material.RED_DYE;
        ItemStack dye = new ItemStack(mat);
        ItemMeta meta = dye.getItemMeta();
        meta.displayName(Component.text(
                enabled ? "✔ Enabled" : "✘ Disabled",
                enabled ? NamedTextColor.GREEN : NamedTextColor.RED
        ).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Click to toggle " + OPTION_NAMES[optionIndex], NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        dye.setItemMeta(meta);
        return dye;
    }

    public static boolean[] getValues(SyncSettings s) {
        return new boolean[]{
            s.isInventorySync(),
            s.isHealthSync(),
            s.isHungerSync(),
            s.isXpSync(),
            s.isPotionEffectsSync(),
            s.isEnderChestSync(),
            s.isPositionSync()
        };
    }

    public static void setByIndex(SyncSettings s, int index, boolean value) {
        switch (index) {
            case 0 -> s.setInventorySync(value);
            case 1 -> s.setHealthSync(value);
            case 2 -> s.setHungerSync(value);
            case 3 -> s.setXpSync(value);
            case 4 -> s.setPotionEffectsSync(value);
            case 5 -> s.setEnderChestSync(value);
            case 6 -> s.setPositionSync(value);
        }
    }
}

