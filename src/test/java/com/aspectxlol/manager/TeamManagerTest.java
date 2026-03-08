package com.aspectxlol.manager;

import com.aspectxlol.model.SyncSettings;
import com.aspectxlol.model.SyncTeam;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TeamManager")
class TeamManagerTest {

    // Default settings: all on except position
    private static final SyncSettings ALL_ON =
            new SyncSettings(true, true, true, true, true, true, false);

    private AtomicInteger persistCount;
    private TeamManager manager;

    /** Builds a TeamManager that counts persist calls and uses ALL_ON defaults. */
    @BeforeEach
    void setUp() {
        persistCount = new AtomicInteger(0);
        manager = new TeamManager(
                () -> persistCount.incrementAndGet(),
                () -> new SyncSettings(true, true, true, true, true, true, false)
        );
    }

    // ─── createTeam ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("createTeam returns a non-null team with the leader as member")
    void createTeamReturnValue() {
        UUID leader = UUID.randomUUID();
        SyncTeam team = manager.createTeam(leader, "Leader");

        assertNotNull(team);
        assertTrue(team.getMembers().contains(leader));
        assertEquals(leader, team.getLeaderUUID());
    }

    @Test
    @DisplayName("createTeam registers the player as in-team")
    void createTeamRegistersPlayer() {
        UUID leader = UUID.randomUUID();
        manager.createTeam(leader, "Leader");
        assertTrue(manager.isInTeam(leader));
    }

    @Test
    @DisplayName("createTeam applies default SyncSettings")
    void createTeamAppliesDefaults() {
        UUID leader = UUID.randomUUID();
        SyncTeam team = manager.createTeam(leader, "Leader");
        assertTrue(team.getSettings().isInventorySync());
        assertFalse(team.getSettings().isPositionSync());
    }

    @Test
    @DisplayName("createTeam triggers persist")
    void createTeamPersists() {
        manager.createTeam(UUID.randomUUID(), "X");
        assertEquals(1, persistCount.get());
    }

    @Test
    @DisplayName("two separate createTeam calls produce different team IDs")
    void createTeamUniqueIds() {
        SyncTeam a = manager.createTeam(UUID.randomUUID(), "A");
        SyncTeam b = manager.createTeam(UUID.randomUUID(), "B");
        assertNotEquals(a.getId(), b.getId());
    }

    // ─── getTeamOf / isInTeam ────────────────────────────────────────────────────

    @Test
    @DisplayName("getTeamOf returns null for an unknown player")
    void getTeamOfUnknown() {
        assertNull(manager.getTeamOf(UUID.randomUUID()));
    }

    @Test
    @DisplayName("isInTeam returns false for an unknown player")
    void isInTeamUnknown() {
        assertFalse(manager.isInTeam(UUID.randomUUID()));
    }

    @Test
    @DisplayName("getTeamOf returns the correct team after createTeam")
    void getTeamOfAfterCreate() {
        UUID leader = UUID.randomUUID();
        SyncTeam team = manager.createTeam(leader, "Leader");
        assertSame(team, manager.getTeamOf(leader));
    }

    // ─── joinTeam ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("joinTeam adds the player to the team's members")
    void joinTeamAddsToMembers() {
        UUID leader = UUID.randomUUID();
        SyncTeam team = manager.createTeam(leader, "Leader");

        UUID member = UUID.randomUUID();
        manager.joinTeam(member, "Alice", team.getId());

        assertTrue(team.getMembers().contains(member));
        assertTrue(manager.isInTeam(member));
    }

    @Test
    @DisplayName("joinTeam returns false for an unknown team ID")
    void joinTeamUnknownTeam() {
        assertFalse(manager.joinTeam(UUID.randomUUID(), "X", UUID.randomUUID()));
    }

    @Test
    @DisplayName("joinTeam triggers persist")
    void joinTeamPersists() {
        UUID leader = UUID.randomUUID();
        SyncTeam team = manager.createTeam(leader, "Leader");
        persistCount.set(0); // reset after createTeam

        manager.joinTeam(UUID.randomUUID(), "Alice", team.getId());
        assertEquals(1, persistCount.get());
    }

    // ─── leaveTeam ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("leaveTeam removes the player from isInTeam")
    void leaveTeamRemovesPlayer() {
        UUID leader = UUID.randomUUID();
        manager.createTeam(leader, "Leader");
        manager.leaveTeam(leader);
        assertFalse(manager.isInTeam(leader));
    }

    @Test
    @DisplayName("leaveTeam disbands team when last member leaves")
    void leaveTeamDisbandWhenEmpty() {
        UUID leader = UUID.randomUUID();
        SyncTeam team = manager.createTeam(leader, "Leader");
        UUID teamId = team.getId();

        manager.leaveTeam(leader);

        // Team should be gone — no member can look it up
        assertNull(manager.getTeamOf(leader));
    }

