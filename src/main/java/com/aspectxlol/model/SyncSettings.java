package com.aspectxlol.model;

public class SyncSettings {

    private boolean inventorySync;
    private boolean healthSync;
    private boolean hungerSync;
    private boolean xpSync;
    private boolean potionEffectsSync;
    private boolean enderChestSync;
    private boolean positionSync;

    public SyncSettings(boolean inventorySync, boolean healthSync, boolean hungerSync,
                        boolean xpSync, boolean potionEffectsSync, boolean enderChestSync,
                        boolean positionSync) {
        this.inventorySync = inventorySync;
        this.healthSync = healthSync;
        this.hungerSync = hungerSync;
        this.xpSync = xpSync;
        this.potionEffectsSync = potionEffectsSync;
        this.enderChestSync = enderChestSync;
        this.positionSync = positionSync;
    }

    public boolean isInventorySync() { return inventorySync; }
    public void setInventorySync(boolean inventorySync) { this.inventorySync = inventorySync; }

    public boolean isHealthSync() { return healthSync; }
    public void setHealthSync(boolean healthSync) { this.healthSync = healthSync; }

    public boolean isHungerSync() { return hungerSync; }
    public void setHungerSync(boolean hungerSync) { this.hungerSync = hungerSync; }

    public boolean isXpSync() { return xpSync; }
    public void setXpSync(boolean xpSync) { this.xpSync = xpSync; }

    public boolean isPotionEffectsSync() { return potionEffectsSync; }
    public void setPotionEffectsSync(boolean potionEffectsSync) { this.potionEffectsSync = potionEffectsSync; }

    public boolean isEnderChestSync() { return enderChestSync; }
    public void setEnderChestSync(boolean enderChestSync) { this.enderChestSync = enderChestSync; }

    public boolean isPositionSync() { return positionSync; }
    public void setPositionSync(boolean positionSync) { this.positionSync = positionSync; }
}

