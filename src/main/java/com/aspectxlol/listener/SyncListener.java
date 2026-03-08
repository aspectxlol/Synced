package com.aspectxlol.listener;

import com.aspectxlol.Synced;
import com.aspectxlol.manager.SyncManager;
import com.aspectxlol.manager.TablistManager;
import com.aspectxlol.manager.TeamManager;
import com.aspectxlol.model.SyncTeam;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
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
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class SyncListener implements Listener {

    private final Synced plugin;
    private final TeamManager teamManager;
    private final SyncManager syncManager;
    private final TablistManager tablistManager;

    /**
     * Players who already have a 1-tick inventory sync scheduled this tick.
     * Prevents scheduling the same sync multiple times when several inventory
     * events fire in rapid succession (e.g. shift-clicking stacks).
     */
    private final Set<UUID> pendingInventorySync = new HashSet<>();

    public SyncListener(Synced plugin, TeamManager teamManager, SyncManager syncManager,
                        TablistManager tablistManager) {
        this.plugin = plugin;
        this.teamManager = teamManager;
        this.syncManager = syncManager;
        this.tablistManager = tablistManager;
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

        // Apply scoreboard so this player sees team colours (and others see theirs)
        tablistManager.onJoin(joining);

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

        tablistManager.onQuit(quitting);
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
     * Totem of Undying guard.
     *
     * When {@code sync-totem-effects} is false (default): the totem holder is
     * added to {@code currentlySyncing} for 40 ticks (2 s). This covers the
     * full burst of regeneration/absorption/fire-resistance effects that Minecraft
     * applies in the ticks immediately after resurrection, so none of them
     * propagate to teammates. Only the holder benefits from their own totem.
     *
     * When {@code sync-totem-effects} is true: the guard is skipped entirely and
     * the recovery effects sync to teammates as normal health/effect events.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTotemUse(EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (plugin.getConfig().getBoolean("sync-totem-effects", false)) return;

        UUID uuid = player.getUniqueId();
        SyncManager.getCurrentlySyncing().add(uuid);
        // Remove after 2 seconds — long enough to cover the entire totem effect burst
        Bukkit.getScheduler().runTaskLater(plugin, () ->
                SyncManager.getCurrentlySyncing().remove(uuid), 40L);
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

    // ─── Inventory Sync ──────────────────────────────────────────────────────────
    //
    // We want every inventory change to propagate immediately. The events below
    // cover every way a player's inventory can change:
    //
    //   InventoryCloseEvent      – closed any inventory (crafting table, chest, etc.)
    //   InventoryClickEvent      – clicked inside any open inventory (external or own)
    //   PlayerDropItemEvent      – threw an item (Q)
    //   EntityPickupItemEvent    – picked up a ground item
    //   PlayerSwapHandItemsEvent – pressed F (main ↔ offhand)
    //   PlayerItemConsumeEvent   – finished eating/drinking (item removed from hand)
    //
    // All handlers call scheduleInventorySync() which uses a 1-tick delay so the
    // inventory state has settled, and a per-player pendingInventorySync set so
    // multiple events in the same tick collapse into a single broadcast.

    private void scheduleInventorySync(Player player, SyncTeam team) {
        UUID uuid = player.getUniqueId();
        if (pendingInventorySync.contains(uuid)) return; // already queued this tick
        pendingInventorySync.add(uuid);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pendingInventorySync.remove(uuid);
            if (!player.isOnline()) return;
            if (SyncManager.getCurrentlySyncing().contains(uuid)) return;
            broadcastSync(player, team, target -> syncManager.syncInventory(player, target));
        }, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();
        if (SyncManager.getCurrentlySyncing().contains(uuid)) return;

        SyncTeam team = teamManager.getTeamOf(uuid);
        if (team == null) return;

        InventoryType type = event.getInventory().getType();

        // Ender chest close — sync ender chest contents
        if (type == InventoryType.ENDER_CHEST) {
            if (!team.getSettings().isEnderChestSync()) return;
            broadcastSync(player, team, target -> syncManager.syncEnderChest(player, target));
            return;
        }

        if (!team.getSettings().isInventorySync()) return;
        scheduleInventorySync(player, team);
    }

    /** Catches every click inside any open inventory — crafting table, chest, own inventory, etc. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();
        if (SyncManager.getCurrentlySyncing().contains(uuid)) return;

        // Skip the Synced config GUI itself — GuiClickListener handles that
        String title = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(event.getView().title());
        if (title.equals(com.aspectxlol.gui.ConfigGui.GUI_TITLE)) return;

        SyncTeam team = teamManager.getTeamOf(uuid);
        if (team == null || !team.getSettings().isInventorySync()) return;

        scheduleInventorySync(player, team);
    }

    /** Q — item thrown out of inventory. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (SyncManager.getCurrentlySyncing().contains(uuid)) return;

        SyncTeam team = teamManager.getTeamOf(uuid);
        if (team == null || !team.getSettings().isInventorySync()) return;

        scheduleInventorySync(player, team);
    }

    /** Ground item walked over / magnet-picked up. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();
        if (SyncManager.getCurrentlySyncing().contains(uuid)) return;

        SyncTeam team = teamManager.getTeamOf(uuid);
        if (team == null || !team.getSettings().isInventorySync()) return;

        scheduleInventorySync(player, team);
    }

    /** F key — swaps main hand and off-hand. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (SyncManager.getCurrentlySyncing().contains(uuid)) return;

        SyncTeam team = teamManager.getTeamOf(uuid);
        if (team == null || !team.getSettings().isInventorySync()) return;

        scheduleInventorySync(player, team);
    }

    /** Finished eating/drinking — item removed from hand slot. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerItemConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (SyncManager.getCurrentlySyncing().contains(uuid)) return;

        SyncTeam team = teamManager.getTeamOf(uuid);
        if (team == null || !team.getSettings().isInventorySync()) return;

        scheduleInventorySync(player, team);
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

        // Calculate the health the source will have after this hit (clamped to 0).
        // We DO propagate fatal blows — if one teammate dies, all teammates die.
        // The deadPlayers guard in onPlayerDeath prevents the resulting setHealth(0)
        // calls on teammates from looping back and re-killing the original player.
        double newHealth = Math.max(0.0, player.getHealth() - event.getFinalDamage());

        SyncManager.withSyncLock(uuid, team, () -> {
            for (UUID memberId : team.getMembers()) {
                if (memberId.equals(uuid)) continue;
                if (SyncManager.getDeadPlayers().contains(memberId)) continue;
                Player target = Bukkit.getPlayer(memberId);
                if (target == null || !target.isOnline()) continue;
                double maxHp = SyncManager.getMaxHealth(target);
                // Clamp to target's own max health, but allow 0 (death)
                target.setHealth(Math.min(newHealth, maxHp));
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