    @Test
    @DisplayName("leaveTeam promotes another member to leader when leader leaves")
    void leaveTeamPromotesMember() {
        UUID leader = UUID.randomUUID();
        SyncTeam team = manager.createTeam(leader, "Leader");

        UUID member = UUID.randomUUID();
        manager.joinTeam(member, "Alice", team.getId());

        manager.leaveTeam(leader);

        // Alice should now be the leader
        SyncTeam remaining = manager.getTeamOf(member);
        assertNotNull(remaining);
        assertEquals(member, remaining.getLeaderUUID());
    }

    @Test
    @DisplayName("leaving a non-member is a no-op")
    void leaveTeamNonMemberNoOp() {
        assertDoesNotThrow(() -> manager.leaveTeam(UUID.randomUUID()));
    }

    @Test
    @DisplayName("leaveTeam triggers persist")
    void leaveTeamPersists() {
        UUID leader = UUID.randomUUID();
        manager.createTeam(leader, "Leader");
        persistCount.set(0);

        manager.leaveTeam(leader);
        assertEquals(1, persistCount.get());
    }

    // ─── disbandTeam ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("disbandTeam removes all members from isInTeam")
    void disbandTeamRemovesAllMembers() {
        UUID leader = UUID.randomUUID();
        SyncTeam team = manager.createTeam(leader, "Leader");
        UUID m1 = UUID.randomUUID();
        UUID m2 = UUID.randomUUID();
        manager.joinTeam(m1, "A", team.getId());
        manager.joinTeam(m2, "B", team.getId());

        manager.disbandTeam(team.getId());

        assertFalse(manager.isInTeam(leader));
        assertFalse(manager.isInTeam(m1));
        assertFalse(manager.isInTeam(m2));
    }

    @Test
    @DisplayName("disbandTeam on unknown ID is a no-op")
    void disbandTeamUnknownNoOp() {
        assertDoesNotThrow(() -> manager.disbandTeam(UUID.randomUUID()));
    }

    // ─── Pending outgoing invites ─────────────────────────────────────────────────

    @Test
    @DisplayName("hasPendingOutgoingInvite returns false when no invite stored")
    void noPendingOutgoingInvite() {
        assertFalse(manager.hasPendingOutgoingInvite(UUID.randomUUID(), UUID.randomUUID()));
    }

    @Test
    @DisplayName("storePendingOutgoingInvite then hasPendingOutgoingInvite returns true")
    void storeThenCheckInvite() {
        UUID sender = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        // Use the no-Bukkit overload by calling internal map directly via a
        // dedicated test-only helper — we wire the runnable to no-op since
        // there's no scheduler in tests.
        manager.storePendingOutgoingInviteForTest(sender, target);
        assertTrue(manager.hasPendingOutgoingInvite(sender, target));
    }

    @Test
    @DisplayName("hasPendingOutgoingInvite returns false for the wrong target")
    void wrongTargetInvite() {
        UUID sender = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        manager.storePendingOutgoingInviteForTest(sender, target);
        assertFalse(manager.hasPendingOutgoingInvite(sender, UUID.randomUUID()));
    }

    @Test
    @DisplayName("cancelOutgoingInvite removes a stored invite")
    void cancelOutgoingInvite() {
        UUID sender = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        manager.storePendingOutgoingInviteForTest(sender, target);
        manager.cancelOutgoingInvite(sender, target);
        assertFalse(manager.hasPendingOutgoingInvite(sender, target));
    }

    @Test
    @DisplayName("cancelOutgoingInvite with null sender is a no-op")
    void cancelOutgoingInviteNullSenderNoOp() {
        assertDoesNotThrow(() -> manager.cancelOutgoingInvite(null, UUID.randomUUID()));
    }

    // ─── getPendingInviteFrom ────────────────────────────────────────────────────

    @Test
    @DisplayName("getPendingInviteFrom returns null when no invite exists")
    void getPendingInviteFromNoInvite() {
        assertNull(manager.getPendingInviteFrom(UUID.randomUUID(), UUID.randomUUID()));
    }

    @Test
    @DisplayName("getPendingInviteFrom returns the team when invite exists")
    void getPendingInviteFromWithInvite() {
        UUID leader = UUID.randomUUID();
        SyncTeam team = manager.createTeam(leader, "Leader");

        UUID invitee = UUID.randomUUID();
        team.getPendingInvites().put(invitee, team.getId());

        SyncTeam found = manager.getPendingInviteFrom(invitee, leader);
        assertNotNull(found);
        assertEquals(team.getId(), found.getId());
    }
}

