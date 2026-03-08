package com.aspectxlol.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SyncTeam")
class SyncTeamTest {

    private UUID leaderId;
    private SyncSettings settings;
    private SyncTeam team;

    @BeforeEach
    void setUp() {
        leaderId = UUID.randomUUID();
        settings = new SyncSettings(true, true, true, true, true, true, false);
        team = new SyncTeam(UUID.randomUUID(), leaderId, settings);
    }

    // ─── Construction ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("constructor adds leader to members automatically")
    void constructorAddsLeaderToMembers() {
        assertTrue(team.getMembers().contains(leaderId));
        assertEquals(1, team.getMembers().size());
    }

    @Test
    @DisplayName("constructor stores the provided leader UUID")
    void constructorStoresLeaderUUID() {
        assertEquals(leaderId, team.getLeaderUUID());
    }

    @Test
    @DisplayName("constructor stores the provided SyncSettings")
    void constructorStoresSettings() {
        assertSame(settings, team.getSettings());
    }

    @Test
    @DisplayName("getId returns a non-null UUID")
    void idIsNonNull() {
        assertNotNull(team.getId());
    }

    // ─── addMember ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("addMember adds UUID to members set")
    void addMemberAddsTOMembersSet() {
        UUID newMember = UUID.randomUUID();
        team.addMember(newMember, "Alice");
        assertTrue(team.getMembers().contains(newMember));
        assertEquals(2, team.getMembers().size());
    }

    @Test
    @DisplayName("addMember registers name in memberNames map")
    void addMemberRegistersName() {
        UUID newMember = UUID.randomUUID();
        team.addMember(newMember, "Alice");
        assertTrue(team.getMemberNames().containsKey("Alice"));
        assertEquals(newMember, team.getMemberNames().get("Alice"));
    }

    @Test
    @DisplayName("adding the same UUID twice does not duplicate it in members")
    void addMemberIdempotent() {
        team.addMember(leaderId, "Leader");
        assertEquals(1, team.getMembers().size());
    }

    // ─── removeMember ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("removeMember removes UUID from members set")
    void removeMemberRemovesFromSet() {
        UUID member = UUID.randomUUID();
        team.addMember(member, "Bob");
        team.removeMember(member);
        assertFalse(team.getMembers().contains(member));
    }

    @Test
    @DisplayName("removeMember removes the name from memberNames")
    void removeMemberRemovesName() {
        UUID member = UUID.randomUUID();
        team.addMember(member, "Bob");
        team.removeMember(member);
        assertFalse(team.getMemberNames().containsValue(member));
    }

    @Test
    @DisplayName("removing a non-existent member is a no-op")
    void removeNonExistentMemberIsNoOp() {
        int before = team.getMembers().size();
        team.removeMember(UUID.randomUUID());
        assertEquals(before, team.getMembers().size());
    }

    // ─── pendingInvites ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("getPendingInvites returns non-null map on fresh team")
    void pendingInvitesNonNull() {
        assertNotNull(team.getPendingInvites());
    }

    @Test
    @DisplayName("pending invite can be stored and retrieved")
    void storePendingInvite() {
        UUID invitee = UUID.randomUUID();
        UUID teamId  = team.getId();
        team.getPendingInvites().put(invitee, teamId);
        assertTrue(team.getPendingInvites().containsKey(invitee));
    }

    // ─── setLeaderUUID ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("setLeaderUUID changes the stored leader")
    void setLeaderUUID() {
        UUID newLeader = UUID.randomUUID();
        team.addMember(newLeader, "NewLeader");
        team.setLeaderUUID(newLeader);
        assertEquals(newLeader, team.getLeaderUUID());
    }

    // ─── setSettings ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("setSettings replaces the stored SyncSettings object")
    void setSettings() {
        SyncSettings newSettings = new SyncSettings(false, false, false, false, false, false, false);
        team.setSettings(newSettings);
        assertSame(newSettings, team.getSettings());
    }
}

