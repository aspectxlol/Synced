package com.aspectxlol.listener;

import com.aspectxlol.gui.ConfigGui;
import com.aspectxlol.manager.TeamManager;
import com.aspectxlol.model.SyncSettings;
import com.aspectxlol.model.SyncTeam;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;


public class GuiClickListener implements Listener {

    private final TeamManager teamManager;

    public GuiClickListener(TeamManager teamManager) {
        this.teamManager = teamManager;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Check if this is the Synced GUI
        String title = PlainTextComponentSerializer.plainText()
                .serialize(event.getView().title());
        if (!title.equals(ConfigGui.GUI_TITLE)) return;

        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;

        // Check if the clicked slot is a toggle slot
        int toggleIndex = -1;
        for (int i = 0; i < ConfigGui.TOGGLE_SLOTS.length; i++) {
            if (ConfigGui.TOGGLE_SLOTS[i] == slot) {
                toggleIndex = i;
                break;
            }
        }
        if (toggleIndex == -1) return;

        // Get the player's team settings
        SyncTeam team = teamManager.getTeamOf(player.getUniqueId());
        if (team == null) return;

        SyncSettings settings = team.getSettings();
        boolean[] values = ConfigGui.getValues(settings);
        boolean newValue = !values[toggleIndex];
        ConfigGui.setByIndex(settings, toggleIndex, newValue);

        // Update the toggle dye in the inventory in real time
        event.getInventory().setItem(slot, ConfigGui.buildToggleDye(toggleIndex, newValue));

        teamManager.saveTeams();
    }
}

