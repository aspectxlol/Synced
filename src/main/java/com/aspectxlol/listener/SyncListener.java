package com.aspectxlol.listener;

import com.aspectxlol.Synced;
import com.aspectxlol.manager.SyncManager;
import com.aspectxlol.manager.TeamManager;import com.aspectxlol.model.SyncTeam;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;

import java.util.UUID;

public class SyncListener implements Listener {

    private final Synced plugin;
    private final TeamManager teamManager;
    private final SyncManager syncManager;

    public SyncListener(Synced plugin, TeamManager teamManager, SyncManager syncManager) {
        this.plugin = plugin;
        this.teamManager = teamManager;
        this.syncManager = syncManager;
    }

    // ─── Convenience: broadcast to all online teammates ──────────────────────────

    /**
     * Runs {@code action} for every online teammate of {@code source},
     * wrapped in the full sync-lock so neither the source nor any target
     * can re-trigger sync during the operation.
     */
    private void broadcastSync(Player source, SyncTeam team, java.util.function.Consumer<Player> action) {
        UUID srcId = source.getUniqueId();
        SyncManager.withSyncLock(srcId, team, () -> {
            for (UUID memberId : team.getMembers()) {
                if (memberId.equals(srcId)) continue;
                Player target = Bukkit.getPlayer(memberId);
                if (target == null || !target.isOnline()) continue;
                action.accept(target);
            }
        });
    }

    // ─── Edge case 2: Late-join catch-up ────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player joining = event.getPlayer();
        UUID joinId = joining.getUniqueId();

        SyncTeam team = teamManager.getTeamOf(joinId);
        if (team == null) return;

        // Find the team leader; if offline, fall back to any online member
        Player source = Bukkit.getPlayer(team.getLeaderUUID());
        if (source == null || !source.isOnline() || source.equals(joining)) {
            source = null;
            for (UUID memberId : team.getMembers()) {
                if (memberId.equals(joinId)) continue;
                Player candidate = Bukkit.getPlayer(memberId);
                if (candidate != null && candidate.isOnline()) {
                    source = candidate;
                    break;
                }
            }
        }

        if (source == null) return; // no online member to sync from

