/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.jobs;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The items a company builds a supplied build job with: the job chest, which marks where its members pick their work
 * material and is placed as a barrel, and the magic blocks it hands out, each bound to a job and an item type of its
 * stock. Both are knowledge books that look like what they stand for, so an item that ends up anywhere else is worth
 * nothing: it places, crafts, smelts and burns as nothing. Both carry their job in their persistent data, and so does a
 * placed job chest.
 */
public final class MagicItems {
    /**
     * The block a job chest is placed as.
     */
    public static final Material CHEST = Material.BARREL;

    /**
     * The item magic blocks and job chests are made of.
     */
    public static final Material BASE = Material.KNOWLEDGE_BOOK;

    private final NamespacedKey magicJob;
    private final NamespacedKey magicType;
    private final NamespacedKey chestJob;

    /**
     * @param plugin the plugin owning the keys
     */
    public MagicItems(Plugin plugin) {
        this.magicJob = new NamespacedKey(plugin, "magic_job");
        this.magicType = new NamespacedKey(plugin, "magic_type");
        this.chestJob = new NamespacedKey(plugin, "job_chest");
    }

    /**
     * A magic block.
     *
     * @param job  id of the job it is bound to
     * @param type the item type of the job's stock it places
     */
    public record Magic(int job, Material type) {}

    /**
     * @param job  id of the job
     * @param type an item type of its stock
     * @param name the name it shows
     * @param lore the lines below its name
     * @return one magic block of that type, looking like it and glowing
     */
    public ItemStack magic(int job, Material type, Component name, List<Component> lore) {
        var item = new ItemStack(BASE);
        item.editMeta(meta -> {
            meta.setItemModel(type.getKey());
            meta.displayName(name);
            meta.lore(lore);
            meta.setEnchantmentGlintOverride(true);
            meta.getPersistentDataContainer().set(magicJob, PersistentDataType.INTEGER, job);
            meta.getPersistentDataContainer().set(magicType, PersistentDataType.STRING, type.name());
        });
        return item;
    }

    /**
     * @param item an item, or null
     * @return the magic block it is, empty for any other item
     */
    public Optional<Magic> magic(@Nullable ItemStack item) {
        if (item == null || item.getType() != BASE || !item.hasItemMeta()) return Optional.empty();
        var data = item.getItemMeta().getPersistentDataContainer();
        var job = data.get(magicJob, PersistentDataType.INTEGER);
        var type = data.get(magicType, PersistentDataType.STRING);
        if (job == null || type == null) return Optional.empty();
        var material = Material.getMaterial(type);
        return material == null ? Optional.empty() : Optional.of(new Magic(job, material));
    }

    /**
     * @param item an item, or null
     * @return whether it is a magic block
     */
    public boolean isMagic(@Nullable ItemStack item) {
        return magic(item).isPresent();
    }

    /**
     * @param job  id of the job
     * @param name the name it shows
     * @param lore the lines below its name
     * @return a job chest item for the job, looking like a barrel
     */
    public ItemStack chest(int job, Component name, List<Component> lore) {
        var item = new ItemStack(BASE);
        item.editMeta(meta -> {
            meta.setItemModel(CHEST.getKey());
            meta.displayName(name);
            meta.lore(lore);
            meta.setEnchantmentGlintOverride(true);
            meta.getPersistentDataContainer().set(chestJob, PersistentDataType.INTEGER, job);
        });
        return item;
    }

    /**
     * @param item an item, or null
     * @return the job of the job chest item it is, empty for any other item
     */
    public OptionalInt chest(@Nullable ItemStack item) {
        if (item == null || item.getType() != BASE || !item.hasItemMeta()) return OptionalInt.empty();
        return job(item.getItemMeta().getPersistentDataContainer());
    }

    /**
     * Marks a placed block as the job chest of a job.
     *
     * @param block the placed block
     * @param job   id of the job
     */
    public void mark(Block block, int job) {
        if (!(block.getState() instanceof TileState state)) return;
        state.getPersistentDataContainer().set(chestJob, PersistentDataType.INTEGER, job);
        state.update(true, false);
    }

    /**
     * @param block a block
     * @return the job of the job chest it is, empty for any other block
     */
    public OptionalInt chest(Block block) {
        if (block.getType() != CHEST || !(block.getState() instanceof TileState state)) return OptionalInt.empty();
        return job(state.getPersistentDataContainer());
    }

    private OptionalInt job(PersistentDataContainer data) {
        var job = data.get(chestJob, PersistentDataType.INTEGER);
        return job == null ? OptionalInt.empty() : OptionalInt.of(job);
    }
}
