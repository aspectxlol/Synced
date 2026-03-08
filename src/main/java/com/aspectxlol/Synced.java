package com.aspectxlol;

import com.aspectxlol.command.SyncedCommand;
import com.aspectxlol.listener.GuiClickListener;
import com.aspectxlol.listener.SyncListener;
import com.aspectxlol.manager.SyncManager;
import com.aspectxlol.manager.TeamManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class Synced extends JavaPlugin {

    private TeamManager teamManager;
    private SyncManager syncManager;

    @Override
    public void onEnable() {
        // Save default config
        saveDefaultConfig();

        // Initialize managers
        teamManager = new TeamManager(this);
        syncManager = new SyncManager();

        // Load persisted team data
        teamManager.loadTeams();

        // Register listeners
        getServer().getPluginManager().registerEvents(new GuiClickListener(teamManager), this);
        getServer().getPluginManager().registerEvents(new SyncListener(teamManager, syncManager), this);

        // Register command
        SyncedCommand syncedCommand = new SyncedCommand(this, teamManager);
        var cmd = getCommand("synced");
        if (cmd != null) {
            cmd.setExecutor(syncedCommand);
            cmd.setTabCompleter(syncedCommand);
        }

        getLogger().info("Synced plugin enabled!");
    }

    @Override
    public void onDisable() {
        // Save team data on shutdown
        if (teamManager != null) {
            teamManager.saveTeams();
        }
        getLogger().info("Synced plugin disabled.");
    }

    public TeamManager getTeamManager() {
        return teamManager;
    }

    public SyncManager getSyncManager() {
        return syncManager;
    }
}
