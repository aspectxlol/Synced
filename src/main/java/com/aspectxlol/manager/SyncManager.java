package com.aspectxlol.manager;

import com.aspectxlol.model.SyncSettings;
import com.aspectxlol.model.SyncTeam;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.*;

public class SyncManager {

    private final Set<UUID> currentlySyncing = Collections.synchronizedSet(new HashSet<>());

    public Set<UUID> getCurrentlySyncing() {
        return currentlySyncing;
    }

    /**
     * Syncs the source player's data to all other online team members.
     */
    public void syncFromPlayer(Player source, SyncTeam team) {
        if (currentlySyncing.contains(source.getUniqueId())) return;

        SyncSettings settings = team.getSettings();

        for (UUID memberId : team.getMembers()) {
            if (memberId.equals(source.getUniqueId())) continue;
            Player target = Bukkit.getPlayer(memberId);
            if (target == null || !target.isOnline()) continue;
            if (currentlySyncing.contains(target.getUniqueId())) continue;

            currentlySyncing.add(target.getUniqueId());
            try {
                applySync(source, target, settings);
            } finally {
                currentlySyncing.remove(target.getUniqueId());
            }
        }
    }

    /**
     * Syncs a specific aspect from source to target.
     */
    private void applySync(Player source, Player target, SyncSettings settings) {
        if (settings.isInventorySync()) {
            syncInventory(source, target);
        }
        if (settings.isHealthSync()) {
            syncHealth(source, target);
        }
        if (settings.isHungerSync()) {
            syncHunger(source, target);
        }
        if (settings.isXpSync()) {
            syncXp(source, target);
        }
        if (settings.isPotionEffectsSync()) {
            syncPotionEffects(source, target);
        }
        // Ender chest and position sync are handled separately via events
    }

    public void syncInventory(Player source, Player target) {
        ItemStack[] contents = source.getInventory().getContents().clone();
        ItemStack[] copied = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            copied[i] = contents[i] != null ? contents[i].clone() : null;
        }
        target.getInventory().setContents(copied);
        target.updateInventory();
    }

    public void syncHealth(Player source, Player target) {
        target.setHealth(Math.min(source.getHealth(), target.getAttribute(
                org.bukkit.attribute.Attribute.MAX_HEALTH).getValue()));
    }

    public void syncHunger(Player source, Player target) {
        target.setFoodLevel(source.getFoodLevel());
        target.setSaturation(source.getSaturation());
        target.setExhaustion(source.getExhaustion());
    }

    public void syncXp(Player source, Player target) {
        target.setLevel(source.getLevel());
        target.setExp(source.getExp());
        target.setTotalExperience(source.getTotalExperience());
    }

    public void syncPotionEffects(Player source, Player target) {
        // Remove all current effects from target
        for (PotionEffect effect : new ArrayList<>(target.getActivePotionEffects())) {
            target.removePotionEffect(effect.getType());
        }
        // Apply source's effects
        for (PotionEffect effect : source.getActivePotionEffects()) {
            target.addPotionEffect(effect);
        }
    }

    public void syncEnderChest(Player source, Player target) {
        ItemStack[] contents = source.getEnderChest().getContents().clone();
        ItemStack[] copied = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            copied[i] = contents[i] != null ? contents[i].clone() : null;
        }
        target.getEnderChest().setContents(copied);
    }

    public void syncPosition(Player source, Player target) {
        target.teleport(source.getLocation());
    }
}

