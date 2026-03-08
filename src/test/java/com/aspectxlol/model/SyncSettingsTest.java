package com.aspectxlol.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SyncSettings")
class SyncSettingsTest {

    private SyncSettings allOn;
    private SyncSettings allOff;

    @BeforeEach
    void setUp() {
        allOn  = new SyncSettings(true, true, true, true, true, true, true);
        allOff = new SyncSettings(false, false, false, false, false, false, false);
    }

    // ─── Constructor ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("all-on constructor stores true for every flag")
    void allOnConstructor() {
        assertTrue(allOn.isInventorySync());
        assertTrue(allOn.isHealthSync());
        assertTrue(allOn.isHungerSync());
        assertTrue(allOn.isXpSync());
        assertTrue(allOn.isPotionEffectsSync());
        assertTrue(allOn.isEnderChestSync());
        assertTrue(allOn.isPositionSync());
    }

    @Test
    @DisplayName("all-off constructor stores false for every flag")
    void allOffConstructor() {
        assertFalse(allOff.isInventorySync());
        assertFalse(allOff.isHealthSync());
        assertFalse(allOff.isHungerSync());
        assertFalse(allOff.isXpSync());
        assertFalse(allOff.isPotionEffectsSync());
        assertFalse(allOff.isEnderChestSync());
        assertFalse(allOff.isPositionSync());
    }

    // ─── Setters ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("setInventorySync toggles from true to false and back")
    void setInventorySync() {
        allOn.setInventorySync(false);
        assertFalse(allOn.isInventorySync());
        allOn.setInventorySync(true);
        assertTrue(allOn.isInventorySync());
    }

    @Test
    @DisplayName("setHealthSync toggles independently")
    void setHealthSync() {
        allOff.setHealthSync(true);
        assertTrue(allOff.isHealthSync());
        // other flags unaffected
        assertFalse(allOff.isInventorySync());
        assertFalse(allOff.isXpSync());
    }

    @Test
    @DisplayName("setHungerSync toggles independently")
    void setHungerSync() {
        allOff.setHungerSync(true);
        assertTrue(allOff.isHungerSync());
    }

    @Test
    @DisplayName("setXpSync toggles independently")
    void setXpSync() {
        allOff.setXpSync(true);
        assertTrue(allOff.isXpSync());
    }

    @Test
    @DisplayName("setPotionEffectsSync toggles independently")
    void setPotionEffectsSync() {
        allOff.setPotionEffectsSync(true);
        assertTrue(allOff.isPotionEffectsSync());
    }

    @Test
    @DisplayName("setEnderChestSync toggles independently")
    void setEnderChestSync() {
        allOff.setEnderChestSync(true);
        assertTrue(allOff.isEnderChestSync());
    }

    @Test
    @DisplayName("setPositionSync toggles independently")
    void setPositionSync() {
        allOff.setPositionSync(true);
        assertTrue(allOff.isPositionSync());
    }

    // ─── Default config values ───────────────────────────────────────────────────

    @Test
    @DisplayName("position sync defaults to false by convention")
    void positionSyncDefaultIsFalse() {
        // Mirrors the config default: position-sync: false
        SyncSettings defaults = new SyncSettings(true, true, true, true, true, true, false);
        assertFalse(defaults.isPositionSync());
        assertTrue(defaults.isInventorySync());
    }
}

