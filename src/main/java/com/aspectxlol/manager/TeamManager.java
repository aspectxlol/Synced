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
import java.util.function.Supplier;
import java.util.logging.Level;

public class TeamManager {

    private final Synced plugin;
    private final Gson gson;

    // teamId -> SyncTeam
    private final Map<UUID, SyncTeam> teams = new HashMap<>();
    // playerUUID -> teamId
    private final Map<UUID, UUID> playerTeam = new HashMap<>();
    // Outgoing invite requests (player-to-player, no team yet): senderUUID -> targetUUID
    // Used for the mutual /sync join <player> flow.
    private final Map<UUID, UUID> outgoingInvites = new HashMap<>();

    private final File dataFile;

    /** Callback invoked after any mutating operation — persists state to disk. */
    private final Runnable persistCallback;

    /** Supplies the default SyncSettings for newly created teams. */
    private final Supplier<SyncSettings> defaultSettingsSupplier;

    // ─── Constructors ────────────────────────────────────────────────────────────

    /** Production constructor — wired to a live Bukkit plugin. */
    public TeamManager(Synced plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.dataFile = new File(plugin.getDataFolder(), "teams.json");
        this.persistCallback = this::saveTeams;
        this.defaultSettingsSupplier = () -> {
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
        };
    }

    /**
     * Test constructor — no Bukkit dependency.
     *
     * @param persistCallback      called after every mutation; pass {@code () -> {}} to skip I/O
     * @param defaultSettingsSupplier  produces default settings for new teams
     */
    public TeamManager(Runnable persistCallback, Supplier<SyncSettings> defaultSettingsSupplier) {
        this.plugin = null;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.dataFile = null;
        this.persistCallback = persistCallback;
        this.defaultSettingsSupplier = defaultSettingsSupplier;
    }

    // ─── Team CRUD ──────────────────────────────────────────────────────────────

    public SyncTeam createTeam(UUID leaderUUID, String leaderName) {
        SyncSettings defaults = defaultSettingsSupplier.get();
        SyncTeam team = new SyncTeam(UUID.randomUUID(), leaderUUID, defaults);
        team.addMember(leaderUUID, leaderName);
        teams.put(team.getId(), team);
        playerTeam.put(leaderUUID, team.getId());
        persistCallback.run();
        return team;
    }

    public void disbandTeam(UUID teamId) {
        SyncTeam team = teams.get(teamId);
        if (team == null) return;
        for (UUID member : new HashSet<>(team.getMembers())) {
            playerTeam.remove(member);
        }
        teams.remove(teamId);
        persistCallback.run();
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
        persistCallback.run();
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
        persistCallback.run();
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
     * Stores a player-to-player outgoing sync request (before any team exists).
     * Auto-expires after 60 seconds.
     */
    public void storePendingOutgoingInvite(UUID sender, UUID target, org.bukkit.plugin.Plugin plugin) {
        outgoingInvites.put(sender, target);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            UUID current = outgoingInvites.get(sender);
            if (target.equals(current)) outgoingInvites.remove(sender);
        }, 20L * 60);
    }

    /** Returns true if {@code sender} has an outgoing invite pointing at {@code target}. */
    public boolean hasPendingOutgoingInvite(UUID sender, UUID target) {
        return target.equals(outgoingInvites.get(sender));
    }

    /**
     * Test-only variant of {@link #storePendingOutgoingInvite} that does not
     * schedule a Bukkit auto-expiry task. Use this in unit tests only.
     */
    public void storePendingOutgoingInviteForTest(UUID sender, UUID target) {
        outgoingInvites.put(sender, target);
    }

    /** Removes a pending outgoing invite from sender→target (e.g. on decline). */
    public void cancelOutgoingInvite(UUID sender, UUID target) {
        if (sender == null) return;
        if (target.equals(outgoingInvites.get(sender))) outgoingInvites.remove(sender);
    }

    /**
     * Returns the team that {@code inviter} invited {@code invitee} to,
     * or null if no such invite exists on any team.
     */
    public SyncTeam getPendingInviteFrom(UUID invitee, UUID inviter) {
        for (SyncTeam team : teams.values()) {
            // The invite map on SyncTeam stores invitee→teamId, issued by the team leader.
            // We also need to verify the inviter is in that team.
            if (team.getPendingInvites().containsKey(invitee) && team.getMembers().contains(inviter)) {
                return team;
            }
        }
        return null;
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
                persistCallback.run();
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

}

