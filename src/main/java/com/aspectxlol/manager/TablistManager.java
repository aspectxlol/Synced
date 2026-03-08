package com.aspectxlol.manager;

import com.aspectxlol.Synced;
import com.aspectxlol.model.SyncTeam;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

import java.util.UUID;

/**
 * Manages scoreboard teams so players in a sync team appear in AQUA
 * in the tab list, while players with no team appear in GRAY.
 */
public class TablistManager {

    private static final String TEAM_NAME   = "synced_in";
    private static final String NOTEAM_NAME = "synced_out";

    private final Synced plugin;
    private final TeamManager teamManager;
    private final Scoreboard board;
    private final Team inTeam;
    private final Team noTeam;

    public TablistManager(Synced plugin, TeamManager teamManager) {
        this.plugin = plugin;
        this.teamManager = teamManager;

        ScoreboardManager sbm = Bukkit.getScoreboardManager();
        this.board = sbm.getNewScoreboard();

        // "in-team" scoreboard team — aqua prefix
        this.inTeam = getOrCreate(TEAM_NAME);
        inTeam.prefix(Component.text("", NamedTextColor.AQUA));
        inTeam.color(NamedTextColor.AQUA);

        // "no-team" scoreboard team — gray prefix
        this.noTeam = getOrCreate(NOTEAM_NAME);
        noTeam.prefix(Component.text("", NamedTextColor.GRAY));
        noTeam.color(NamedTextColor.GRAY);
    }

    // ─── Public API ──────────────────────────────────────────────────────────────

    /**
     * Assigns the scoreboard to every online player so they all see the colours.
     * Call once on enable after loading teams.
     */
    public void applyToAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setScoreboard(board);
            refresh(p);
        }
    }

    /** Call when a player joins the server. */
    public void onJoin(Player player) {
        player.setScoreboard(board);
        refresh(player);
    }

    /** Call when a player leaves the server. */
    public void onQuit(Player player) {
        inTeam.removeEntry(player.getName());
        noTeam.removeEntry(player.getName());
    }

    /**
     * Refreshes a single player's tab colour based on their current team status.
     * Call whenever team membership changes (join team, leave, disband, etc.).
     */
    public void refresh(Player player) {
        String name = player.getName();
        inTeam.removeEntry(name);
        noTeam.removeEntry(name);

        if (teamManager.isInTeam(player.getUniqueId())) {
            inTeam.addEntry(name);
        } else {
            noTeam.addEntry(name);
        }
    }

    /**
     * Refreshes every online member of a team (e.g. after someone joins/leaves it).
     */
    public void refreshTeam(SyncTeam team) {
        for (UUID memberId : team.getMembers()) {
            Player p = Bukkit.getPlayer(memberId);
            if (p != null && p.isOnline()) refresh(p);
        }
    }

    /**
     * Refreshes every online player (e.g. after a disband affects multiple people).
     */
    public void refreshAll() {
        for (Player p : Bukkit.getOnlinePlayers()) refresh(p);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    private Team getOrCreate(String name) {
        Team existing = board.getTeam(name);
        return existing != null ? existing : board.registerNewTeam(name);
    }
}

