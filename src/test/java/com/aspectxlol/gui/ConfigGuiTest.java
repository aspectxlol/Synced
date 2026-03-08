package com.aspectxlol.gui;

import com.aspectxlol.model.SyncSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ConfigGui — slot layout and toggle helpers")
class ConfigGuiTest {

    private SyncSettings allOn;
    private SyncSettings allOff;

    @BeforeEach
    void setUp() {
        allOn  = new SyncSettings(true,  true,  true,  true,  true,  true,  true);
        allOff = new SyncSettings(false, false, false, false, false, false, false);
    }

    // ─── Slot arrays ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("ICON_SLOTS and TOGGLE_SLOTS have the same length as OPTION_NAMES")
    void slotArrayLengthsMatch() {
        assertEquals(ConfigGui.OPTION_NAMES.length,    ConfigGui.ICON_SLOTS.length);
        assertEquals(ConfigGui.OPTION_NAMES.length,    ConfigGui.TOGGLE_SLOTS.length);
        assertEquals(ConfigGui.ICON_MATERIALS.length,  ConfigGui.OPTION_NAMES.length);
    }

    @Test
    @DisplayName("all icon slots are within a 54-slot chest (0–53)")
    void iconSlotsInBounds() {
        for (int slot : ConfigGui.ICON_SLOTS) {
            assertTrue(slot >= 0 && slot < 54,
                    "Icon slot " + slot + " is out of chest bounds");
        }
    }

    @Test
    @DisplayName("all toggle slots are within a 54-slot chest (0–53)")
    void toggleSlotsInBounds() {
        for (int slot : ConfigGui.TOGGLE_SLOTS) {
            assertTrue(slot >= 0 && slot < 54,
                    "Toggle slot " + slot + " is out of chest bounds");
        }
    }

    @Test
    @DisplayName("no icon slot and toggle slot share the same index position")
    void iconAndToggleSlotsDoNotOverlap() {
        for (int i = 0; i < ConfigGui.ICON_SLOTS.length; i++) {
            assertNotEquals(ConfigGui.ICON_SLOTS[i], ConfigGui.TOGGLE_SLOTS[i],
                    "Icon and toggle share slot at index " + i);
        }
    }

    @Test
    @DisplayName("toggle slots are exactly 9 below their paired icon slots (same column, next row)")
    void toggleSlotsBelowIconSlots() {
        for (int i = 0; i < ConfigGui.ICON_SLOTS.length; i++) {
            assertEquals(ConfigGui.ICON_SLOTS[i] + 9, ConfigGui.TOGGLE_SLOTS[i],
                    "Toggle slot for option " + i + " should be 9 below the icon slot");
        }
    }

    // ─── getValues ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getValues returns all true for allOn settings")
    void getValuesAllOn() {
        boolean[] values = ConfigGui.getValues(allOn);
        for (int i = 0; i < values.length; i++) {
            assertTrue(values[i], "Expected true at index " + i);
        }
    }

    @Test
    @DisplayName("getValues returns all false for allOff settings")
    void getValuesAllOff() {
        boolean[] values = ConfigGui.getValues(allOff);
        for (int i = 0; i < values.length; i++) {
            assertFalse(values[i], "Expected false at index " + i);
        }
    }

    @Test
    @DisplayName("getValues returns an array with length equal to number of options")
    void getValuesLength() {
        assertEquals(ConfigGui.OPTION_NAMES.length, ConfigGui.getValues(allOn).length);
    }

    @Test
    @DisplayName("getValues reflects the correct order: inventory, health, hunger, xp, potions, ender, position")
    void getValuesOrder() {
        // Only position sync is off; all others are on
        SyncSettings mixed = new SyncSettings(true, true, true, true, true, true, false);
        boolean[] values = ConfigGui.getValues(mixed);

        assertTrue(values[0],  "index 0 = inventory sync");
        assertTrue(values[1],  "index 1 = health sync");
        assertTrue(values[2],  "index 2 = hunger sync");
        assertTrue(values[3],  "index 3 = xp sync");
        assertTrue(values[4],  "index 4 = potion effects sync");
        assertTrue(values[5],  "index 5 = ender chest sync");
        assertFalse(values[6], "index 6 = position sync (off)");
    }

    // ─── setByIndex ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("setByIndex(0, true) enables inventory sync")
    void setByIndex0() {
        ConfigGui.setByIndex(allOff, 0, true);
        assertTrue(allOff.isInventorySync());
    }

    @Test
    @DisplayName("setByIndex(1, true) enables health sync")
    void setByIndex1() {
        ConfigGui.setByIndex(allOff, 1, true);
        assertTrue(allOff.isHealthSync());
    }

    @Test
    @DisplayName("setByIndex(2, true) enables hunger sync")
    void setByIndex2() {
        ConfigGui.setByIndex(allOff, 2, true);
        assertTrue(allOff.isHungerSync());
    }

    @Test
    @DisplayName("setByIndex(3, true) enables xp sync")
    void setByIndex3() {
        ConfigGui.setByIndex(allOff, 3, true);
        assertTrue(allOff.isXpSync());
    }

    @Test
    @DisplayName("setByIndex(4, true) enables potion effects sync")
    void setByIndex4() {
        ConfigGui.setByIndex(allOff, 4, true);
        assertTrue(allOff.isPotionEffectsSync());
    }

    @Test
    @DisplayName("setByIndex(5, true) enables ender chest sync")
    void setByIndex5() {
        ConfigGui.setByIndex(allOff, 5, true);
        assertTrue(allOff.isEnderChestSync());
    }

    @Test
    @DisplayName("setByIndex(6, true) enables position sync")
    void setByIndex6() {
        ConfigGui.setByIndex(allOff, 6, true);
        assertTrue(allOff.isPositionSync());
    }

    @Test
    @DisplayName("setByIndex then getValues round-trips correctly for every option")
    void setByIndexRoundTrip() {
        SyncSettings settings = new SyncSettings(false, false, false, false, false, false, false);
        for (int i = 0; i < ConfigGui.OPTION_NAMES.length; i++) {
            ConfigGui.setByIndex(settings, i, true);
            boolean[] values = ConfigGui.getValues(settings);
            assertTrue(values[i], "Round-trip failed at index " + i);
            // Reset for next iteration
            ConfigGui.setByIndex(settings, i, false);
        }
    }

    @Test
    @DisplayName("setByIndex does not affect other flags")
    void setByIndexIsolated() {
        ConfigGui.setByIndex(allOff, 3, true); // XP only
        assertFalse(allOff.isInventorySync());
        assertFalse(allOff.isHealthSync());
        assertTrue(allOff.isXpSync());
        assertFalse(allOff.isPotionEffectsSync());
    }
}

