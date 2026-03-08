package com.aspectxlol.command;

import com.aspectxlol.Synced;
import com.aspectxlol.gui.ConfigGui;
import com.aspectxlol.manager.TeamManager;
import com.aspectxlol.model.SyncTeam;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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

    public SyncedCommand(Synced plugin, TeamManager teamManager) {
        this.plugin = plugin;
        this.teamManager = teamManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "start" -> handleStart(player);
            case "invite" -> handleInvite(player, args);
            case "join" -> handleJoin(player, args);
            case "leave" -> handleLeave(player);
            case "disband" -> handleDisband(player);
            case "info" -> handleInfo(player);
            default -> sendHelp(player);
        }
        return true;
    }

    // ─── Subcommands ────────────────────────────────────────────────────────────

    private void handleStart(Player player) {
        // If the player doesn't have a team, create one
        if (!teamManager.isInTeam(player.getUniqueId())) {
            SyncTeam team = teamManager.createTeam(player.getUniqueId(), player.getName());
            player.sendMessage(Component.text("Created a new sync team! Your team ID: ", NamedTextColor.GREEN)
                    .append(Component.text(team.getId().toString().substring(0, 8), NamedTextColor.YELLOW)));
        }

        SyncTeam team = teamManager.getTeamOf(player.getUniqueId());
        if (team == null) return;

        player.openInventory(ConfigGui.build(team.getSettings()));
    }

    private void handleInvite(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /synced invite <player>", NamedTextColor.RED));
            return;
        }

        SyncTeam team = teamManager.getTeamOf(player.getUniqueId());
        if (team == null) {
            player.sendMessage(Component.text("You are not in a team. Use /synced start to create one.", NamedTextColor.RED));
            return;
        }

        if (!team.getLeaderUUID().equals(player.getUniqueId())) {
            player.sendMessage(Component.text("Only the team leader can invite players.", NamedTextColor.RED));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null || !target.isOnline()) {
            player.sendMessage(Component.text("Player not found or offline.", NamedTextColor.RED));
            return;
        }

        if (teamManager.isInTeam(target.getUniqueId())) {
            player.sendMessage(Component.text(target.getName() + " is already in a team.", NamedTextColor.RED));
            return;
        }

        teamManager.invitePlayer(team.getId(), target.getUniqueId());
        player.sendMessage(Component.text("Invited " + target.getName() + " to your team.", NamedTextColor.GREEN));
        target.sendMessage(Component.text("You have been invited to " + player.getName() + "'s sync team!", NamedTextColor.AQUA)
                .appendNewline()
                .append(Component.text("Use /synced join " + team.getId().toString().substring(0, 8) + " to accept.", NamedTextColor.GRAY)));
    }

    private void handleJoin(Player player, String[] args) {
        if (teamManager.isInTeam(player.getUniqueId())) {
            player.sendMessage(Component.text("You are already in a team. Use /synced leave first.", NamedTextColor.RED));
            return;
        }

        // Check if the player has a pending invite
        SyncTeam invitedTeam = teamManager.getPendingInviteTeam(player.getUniqueId());
        if (invitedTeam == null) {
            player.sendMessage(Component.text("You have no pending team invites.", NamedTextColor.RED));
            return;
        }

        teamManager.joinTeam(player.getUniqueId(), player.getName(), invitedTeam.getId());
        player.sendMessage(Component.text("You joined the sync team!", NamedTextColor.GREEN));

        // Notify other members
        for (UUID memberId : invitedTeam.getMembers()) {
            if (memberId.equals(player.getUniqueId())) continue;
            Player member = Bukkit.getPlayer(memberId);
            if (member != null && member.isOnline()) {
                member.sendMessage(Component.text(player.getName() + " joined your sync team.", NamedTextColor.AQUA));
            }
        }
    }

    private void handleLeave(Player player) {
        if (!teamManager.isInTeam(player.getUniqueId())) {
            player.sendMessage(Component.text("You are not in a team.", NamedTextColor.RED));
            return;
        }

        SyncTeam team = teamManager.getTeamOf(player.getUniqueId());
        teamManager.leaveTeam(player.getUniqueId());
        player.sendMessage(Component.text("You left your sync team.", NamedTextColor.YELLOW));

        // Notify remaining members
        if (team != null) {
            for (UUID memberId : team.getMembers()) {
                Player member = Bukkit.getPlayer(memberId);
                if (member != null && member.isOnline()) {
                    member.sendMessage(Component.text(player.getName() + " left the sync team.", NamedTextColor.YELLOW));
                }
            }
        }
    }

    private void handleDisband(Player player) {
        SyncTeam team = teamManager.getTeamOf(player.getUniqueId());
        if (team == null) {
            player.sendMessage(Component.text("You are not in a team.", NamedTextColor.RED));
            return;
        }

        if (!team.getLeaderUUID().equals(player.getUniqueId())) {
            player.sendMessage(Component.text("Only the team leader can disband the team.", NamedTextColor.RED));
            return;
        }

        // Notify all members before disbanding
        Set<UUID> members = new HashSet<>(team.getMembers());
        teamManager.disbandTeam(team.getId());
        for (UUID memberId : members) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null && member.isOnline()) {
                member.sendMessage(Component.text("Your sync team has been disbanded.", NamedTextColor.RED));
            }
        }
    }

    private void handleInfo(Player player) {
        SyncTeam team = teamManager.getTeamOf(player.getUniqueId());
        if (team == null) {
            player.sendMessage(Component.text("You are not in a team.", NamedTextColor.RED));
            return;
        }

        Component msg = Component.text("═══ Sync Team Info ═══", NamedTextColor.DARK_AQUA)
                .appendNewline()
                .append(Component.text("Members: ", NamedTextColor.AQUA))
                .append(Component.text(buildMemberList(team), NamedTextColor.WHITE))
                .appendNewline()
                .append(Component.text("Toggles:", NamedTextColor.AQUA))
                .appendNewline()
                .append(toggleLine("Inventory", team.getSettings().isInventorySync()))
                .appendNewline()
                .append(toggleLine("Health", team.getSettings().isHealthSync()))
                .appendNewline()
                .append(toggleLine("Hunger", team.getSettings().isHungerSync()))
                .appendNewline()
                .append(toggleLine("XP", team.getSettings().isXpSync()))
                .appendNewline()
                .append(toggleLine("Potion Effects", team.getSettings().isPotionEffectsSync()))
                .appendNewline()
                .append(toggleLine("Ender Chest", team.getSettings().isEnderChestSync()))
                .appendNewline()
                .append(toggleLine("Position", team.getSettings().isPositionSync()));
        player.sendMessage(msg);
    }

    private String buildMemberList(SyncTeam team) {
        List<String> names = new ArrayList<>();
        for (Map.Entry<String, UUID> entry : team.getMemberNames().entrySet()) {
            String name = entry.getKey();
            if (team.getLeaderUUID().equals(entry.getValue())) {
                name += " (Leader)";
            }
            names.add(name);
        }
        return String.join(", ", names);
    }

    private Component toggleLine(String name, boolean enabled) {
        return Component.text("  " + name + ": ", NamedTextColor.GRAY)
                .append(Component.text(enabled ? "✔ ON" : "✘ OFF",
                        enabled ? NamedTextColor.GREEN : NamedTextColor.RED));
    }

    private void sendHelp(Player player) {
        player.sendMessage(Component.text("═══ Synced Commands ═══", NamedTextColor.DARK_AQUA)
                .appendNewline()
                .append(Component.text("/synced start", NamedTextColor.YELLOW))
                .append(Component.text(" - Create team & open config GUI", NamedTextColor.GRAY))
                .appendNewline()
                .append(Component.text("/synced invite <player>", NamedTextColor.YELLOW))
                .append(Component.text(" - Invite a player to your team", NamedTextColor.GRAY))
                .appendNewline()
                .append(Component.text("/synced join", NamedTextColor.YELLOW))
                .append(Component.text(" - Accept a team invite", NamedTextColor.GRAY))
                .appendNewline()
                .append(Component.text("/synced leave", NamedTextColor.YELLOW))
                .append(Component.text(" - Leave your team", NamedTextColor.GRAY))
                .appendNewline()
                .append(Component.text("/synced disband", NamedTextColor.YELLOW))
                .append(Component.text(" - Disband your team (leader only)", NamedTextColor.GRAY))
                .appendNewline()
                .append(Component.text("/synced info", NamedTextColor.YELLOW))
                .append(Component.text(" - View team info & sync settings", NamedTextColor.GRAY)));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("start", "invite", "join", "leave", "disband", "info").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("invite")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}


