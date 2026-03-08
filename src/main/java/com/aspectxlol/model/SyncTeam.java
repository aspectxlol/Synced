package com.aspectxlol.model;

import java.util.*;

public class SyncTeam {

    private final UUID id;
    private UUID leaderUUID;
    private final Set<UUID> members;
    private final Map<String, UUID> memberNames; // name -> UUID for display
    private SyncSettings settings;

    // Transient: pending invites (not persisted) - invitee UUID -> inviter UUID
    private transient Map<UUID, UUID> pendingInvites;

    public SyncTeam(UUID id, UUID leaderUUID, SyncSettings settings) {
        this.id = id;
        this.leaderUUID = leaderUUID;
        this.members = new HashSet<>();
        this.memberNames = new HashMap<>();
        this.settings = settings;
        this.pendingInvites = new HashMap<>();
        this.members.add(leaderUUID);
    }

    public UUID getId() { return id; }

    public UUID getLeaderUUID() { return leaderUUID; }
    public void setLeaderUUID(UUID leaderUUID) { this.leaderUUID = leaderUUID; }

    public Set<UUID> getMembers() { return members; }

    public Map<String, UUID> getMemberNames() { return memberNames; }

    public SyncSettings getSettings() { return settings; }
    public void setSettings(SyncSettings settings) { this.settings = settings; }

    public Map<UUID, UUID> getPendingInvites() {
        if (pendingInvites == null) pendingInvites = new HashMap<>();
        return pendingInvites;
    }

    public void addMember(UUID uuid, String name) {
        members.add(uuid);
        memberNames.put(name, uuid);
    }

    public void removeMember(UUID uuid) {
        members.remove(uuid);
        memberNames.values().remove(uuid);
    }
}

