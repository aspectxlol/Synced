package com.aspectxlol.listener;

import com.aspectxlol.manager.SyncManager;
import com.aspectxlol.manager.TeamManager;
import com.aspectxlol.model.SyncTeam;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.UUID;

public class SyncListener implements Listener {

    private final TeamManager teamManager;
    private final SyncManager syncManager;

    public SyncListener(TeamManager teamManager, SyncManager syncManager) {
        this.teamManager = teamManager;
        this.syncManager = syncManager;
    }

    // ─── Inventory Sync ─────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();
        if (syncManager.getCurrentlySyncing().contains(uuid)) return;

        SyncTeam team = teamManager.getTeamOf(uuid);
        if (team == null) return;

        InventoryType type = event.getInventory().getType();

        if (type == InventoryType.ENDER_CHEST) {
            // Ender chest sync
            if (!team.getSettings().isEnderChestSync()) return;
            syncManager.getCurrentlySyncing().add(uuid);
            try {
                for (UUID memberId : team.getMembers()) {
                    if (memberId.equals(uuid)) continue;
                    Player target = Bukkit.getPlayer(memberId);
                    if (target == null || !target.isOnline()) continue;
                    if (syncManager.getCurrentlySyncing().contains(target.getUniqueId())) continue;
                    syncManager.getCurrentlySyncing().add(target.getUniqueId());
                    try {
                        syncManager.syncEnderChest(player, target);
                    } finally {
                        syncManager.getCurrentlySyncing().remove(target.getUniqueId());
                    }
                }
            } finally {
                syncManager.getCurrentlySyncing().remove(uuid);
            }
            return;
        }

