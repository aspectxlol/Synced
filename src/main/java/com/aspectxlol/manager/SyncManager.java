package com.aspectxlol.manager;

import com.aspectxlol.model.SyncSettings;
import com.aspectxlol.model.SyncTeam;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

public class SyncManager {

    /**
     * Static so all listeners share the exact same guard set.
     * Synchronized to be thread-safe (Bukkit events are on the main thread,
     * but better safe than sorry).
     */
    private static final Set<UUID> currentlySyncing =
            Collections.synchronizedSet(new HashSet<>());

    /**
     * Players who are currently dead and must NOT be health-synced.
     */
    private static final Set<UUID> deadPlayers =
            Collections.synchronizedSet(new HashSet<>());

    /**
     * Fallback max-health value used when the Attribute registry lookup returns null.
     * Vanilla Minecraft default is 20.0 (10 hearts).
     */
    private static final double DEFAULT_MAX_HEALTH = 20.0;


    // ─── Guard helpers ──────────────────────────────────────────────────────────

    public static Set<UUID> getCurrentlySyncing() {
        return currentlySyncing;
    }

    public static Set<UUID> getDeadPlayers() {
        return deadPlayers;
    }

    /**
     * Locks the source player AND all online team members into currentlySyncing,
     * runs the action, then unlocks everyone.
     */
    public static void withSyncLock(UUID source, SyncTeam team, Runnable action) {
        Set<UUID> locked = new HashSet<>();
        locked.add(source);
        for (UUID memberId : team.getMembers()) {
            if (!memberId.equals(source)) {
                Player p = Bukkit.getPlayer(memberId);
                if (p != null && p.isOnline()) locked.add(memberId);
            }
        }
        currentlySyncing.addAll(locked);
        try {
            action.run();
        } finally {
            currentlySyncing.removeAll(locked);
        }
    }

    // ─── Full catch-up sync (used on late join) ─────────────────────────────────

    /**
     * Pushes all enabled sync types from {@code source} to {@code target} only.
     * Used for late-join catch-up: source is the team leader (or any online member).
     */
    public void syncAll(Player source, Player target, SyncSettings settings) {
        UUID srcId = source.getUniqueId();
        UUID tgtId = target.getUniqueId();
        currentlySyncing.add(srcId);
        currentlySyncing.add(tgtId);
        try {
            if (settings.isInventorySync())     syncInventory(source, target);
            if (settings.isHealthSync()
                    && !deadPlayers.contains(srcId)) syncHealth(source, target);
            if (settings.isHungerSync())        syncHunger(source, target);
            if (settings.isXpSync())            syncXp(source, target);
            if (settings.isPotionEffectsSync()) syncPotionEffects(source, target);
            if (settings.isEnderChestSync())    syncEnderChest(source, target);
            // Position sync on join is intentionally skipped to avoid jarring TP
        } finally {
            currentlySyncing.remove(srcId);
            currentlySyncing.remove(tgtId);
        }
    }

    // ─── Per-type sync methods ──────────────────────────────────────────────────

    public void syncInventory(Player source, Player target) {
        PlayerInventory src = source.getInventory();
        PlayerInventory dst = target.getInventory();

        // ── Main storage (slots 0-35: hotbar + main grid) ──────────────────────
        dst.setContents(deepCopy(src.getContents()));

        // ── Armour (helmet, chestplate, leggings, boots) ───────────────────────
        dst.setHelmet(     clone(src.getHelmet()));
        dst.setChestplate( clone(src.getChestplate()));
        dst.setLeggings(   clone(src.getLeggings()));
        dst.setBoots(      clone(src.getBoots()));

        // ── Off-hand ───────────────────────────────────────────────────────────
        dst.setItemInOffHand(clone(src.getItemInOffHand()));

        target.updateInventory();
    }

    // Deep-copies an array of ItemStacks, preserving nulls and all NBT data.
    private static ItemStack[] deepCopy(ItemStack[] original) {
        ItemStack[] copy = new ItemStack[original.length];
        for (int i = 0; i < original.length; i++) {
            copy[i] = clone(original[i]);
        }
        return copy;
    }

    // Null-safe single-item clone.
    private static ItemStack clone(ItemStack item) {
        return item != null ? item.clone() : null;
    }

    public void syncHealth(Player source, Player target) {
        double maxHealth = getMaxHealth(target);
        target.setHealth(Math.min(source.getHealth(), maxHealth));
    }

    // ─── Internal helpers ───────────────────────────────────────────────────────

    /**
     * Looks up MAX_HEALTH via the Paper 1.21 registry-based Attribute API.
     * Avoids the removed Attribute.MAX_HEALTH enum constant.
     */
    public static double getMaxHealth(Player player) {
        Attribute maxHealthAttr = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("max_health"));
        if (maxHealthAttr == null) return DEFAULT_MAX_HEALTH;
        AttributeInstance inst = player.getAttribute(maxHealthAttr);
        return inst != null ? inst.getValue() : DEFAULT_MAX_HEALTH;
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

    /**
     * Smart potion sync: only removes effects the source does NOT have,
     * then adds/updates effects the source does have.
     * This prevents flickering from a blind clearActivePotionEffects().
     */
    public void syncPotionEffects(Player source, Player target) {
        Collection<PotionEffect> sourceEffects = source.getActivePotionEffects();
        Set<PotionEffectType> sourceTypes = new HashSet<>();
        for (PotionEffect e : sourceEffects) sourceTypes.add(e.getType());

        // Remove only effects the target has that the source does NOT have
        for (PotionEffect effect : new ArrayList<>(target.getActivePotionEffects())) {
            if (!sourceTypes.contains(effect.getType())) {
                target.removePotionEffect(effect.getType());
            }
        }
        // Apply / update source's effects on the target
        for (PotionEffect effect : sourceEffects) {
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
