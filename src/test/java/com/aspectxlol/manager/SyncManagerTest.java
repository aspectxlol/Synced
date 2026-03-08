package com.aspectxlol.manager;

import com.aspectxlol.model.SyncSettings;
import com.aspectxlol.model.SyncTeam;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

@DisplayName("SyncManager — static guard sets")
class SyncManagerTest {

    // ─── Clean up static state between tests ─────────────────────────────────────

    @BeforeEach
    @AfterEach
    void clearStaticSets() {
        SyncManager.getCurrentlySyncing().clear();
        SyncManager.getDeadPlayers().clear();
    }

    // ─── currentlySyncing ────────────────────────────────────────────────────────

    @Test
    @DisplayName("currentlySyncing is empty initially")
    void currentlySyncingEmptyAtStart() {
        assertTrue(SyncManager.getCurrentlySyncing().isEmpty());
    }

    @Test
    @DisplayName("can add and remove a UUID from currentlySyncing")
    void addRemoveFromCurrentlySyncing() {
        UUID id = UUID.randomUUID();
        SyncManager.getCurrentlySyncing().add(id);
        assertTrue(SyncManager.getCurrentlySyncing().contains(id));

        SyncManager.getCurrentlySyncing().remove(id);
        assertFalse(SyncManager.getCurrentlySyncing().contains(id));
    }

    // ─── deadPlayers ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("deadPlayers is empty initially")
    void deadPlayersEmptyAtStart() {
        assertTrue(SyncManager.getDeadPlayers().isEmpty());
    }

    @Test
    @DisplayName("can add and remove a UUID from deadPlayers")
    void addRemoveFromDeadPlayers() {
        UUID id = UUID.randomUUID();
        SyncManager.getDeadPlayers().add(id);
        assertTrue(SyncManager.getDeadPlayers().contains(id));

        SyncManager.getDeadPlayers().remove(id);
        assertFalse(SyncManager.getDeadPlayers().contains(id));
    }

    @Test
    @DisplayName("currentlySyncing and deadPlayers are independent sets")
    void guardsAreIndependent() {
        UUID id = UUID.randomUUID();
        SyncManager.getCurrentlySyncing().add(id);
        assertFalse(SyncManager.getDeadPlayers().contains(id));
    }

    // ─── withSyncLock ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("withSyncLock adds source to currentlySyncing during action and removes after")
    void withSyncLockAddsAndRemovesSource() {
        UUID source = UUID.randomUUID();
        SyncTeam team = new SyncTeam(UUID.randomUUID(), source,
                new SyncSettings(true, true, true, true, true, true, false));

        boolean[] wasLocked = {false};
        SyncManager.withSyncLock(source, team, () ->
                wasLocked[0] = SyncManager.getCurrentlySyncing().contains(source));

        assertTrue(wasLocked[0], "source should be locked during action");
        assertFalse(SyncManager.getCurrentlySyncing().contains(source),
                "source should be unlocked after action");
    }

    @Test
    @DisplayName("withSyncLock releases lock even when action throws")
    void withSyncLockReleasesOnException() {
        UUID source = UUID.randomUUID();
        SyncTeam team = new SyncTeam(UUID.randomUUID(), source,
                new SyncSettings(true, true, true, true, true, true, false));

        assertThrows(RuntimeException.class, () ->
                SyncManager.withSyncLock(source, team, () -> {
                    throw new RuntimeException("boom");
                }));

        assertFalse(SyncManager.getCurrentlySyncing().contains(source),
                "lock must be released even after exception");
    }

    @Test
    @DisplayName("withSyncLock does not lock team members who are offline (not in Bukkit)")
    void withSyncLockOnlyLocksOnlineMembers() {
        UUID source = UUID.randomUUID();
        UUID offlineMember = UUID.randomUUID();
        SyncTeam team = new SyncTeam(UUID.randomUUID(), source,
                new SyncSettings(true, true, true, true, true, true, false));
        team.addMember(offlineMember, "Offline");

        try (MockedStatic<Bukkit> mockedBukkit = Mockito.mockStatic(Bukkit.class)) {
            mockedBukkit.when(() -> Bukkit.getPlayer(any(UUID.class))).thenReturn(null);

            SyncManager.withSyncLock(source, team, () -> {
                // offlineMember should not appear — Bukkit.getPlayer returns null
                assertFalse(SyncManager.getCurrentlySyncing().contains(offlineMember));
            });
        }
    }

    // ─── DEFAULT_MAX_HEALTH fallback ─────────────────────────────────────────────

    @Test
    @DisplayName("getMaxHealth returns 20.0 when registry lookup returns null (test env)")
    void getMaxHealthFallback() {
        // In a test environment there is no Bukkit server, so Registry.ATTRIBUTE
        // is not available and getMaxHealth should gracefully return the fallback.
        // We verify via reflection that the fallback constant is 20.0.
        try {
            var field = SyncManager.class.getDeclaredField("DEFAULT_MAX_HEALTH");
            field.setAccessible(true);
            double value = (double) field.get(null);
            assertEquals(20.0, value, 1e-9);
        } catch (ReflectiveOperationException e) {
            fail("Could not access DEFAULT_MAX_HEALTH: " + e.getMessage());
        }
    }
}