        // Regular inventory sync on close (player inventory, chest, etc.)
        if (!team.getSettings().isInventorySync()) return;
        if (type == InventoryType.PLAYER || type == InventoryType.CRAFTING) {
            syncManager.getCurrentlySyncing().add(uuid);
            try {
                for (UUID memberId : team.getMembers()) {
                    if (memberId.equals(uuid)) continue;
                    Player target = Bukkit.getPlayer(memberId);
                    if (target == null || !target.isOnline()) continue;
                    if (syncManager.getCurrentlySyncing().contains(target.getUniqueId())) continue;
                    syncManager.getCurrentlySyncing().add(target.getUniqueId());
                    try {
                        syncManager.syncInventory(player, target);
                    } finally {
                        syncManager.getCurrentlySyncing().remove(target.getUniqueId());
                    }
                }
            } finally {
                syncManager.getCurrentlySyncing().remove(uuid);
            }
        }
    }

    // ─── Health Sync ────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityRegainHealth(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();
        if (syncManager.getCurrentlySyncing().contains(uuid)) return;

        SyncTeam team = teamManager.getTeamOf(uuid);
        if (team == null || !team.getSettings().isHealthSync()) return;

        double newHealth = Math.min(player.getHealth() + event.getAmount(),
                player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue());

        syncManager.getCurrentlySyncing().add(uuid);
        try {
            for (UUID memberId : team.getMembers()) {
                if (memberId.equals(uuid)) continue;
                Player target = Bukkit.getPlayer(memberId);
                if (target == null || !target.isOnline()) continue;
                if (syncManager.getCurrentlySyncing().contains(target.getUniqueId())) continue;
                syncManager.getCurrentlySyncing().add(target.getUniqueId());
                try {
                    target.setHealth(Math.min(newHealth,
                            target.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue()));
                } finally {
                    syncManager.getCurrentlySyncing().remove(target.getUniqueId());
                }
            }
        } finally {
            syncManager.getCurrentlySyncing().remove(uuid);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();
        if (syncManager.getCurrentlySyncing().contains(uuid)) return;

        SyncTeam team = teamManager.getTeamOf(uuid);
        if (team == null || !team.getSettings().isHealthSync()) return;

        double newHealth = Math.max(0, player.getHealth() - event.getFinalDamage());

        syncManager.getCurrentlySyncing().add(uuid);
        try {
            for (UUID memberId : team.getMembers()) {
                if (memberId.equals(uuid)) continue;
                Player target = Bukkit.getPlayer(memberId);
                if (target == null || !target.isOnline()) continue;
                if (syncManager.getCurrentlySyncing().contains(target.getUniqueId())) continue;
                syncManager.getCurrentlySyncing().add(target.getUniqueId());
                try {
                    double maxHealth = target.getAttribute(
                            org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
                    target.setHealth(Math.min(newHealth, maxHealth));
                } finally {
                    syncManager.getCurrentlySyncing().remove(target.getUniqueId());
                }
            }
        } finally {
            syncManager.getCurrentlySyncing().remove(uuid);
        }
    }

    // ─── Hunger Sync ────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();
        if (syncManager.getCurrentlySyncing().contains(uuid)) return;

        SyncTeam team = teamManager.getTeamOf(uuid);
        if (team == null || !team.getSettings().isHungerSync()) return;

        int newFood = event.getFoodLevel();

        syncManager.getCurrentlySyncing().add(uuid);
        try {
            for (UUID memberId : team.getMembers()) {
                if (memberId.equals(uuid)) continue;
                Player target = Bukkit.getPlayer(memberId);
                if (target == null || !target.isOnline()) continue;
                if (syncManager.getCurrentlySyncing().contains(target.getUniqueId())) continue;
                syncManager.getCurrentlySyncing().add(target.getUniqueId());
                try {
                    target.setFoodLevel(newFood);
                    target.setSaturation(player.getSaturation());
                } finally {
                    syncManager.getCurrentlySyncing().remove(target.getUniqueId());
                }
            }
        } finally {
            syncManager.getCurrentlySyncing().remove(uuid);
        }
    }

    // ─── XP Sync ────────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerExpChange(PlayerExpChangeEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (syncManager.getCurrentlySyncing().contains(uuid)) return;

        SyncTeam team = teamManager.getTeamOf(uuid);
        if (team == null || !team.getSettings().isXpSync()) return;

        int newExp = player.getTotalExperience() + event.getAmount();

        syncManager.getCurrentlySyncing().add(uuid);
        try {
            for (UUID memberId : team.getMembers()) {
                if (memberId.equals(uuid)) continue;
                Player target = Bukkit.getPlayer(memberId);
                if (target == null || !target.isOnline()) continue;
                if (syncManager.getCurrentlySyncing().contains(target.getUniqueId())) continue;
                syncManager.getCurrentlySyncing().add(target.getUniqueId());
                try {
                    target.setTotalExperience(newExp);
                    target.setLevel(player.getLevel());
                    target.setExp(player.getExp());
                } finally {
                    syncManager.getCurrentlySyncing().remove(target.getUniqueId());
                }
            }
        } finally {
            syncManager.getCurrentlySyncing().remove(uuid);
        }
    }

    // ─── Potion Effects Sync ────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityPotionEffect(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();
        if (syncManager.getCurrentlySyncing().contains(uuid)) return;

        SyncTeam team = teamManager.getTeamOf(uuid);
        if (team == null || !team.getSettings().isPotionEffectsSync()) return;

        syncManager.getCurrentlySyncing().add(uuid);
        try {
            for (UUID memberId : team.getMembers()) {
                if (memberId.equals(uuid)) continue;
                Player target = Bukkit.getPlayer(memberId);
                if (target == null || !target.isOnline()) continue;
                if (syncManager.getCurrentlySyncing().contains(target.getUniqueId())) continue;
                syncManager.getCurrentlySyncing().add(target.getUniqueId());
                try {
                    syncManager.syncPotionEffects(player, target);
                } finally {
                    syncManager.getCurrentlySyncing().remove(target.getUniqueId());
                }
            }
        } finally {
            syncManager.getCurrentlySyncing().remove(uuid);
        }
    }

    // ─── Position Sync ──────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (syncManager.getCurrentlySyncing().contains(uuid)) return;

        SyncTeam team = teamManager.getTeamOf(uuid);
        if (team == null || !team.getSettings().isPositionSync()) return;

        var destination = event.getTo();
        if (destination == null) return;

        syncManager.getCurrentlySyncing().add(uuid);
        try {
            for (UUID memberId : team.getMembers()) {
                if (memberId.equals(uuid)) continue;
                Player target = Bukkit.getPlayer(memberId);
                if (target == null || !target.isOnline()) continue;
                if (syncManager.getCurrentlySyncing().contains(target.getUniqueId())) continue;
                syncManager.getCurrentlySyncing().add(target.getUniqueId());
                try {
                    target.teleport(destination);
                } finally {
                    syncManager.getCurrentlySyncing().remove(target.getUniqueId());
                }
            }
        } finally {
            syncManager.getCurrentlySyncing().remove(uuid);
        }
    }
}

