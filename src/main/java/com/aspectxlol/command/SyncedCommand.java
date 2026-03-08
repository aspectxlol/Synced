package com.aspectxlol.command;

import com.aspectxlol.Synced;
import com.aspectxlol.gui.ConfigGui;
import com.aspectxlol.manager.TablistManager;
import com.aspectxlol.manager.TeamManager;
import com.aspectxlol.model.SyncTeam;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class SyncedCommand implements CommandExecutor, TabCompleter {

    private final Synced plugin;
    private final TeamManager teamManager;
    private final TablistManager tablistManager;

    public SyncedCommand(Synced plugin, TeamManager teamManager, TablistManager tablistManager) {
        this.plugin = plugin;
        this.teamManager = teamManager;
        this.tablistManager = tablistManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) { sendHelp(player); return true; }

        switch (args[0].toLowerCase()) {
            case "start"          -> handleStart(player);
            case "join"           -> handleJoin(player, args);
            case "leave"          -> handleLeave(player);
            case "disband"        -> handleDisband(player);
            case "confirmdisband" -> executedisband(player);
            case "decline"        -> handleDecline(player, args);
            case "cancel"         -> { /* no-op — button closes the message */ }
            case "info"           -> handleInfo(player);
            case "kick"           -> handleKick(player, args);
            default               -> sendHelp(player);
        }
        return true;
    }

    // ─── /sync start ─────────────────────────────────────────────────────────────
    // Opens config GUI. If the player has no team yet, creates one first.

    private void handleStart(Player player) {
        if (!teamManager.isInTeam(player.getUniqueId())) {
            teamManager.createTeam(player.getUniqueId(), player.getName());
            tablistManager.refresh(player);
            player.sendMessage(
                Component.text("✦ Sync team created! ", NamedTextColor.GREEN)
                    .append(Component.text("Invite players with ", NamedTextColor.GRAY))
                    .append(clickableCommand("/sync join <player>",
                            "/sync join ", NamedTextColor.YELLOW,
                            "Click to start typing /sync join"))
            );
        }

        SyncTeam team = teamManager.getTeamOf(player.getUniqueId());
        if (team == null) return;
        player.openInventory(ConfigGui.build(team.getSettings()));
    }

    // ─── /sync join <player> ─────────────────────────────────────────────────────
    // New flow:
    //   • If <player> already sent YOU an invite → auto-accept, join their team.
    //   • Otherwise → send <player> a clickable invite notification.
    //   • If both sides run /sync join <other> → team forms automatically.

    private void handleJoin(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /sync join <player>", NamedTextColor.RED));
            return;
        }

        if (teamManager.isInTeam(player.getUniqueId())) {
            player.sendMessage(Component.text("You are already in a sync team. Use ", NamedTextColor.RED)
                .append(clickableCommand("/sync leave", "/sync leave", NamedTextColor.YELLOW, "Click to leave your team"))
                .append(Component.text(" first.", NamedTextColor.RED)));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null || !target.isOnline()) {
            player.sendMessage(Component.text("Player \"" + args[1] + "\" is not online.", NamedTextColor.RED));
            return;
        }
        if (target.equals(player)) {
            player.sendMessage(Component.text("You cannot invite yourself.", NamedTextColor.RED));
            return;
        }

        // Case 1: target already invited the player → accept and join their team
        SyncTeam existingInvite = teamManager.getPendingInviteFrom(player.getUniqueId(), target.getUniqueId());
        if (existingInvite != null) {
            // Target has a team and invited us — join it
            if (teamManager.isInTeam(target.getUniqueId())) {
                teamManager.joinTeam(player.getUniqueId(), player.getName(), existingInvite.getId());
                tablistManager.refresh(player);
                tablistManager.refreshTeam(existingInvite);

                player.sendMessage(header()
                    .append(Component.text("You joined ", NamedTextColor.GREEN))
                    .append(Component.text(target.getName(), NamedTextColor.AQUA))
                    .append(Component.text("'s team!", NamedTextColor.GREEN)));

                // Notify team
                notifyTeam(existingInvite, player,
                    Component.text(player.getName(), NamedTextColor.AQUA)
                        .append(Component.text(" joined the team!", NamedTextColor.GREEN)));
                return;
            }
        }

        // Case 2: target is in a team and we are the one they invited (pending invite exists on their side)
        SyncTeam targetTeam = teamManager.getTeamOf(target.getUniqueId());
        if (targetTeam != null) {
            // Check if target's team has a pending invite for this player
            SyncTeam inviteTeam = teamManager.getPendingInviteTeam(player.getUniqueId());
            if (inviteTeam != null && inviteTeam.getId().equals(targetTeam.getId())) {
                teamManager.joinTeam(player.getUniqueId(), player.getName(), inviteTeam.getId());
                tablistManager.refresh(player);
                tablistManager.refreshTeam(inviteTeam);

                player.sendMessage(header()
                    .append(Component.text("You joined ", NamedTextColor.GREEN))
                    .append(Component.text(target.getName(), NamedTextColor.AQUA))
                    .append(Component.text("'s team!", NamedTextColor.GREEN)));

                notifyTeam(inviteTeam, player,
                    Component.text(player.getName(), NamedTextColor.AQUA)
                        .append(Component.text(" joined the team!", NamedTextColor.GREEN)));
                return;
            }
            player.sendMessage(Component.text(target.getName() + " is already in a team.", NamedTextColor.RED));
            return;
        }

        // Case 3: target has no team yet — check if target already wants to join US
        // (target ran /sync join <player> earlier, which created a pending invite stored as target→player)
        boolean mutualRequest = teamManager.hasPendingOutgoingInvite(target.getUniqueId(), player.getUniqueId());
        if (mutualRequest) {
            // Both want to sync with each other → create a team with player as leader
            SyncTeam newTeam = teamManager.createTeam(player.getUniqueId(), player.getName());
            teamManager.joinTeam(target.getUniqueId(), target.getName(), newTeam.getId());
            tablistManager.refresh(player);
            tablistManager.refresh(target);

            player.sendMessage(header()
                .append(Component.text("Sync team formed with ", NamedTextColor.GREEN))
                .append(Component.text(target.getName(), NamedTextColor.AQUA))
                .append(Component.text("! Use ", NamedTextColor.GREEN))
                .append(clickableCommand("/sync start", "/sync start", NamedTextColor.YELLOW,
                        "Click to open the sync config"))
                .append(Component.text(" to configure.", NamedTextColor.GREEN)));

            target.sendMessage(header()
                .append(Component.text(player.getName(), NamedTextColor.AQUA))
                .append(Component.text(" accepted! You are now in a sync team. Use ", NamedTextColor.GREEN))
                .append(clickableCommand("/sync start", "/sync start", NamedTextColor.YELLOW,
                        "Click to open the sync config"))
                .append(Component.text(" to configure.", NamedTextColor.GREEN)));
            return;
        }

        // Case 4: fresh invite — store it and notify target
        teamManager.storePendingOutgoingInvite(player.getUniqueId(), target.getUniqueId(), plugin);

        player.sendMessage(header()
            .append(Component.text("Sync request sent to ", NamedTextColor.GREEN))
            .append(Component.text(target.getName(), NamedTextColor.AQUA))
            .append(Component.text(". Waiting for them to run ", NamedTextColor.GRAY))
            .append(Component.text("/sync join " + player.getName(), NamedTextColor.YELLOW))
            .append(Component.text(".", NamedTextColor.GRAY)));

        // Clickable invite for target
        target.sendMessage(
            header()
            .append(Component.text(player.getName(), NamedTextColor.AQUA))
            .append(Component.text(" wants to sync with you!", NamedTextColor.GREEN))
            .appendNewline()
            .append(Component.text("  ", NamedTextColor.WHITE))
            .append(Component.text("[ ✔ Accept ]", NamedTextColor.GREEN)
                .decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/sync join " + player.getName()))
                .hoverEvent(HoverEvent.showText(Component.text("Click to join " + player.getName() + "'s sync team"))))
            .append(Component.text("   ", NamedTextColor.WHITE))
            .append(Component.text("[ ✘ Decline ]", NamedTextColor.RED)
                .decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/sync decline " + player.getName()))
                .hoverEvent(HoverEvent.showText(Component.text("Click to decline this invite"))))
        );
    }

    // ─── /sync leave ─────────────────────────────────────────────────────────────

    private void handleLeave(Player player) {
        if (!teamManager.isInTeam(player.getUniqueId())) {
            player.sendMessage(Component.text("You are not in a sync team.", NamedTextColor.RED));
            return;
        }

        SyncTeam team = teamManager.getTeamOf(player.getUniqueId());
        Set<UUID> oldMembers = team != null ? new HashSet<>(team.getMembers()) : Set.of();

        teamManager.leaveTeam(player.getUniqueId());
        tablistManager.refresh(player);
        // Refresh remaining members too (leader may have changed)
        for (UUID id : oldMembers) {
            Player m = Bukkit.getPlayer(id);
            if (m != null && m.isOnline()) tablistManager.refresh(m);
        }

        player.sendMessage(header().append(Component.text("You left your sync team.", NamedTextColor.YELLOW)));

        for (UUID id : oldMembers) {
            if (id.equals(player.getUniqueId())) continue;
            Player m = Bukkit.getPlayer(id);
            if (m != null && m.isOnline()) {
                m.sendMessage(header()
                    .append(Component.text(player.getName(), NamedTextColor.AQUA))
                    .append(Component.text(" left the sync team.", NamedTextColor.YELLOW)));
            }
        }
    }

    // ─── /sync disband ───────────────────────────────────────────────────────────

    private void handleDisband(Player player) {
        SyncTeam team = teamManager.getTeamOf(player.getUniqueId());
        if (team == null) {
            player.sendMessage(Component.text("You are not in a sync team.", NamedTextColor.RED));
            return;
        }
        if (!team.getLeaderUUID().equals(player.getUniqueId())) {
            player.sendMessage(Component.text("Only the team leader can disband the team.", NamedTextColor.RED)
                .appendNewline()
                .append(Component.text("Use ", NamedTextColor.GRAY))
                .append(clickableCommand("/sync leave", "/sync leave", NamedTextColor.YELLOW, "Click to leave"))
                .append(Component.text(" to leave instead.", NamedTextColor.GRAY)));
            return;
        }

        // Confirm button
        player.sendMessage(header()
            .append(Component.text("Are you sure you want to disband the team?", NamedTextColor.RED))
            .appendNewline()
            .append(Component.text("  "))
            .append(Component.text("[ ✔ Yes, disband ]", NamedTextColor.RED)
                .decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/sync confirmdisband"))
                .hoverEvent(HoverEvent.showText(Component.text("This cannot be undone!"))))
            .append(Component.text("   "))
            .append(Component.text("[ ✘ Cancel ]", NamedTextColor.GREEN)
                .decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/sync cancel"))
                .hoverEvent(HoverEvent.showText(Component.text("Cancel"))))
        );
    }

    /** Internal — called from confirm button. */
    private void executedisband(Player player) {
        SyncTeam team = teamManager.getTeamOf(player.getUniqueId());
        if (team == null || !team.getLeaderUUID().equals(player.getUniqueId())) return;

        Set<UUID> members = new HashSet<>(team.getMembers());
        teamManager.disbandTeam(team.getId());
        for (UUID id : members) {
            Player m = Bukkit.getPlayer(id);
            if (m != null && m.isOnline()) {
                tablistManager.refresh(m);
                m.sendMessage(header().append(Component.text("The sync team was disbanded.", NamedTextColor.RED)));
            }
        }
    }

    // ─── /sync decline <player> ──────────────────────────────────────────────────

    private void handleDecline(Player player, String[] args) {
        if (args.length < 2) return;
        Player sender = Bukkit.getPlayer(args[1]);
        // Remove the outgoing invite the sender had stored
        teamManager.cancelOutgoingInvite(sender != null ? sender.getUniqueId() : null, player.getUniqueId());

        player.sendMessage(header()
            .append(Component.text("Declined the sync request from ", NamedTextColor.YELLOW))
            .append(Component.text(args[1], NamedTextColor.AQUA))
            .append(Component.text(".", NamedTextColor.YELLOW)));

        if (sender != null && sender.isOnline()) {
            sender.sendMessage(header()
                .append(Component.text(player.getName(), NamedTextColor.AQUA))
                .append(Component.text(" declined your sync request.", NamedTextColor.RED)));
        }
    }

    // ─── /sync kick <player> ─────────────────────────────────────────────────────

    private void handleKick(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /sync kick <player>", NamedTextColor.RED));
            return;
        }
        SyncTeam team = teamManager.getTeamOf(player.getUniqueId());
        if (team == null || !team.getLeaderUUID().equals(player.getUniqueId())) {
            player.sendMessage(Component.text("Only the team leader can kick players.", NamedTextColor.RED));
            return;
        }

        // Find target by name in team
        UUID targetId = null;
        String targetName = null;
        for (Map.Entry<String, UUID> e : team.getMemberNames().entrySet()) {
            if (e.getKey().equalsIgnoreCase(args[1])) {
                targetId = e.getValue();
                targetName = e.getKey();
                break;
            }
        }
        if (targetId == null || targetId.equals(player.getUniqueId())) {
            player.sendMessage(Component.text("Player not found in your team.", NamedTextColor.RED));
            return;
        }

        teamManager.leaveTeam(targetId);
        tablistManager.refreshAll();

        player.sendMessage(header()
            .append(Component.text(targetName, NamedTextColor.AQUA))
            .append(Component.text(" was kicked from the team.", NamedTextColor.YELLOW)));

        Player kicked = Bukkit.getPlayer(targetId);
        if (kicked != null && kicked.isOnline()) {
            kicked.sendMessage(header()
                .append(Component.text("You were kicked from the sync team by ", NamedTextColor.RED))
                .append(Component.text(player.getName(), NamedTextColor.AQUA))
                .append(Component.text(".", NamedTextColor.RED)));
        }

        notifyTeam(team, null,
            Component.text(targetName, NamedTextColor.AQUA)
                .append(Component.text(" was kicked from the team.", NamedTextColor.YELLOW)));
    }

    // ─── /sync info ──────────────────────────────────────────────────────────────

    private void handleInfo(Player player) {
        SyncTeam team = teamManager.getTeamOf(player.getUniqueId());
        if (team == null) {
            player.sendMessage(Component.text("You are not in a sync team.", NamedTextColor.RED)
                .appendNewline()
                .append(Component.text("Start one with ", NamedTextColor.GRAY))
                .append(clickableCommand("/sync join <player>", "/sync join ",
                        NamedTextColor.YELLOW, "Click to start typing /sync join")));
            return;
        }

        Component msg = Component.text("━━━ Synced Team ━━━", NamedTextColor.DARK_AQUA).decorate(TextDecoration.BOLD)
            .appendNewline()
            .append(Component.text("Members: ", NamedTextColor.AQUA));

        // List members with online/offline indicator
        List<Component> memberComponents = new ArrayList<>();
        for (Map.Entry<String, UUID> e : team.getMemberNames().entrySet()) {
            boolean isLeader = team.getLeaderUUID().equals(e.getValue());
            boolean online = Bukkit.getPlayer(e.getValue()) != null;
            NamedTextColor col = online ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY;
            Component nameComp = Component.text(e.getKey(), col);
            if (isLeader) nameComp = nameComp.append(Component.text(" ★", NamedTextColor.YELLOW));
            memberComponents.add(nameComp);
        }
        msg = msg.append(Component.join(
                net.kyori.adventure.text.JoinConfiguration.separator(Component.text(", ", NamedTextColor.GRAY)),
                memberComponents))
            .appendNewline()
            .append(Component.text("Sync toggles:", NamedTextColor.AQUA))
            .appendNewline()
            .append(toggleLine("Inventory",     team.getSettings().isInventorySync()))
            .appendNewline()
            .append(toggleLine("Health",        team.getSettings().isHealthSync()))
            .appendNewline()
            .append(toggleLine("Hunger",        team.getSettings().isHungerSync()))
            .appendNewline()
            .append(toggleLine("XP",            team.getSettings().isXpSync()))
            .appendNewline()
            .append(toggleLine("Potion Effects",team.getSettings().isPotionEffectsSync()))
            .appendNewline()
            .append(toggleLine("Ender Chest",   team.getSettings().isEnderChestSync()))
            .appendNewline()
            .append(toggleLine("Position",      team.getSettings().isPositionSync()))
            .appendNewline()
            .append(clickableCommand("[ ⚙ Configure ]", "/sync start",
                    NamedTextColor.AQUA, "Click to open the config GUI"));

        player.sendMessage(msg);
    }

    // ─── Help ────────────────────────────────────────────────────────────────────

    private void sendHelp(Player player) {
        player.sendMessage(
            Component.text("━━━ Synced Commands ━━━", NamedTextColor.DARK_AQUA).decorate(TextDecoration.BOLD)
            .appendNewline()
            .append(helpLine("/sync join <player>",  "/sync join ",   "Invite or accept an invite from a player"))
            .appendNewline()
            .append(helpLine("/sync start",          "/sync start",   "Open the sync config GUI"))
            .appendNewline()
            .append(helpLine("/sync info",           "/sync info",    "Show your team info & toggles"))
            .appendNewline()
            .append(helpLine("/sync kick <player>",  "/sync kick ",   "Kick a player (leader only)"))
            .appendNewline()
            .append(helpLine("/sync leave",          "/sync leave",   "Leave your sync team"))
            .appendNewline()
            .append(helpLine("/sync disband",        "/sync disband", "Disband the team (leader only)"))
        );
    }

    // ─── Internal helpers ────────────────────────────────────────────────────────

    private Component header() {
        return Component.text("[Synced] ", NamedTextColor.DARK_AQUA).decorate(TextDecoration.BOLD);
    }

    private Component helpLine(String display, String command, String tooltip) {
        return Component.text("  ")
            .append(Component.text(display, NamedTextColor.YELLOW)
                .clickEvent(ClickEvent.suggestCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text(tooltip, NamedTextColor.GRAY))));
    }

    private Component clickableCommand(String display, String command,
                                       NamedTextColor color, String tooltip) {
        return Component.text(display, color)
            .clickEvent(ClickEvent.suggestCommand(command))
            .hoverEvent(HoverEvent.showText(Component.text(tooltip, NamedTextColor.GRAY)));
    }

    private Component toggleLine(String name, boolean enabled) {
        return Component.text("  " + name + ": ", NamedTextColor.GRAY)
            .append(Component.text(enabled ? "✔ ON" : "✘ OFF",
                    enabled ? NamedTextColor.GREEN : NamedTextColor.RED));
    }

    private void notifyTeam(SyncTeam team, Player exclude, Component message) {
        for (UUID id : team.getMembers()) {
            if (exclude != null && id.equals(exclude.getUniqueId())) continue;
            Player m = Bukkit.getPlayer(id);
            if (m != null && m.isOnline()) {
                m.sendMessage(header().append(message));
            }
        }
    }

    // ─── Tab completion ──────────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, String[] args) {
        if (args.length == 1) {
            return List.of("join", "start", "info", "kick", "leave", "disband")
                    .stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("join") || sub.equals("kick")) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                        .toList();
            }
        }
        return List.of();
    }
}

