package com.aspectxlol.manager;

import com.aspectxlol.Synced;
import com.aspectxlol.model.SyncSettings;
import com.aspectxlol.model.SyncTeam;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Bukkit;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Level;

public class TeamManager {

    private final Synced plugin;
    private final Gson gson;

    // teamId -> SyncTeam
    private final Map<UUID, SyncTeam> teams = new HashMap<>();
    // playerUUID -> teamId
    private final Map<UUID, UUID> playerTeam = new HashMap<>();

    private final File dataFile;

    public TeamManager(Synced plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.dataFile = new File(plugin.getDataFolder(), "teams.json");
    }

    // ─── Team CRUD ──────────────────────────────────────────────────────────────

    public SyncTeam createTeam(UUID leaderUUID, String leaderName) {
        SyncSettings defaults = buildDefaultSettings();
        SyncTeam team = new SyncTeam(UUID.randomUUID(), leaderUUID, defaults);
        team.addMember(leaderUUID, leaderName);
        teams.put(team.getId(), team);
        playerTeam.put(leaderUUID, team.getId());
        saveTeams();
        return team;
    }

    public void disbandTeam(UUID teamId) {
        SyncTeam team = teams.get(teamId);
        if (team == null) return;
        for (UUID member : new HashSet<>(team.getMembers())) {
            playerTeam.remove(member);
        }
        teams.remove(teamId);
        saveTeams();
    }

    public void invitePlayer(UUID teamId, UUID invitee) {
        SyncTeam team = teams.get(teamId);
        if (team == null) return;
        team.getPendingInvites().put(invitee, teamId);
        // Auto-expire invite after 60 seconds
        Bukkit.getScheduler().runTaskLater(plugin, () ->
                team.getPendingInvites().remove(invitee), 20L * 60);
    }

    /**
     * Returns the team the player was invited to, or null if none.
     */
    public SyncTeam getPendingInviteTeam(UUID invitee) {
        for (SyncTeam team : teams.values()) {
            if (team.getPendingInvites().containsKey(invitee)) {
                return team;
            }
        }
        return null;
    }

    public boolean joinTeam(UUID playerUUID, String playerName, UUID teamId) {
        SyncTeam team = teams.get(teamId);
        if (team == null) return false;
        team.addMember(playerUUID, playerName);
        playerTeam.put(playerUUID, teamId);
        team.getPendingInvites().remove(playerUUID);
        saveTeams();
        return true;
    }

    public void leaveTeam(UUID playerUUID) {
        UUID teamId = playerTeam.remove(playerUUID);
        if (teamId == null) return;
        SyncTeam team = teams.get(teamId);
        if (team == null) return;
        team.removeMember(playerUUID);
        // If leader left, assign new leader or disband
        if (team.getLeaderUUID().equals(playerUUID)) {
            Optional<UUID> newLeader = team.getMembers().stream().findFirst();
            if (newLeader.isPresent()) {
                team.setLeaderUUID(newLeader.get());
            } else {
                teams.remove(teamId);
            }
        }
        saveTeams();
    }

    public SyncTeam getTeamOf(UUID playerUUID) {
        UUID teamId = playerTeam.get(playerUUID);
        if (teamId == null) return null;
        return teams.get(teamId);
    }

    public boolean isInTeam(UUID playerUUID) {
        return playerTeam.containsKey(playerUUID);
    }

    /**
     * If the quitting player is team leader, promotes the next online member.
     * If no other member is online the original leaderUUID is preserved so they
     * reclaim leadership automatically when they reconnect (see PlayerJoinEvent).
     * Returns the newly promoted player, or null if no promotion happened.
     */
    public org.bukkit.entity.Player promoteNextOnlineLeader(UUID quittingPlayerUUID) {
        SyncTeam team = getTeamOf(quittingPlayerUUID);
        if (team == null) return null;
        if (!team.getLeaderUUID().equals(quittingPlayerUUID)) return null;

        for (UUID memberId : team.getMembers()) {
            if (memberId.equals(quittingPlayerUUID)) continue;
            org.bukkit.entity.Player candidate = org.bukkit.Bukkit.getPlayer(memberId);
            if (candidate != null && candidate.isOnline()) {
                team.setLeaderUUID(memberId);
                saveTeams();
                return candidate;
            }
        }
        // No online member found — keep original leader UUID, don't touch it
        return null;
    }

    public Collection<SyncTeam> getAllTeams() {
        return Collections.unmodifiableCollection(teams.values());
    }

    // ─── Persistence ────────────────────────────────────────────────────────────

    public void saveTeams() {
        try {
            plugin.getDataFolder().mkdirs();
            Type listType = new TypeToken<List<SyncTeam>>() {}.getType();
            String json = gson.toJson(new ArrayList<>(teams.values()), listType);
            Files.writeString(dataFile.toPath(), json, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save teams.json", e);
        }
    }

    public void loadTeams() {
        if (!dataFile.exists()) return;
        try {
            String json = Files.readString(dataFile.toPath(), StandardCharsets.UTF_8);
            Type listType = new TypeToken<List<SyncTeam>>() {}.getType();
            List<SyncTeam> loaded = gson.fromJson(json, listType);
            if (loaded == null) return;
            for (SyncTeam team : loaded) {
                teams.put(team.getId(), team);
                for (UUID member : team.getMembers()) {
                    playerTeam.put(member, team.getId());
                }
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load teams.json", e);
        }
    }

    // ─── Helpers ────────────────────────────────────────────────────────────────

    private SyncSettings buildDefaultSettings() {
        var cfg = plugin.getConfig();
        return new SyncSettings(
                cfg.getBoolean("defaults.inventory-sync", true),
                cfg.getBoolean("defaults.health-sync", true),
                cfg.getBoolean("defaults.hunger-sync", true),
                cfg.getBoolean("defaults.xp-sync", true),
                cfg.getBoolean("defaults.potion-effects-sync", true),
                cfg.getBoolean("defaults.ender-chest-sync", true),
                cfg.getBoolean("defaults.position-sync", false)
        );
    }
}