        final Player syncSource = source;
        // 1-tick delay to let the player fully load in before we push data
        Bukkit.getScheduler().runTaskLater(plugin, () ->
                syncManager.syncAll(syncSource, joining, team.getSettings()), 1L);
    }

    // ─── Edge case 3: Leader goes offline → auto-promote ────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player quitting = event.getPlayer();
        UUID quitId = quitting.getUniqueId();

        // Clean up dead-player guard in case they disconnected while dead
        SyncManager.getDeadPlayers().remove(quitId);

        SyncTeam team = teamManager.getTeamOf(quitId);
        if (team == null) return;

        if (team.getLeaderUUID().equals(quitId)) {
            Player newLeader = teamManager.promoteNextOnlineLeader(quitId);
            if (newLeader != null) {
                // Notify the new leader
                newLeader.sendMessage(Component.text(
                        "[Synced] " + quitting.getName() + " left. You are now the team leader.",
                        NamedTextColor.YELLOW));
            }
            // If newLeader == null: no online members, original UUID is preserved
        }
    }

    // ─── Edge case 7: Death handling ────────────────────────────────────────────

    /**
     * On death: add to deadPlayers to prevent syncing 0 health to teammates.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        SyncManager.getDeadPlayers().add(event.getEntity().getUniqueId());
    }

    /**
     * On respawn: remove from deadPlayers, then do a fresh catch-up sync
     * from the leader so the respawned player is back in sync.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player respawned = event.getPlayer();
        UUID respawnId = respawned.getUniqueId();
        SyncManager.getDeadPlayers().remove(respawnId);

        SyncTeam team = teamManager.getTeamOf(respawnId);
        if (team == null) return;

        Player source = Bukkit.getPlayer(team.getLeaderUUID());
        if (source == null || !source.isOnline() || source.equals(respawned)) {
            source = null;
            for (UUID memberId : team.getMembers()) {
                if (memberId.equals(respawnId)) continue;
                Player c = Bukkit.getPlayer(memberId);
                if (c != null && c.isOnline()) { source = c; break; }
            }
        }
        if (source == null) return;

        final Player syncSource = source;
        // Delay to let respawn location be applied first
        Bukkit.getScheduler().runTaskLater(plugin, () ->
                syncManager.syncAll(syncSource, respawned, team.getSettings()), 1L);
    }

    // ─── Edge case 1 + 4: Inventory Sync (close / drop / pickup only) ───────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();
        if (SyncManager.getCurrentlySyncing().contains(uuid)) return;

        SyncTeam team = teamManager.getTeamOf(uuid);
        if (team == null) return;

        InventoryType type = event.getInventory().getType();

        if (type == InventoryType.ENDER_CHEST) {
            if (!team.getSettings().isEnderChestSync()) return;
            broadcastSync(player, team, target -> syncManager.syncEnderChest(player, target));
            return;
        }

        if (!team.getSettings().isInventorySync()) return;
        if (type == InventoryType.PLAYER || type == InventoryType.CRAFTING) {
            broadcastSync(player, team, target -> syncManager.syncInventory(player, target));
        }
    }

    /**
     * Edge case 4: Drop item → 1-tick delayed inventory sync so the item is
     * already out of the inventory when we read its contents.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (SyncManager.getCurrentlySyncing().contains(uuid)) return;

        SyncTeam team = teamManager.getTeamOf(uuid);
        if (team == null || !team.getSettings().isInventorySync()) return;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            if (SyncManager.getCurrentlySyncing().contains(uuid)) return;
            broadcastSync(player, team, target -> syncManager.syncInventory(player, target));
        }, 1L);
    }

    /**
     * Edge case 4: Pickup item → 1-tick delayed inventory sync so the item is
     * already in the inventory when we read its contents.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();
        if (SyncManager.getCurrentlySyncing().contains(uuid)) return;

        SyncTeam team = teamManager.getTeamOf(uuid);
        if (team == null || !team.getSettings().isInventorySync()) return;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            if (SyncManager.getCurrentlySyncing().contains(uuid)) return;
            broadcastSync(player, team, target -> syncManager.syncInventory(player, target));
        }, 1L);
    }

    // ─── Health Sync ─────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityRegainHealth(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();
        if (SyncManager.getCurrentlySyncing().contains(uuid)) return;
        if (SyncManager.getDeadPlayers().contains(uuid)) return;

        SyncTeam team = teamManager.getTeamOf(uuid);
        if (team == null || !team.getSettings().isHealthSync()) return;

        double newHealth = Math.min(
                player.getHealth() + event.getAmount(),
                SyncManager.getMaxHealth(player));

        SyncManager.withSyncLock(uuid, team, () -> {
            for (UUID memberId : team.getMembers()) {
                if (memberId.equals(uuid)) continue;
                if (SyncManager.getDeadPlayers().contains(memberId)) continue;
                Player target = Bukkit.getPlayer(memberId);
                if (target == null || !target.isOnline()) continue;
                double maxHp = SyncManager.getMaxHealth(target);
                target.setHealth(Math.min(newHealth, maxHp));
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();
        if (SyncManager.getCurrentlySyncing().contains(uuid)) return;
        if (SyncManager.getDeadPlayers().contains(uuid)) return;

        SyncTeam team = teamManager.getTeamOf(uuid);
        if (team == null || !team.getSettings().isHealthSync()) return;

        double newHealth = Math.max(0.0, player.getHealth() - event.getFinalDamage());
        // Do not propagate a killing blow — that would kill teammates
        if (newHealth <= 0) return;

        SyncManager.withSyncLock(uuid, team, () -> {
            for (UUID memberId : team.getMembers()) {
                if (memberId.equals(uuid)) continue;
                if (SyncManager.getDeadPlayers().contains(memberId)) continue;
                Player target = Bukkit.getPlayer(memberId);
                if (target == null || !target.isOnline()) continue;
                double maxHp = SyncManager.getMaxHealth(target);
                // Ensure we never drive a teammate to 0 via sync
                double safeHealth = Math.max(0.5, Math.min(newHealth, maxHp));
                target.setHealth(safeHealth);
            }
        });
    }

    // ─── Hunger Sync ─────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();
        if (SyncManager.getCurrentlySyncing().contains(uuid)) return;

        SyncTeam team = teamManager.getTeamOf(uuid);
        if (team == null || !team.getSettings().isHungerSync()) return;

        int newFood = event.getFoodLevel();
        float saturation = player.getSaturation();

        SyncManager.withSyncLock(uuid, team, () -> {
            for (UUID memberId : team.getMembers()) {
                if (memberId.equals(uuid)) continue;
                Player target = Bukkit.getPlayer(memberId);
                if (target == null || !target.isOnline()) continue;
                target.setFoodLevel(newFood);
                target.setSaturation(saturation);
            }
        });
    }

    // ─── XP Sync ─────────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerExpChange(PlayerExpChangeEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (SyncManager.getCurrentlySyncing().contains(uuid)) return;

        SyncTeam team = teamManager.getTeamOf(uuid);
        if (team == null || !team.getSettings().isXpSync()) return;

        int newTotal = player.getTotalExperience() + event.getAmount();
        int level = player.getLevel();
        float progress = player.getExp();

        SyncManager.withSyncLock(uuid, team, () -> {
            for (UUID memberId : team.getMembers()) {
                if (memberId.equals(uuid)) continue;
                Player target = Bukkit.getPlayer(memberId);
                if (target == null || !target.isOnline()) continue;
                target.setTotalExperience(newTotal);
                target.setLevel(level);
                target.setExp(progress);
            }
        });
    }

    // ─── Potion Effects Sync ──────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityPotionEffect(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();
        if (SyncManager.getCurrentlySyncing().contains(uuid)) return;

        SyncTeam team = teamManager.getTeamOf(uuid);
        if (team == null || !team.getSettings().isPotionEffectsSync()) return;

        // 1-tick delay: let the effect actually be applied/removed before we read it
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            if (SyncManager.getCurrentlySyncing().contains(uuid)) return;
            broadcastSync(player, team, target -> syncManager.syncPotionEffects(player, target));
        }, 1L);
    }

    // ─── Edge case 5: Position Sync (with teleport-loop guard) ───────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Edge case 5: if this teleport was triggered by us (PLUGIN cause) and
        // the player is in currentlySyncing, it's a sync-induced TP — ignore it.
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN
                && SyncManager.getCurrentlySyncing().contains(uuid)) {
            return;
        }

        if (SyncManager.getCurrentlySyncing().contains(uuid)) return;

        SyncTeam team = teamManager.getTeamOf(uuid);
        if (team == null || !team.getSettings().isPositionSync()) return;

        var destination = event.getTo();

        SyncManager.withSyncLock(uuid, team, () -> {
            for (UUID memberId : team.getMembers()) {
                if (memberId.equals(uuid)) continue;
                Player target = Bukkit.getPlayer(memberId);
                if (target == null || !target.isOnline()) continue;
                target.teleport(destination);
            }
        });
    }
}

