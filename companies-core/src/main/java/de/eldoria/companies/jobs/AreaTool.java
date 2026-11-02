/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.jobs;

import de.eldoria.companies.menus.MenuComponents;
import de.eldoria.companies.menus.Values;
import de.eldoria.eldoutilities.messages.MessageSender;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The area tool: an item that marks the corners of a player's area. A left click on a block marks corner 1, a right
 * click corner 2; the click does nothing else, nor does the item in the other hand. Any item carrying the tool's tag is
 * the tool, so renaming it keeps it. It is a feather, which is worth nothing and is no other plugin's tool: a golden
 * shovel would melt down into gold and is the claim tool of GriefPrevention, a wooden axe is WorldEdit's wand.
 */
public final class AreaTool implements Listener {
    private final NamespacedKey key;
    private final StoredAreas areas;
    private final MessageSender messages;

    /**
     * @param plugin   the plugin, which owns the tool's tag
     * @param areas    keeps the marked corners
     * @param messages tells players what they marked
     */
    public AreaTool(Plugin plugin, StoredAreas areas, MessageSender messages) {
        this.key = new NamespacedKey(plugin, "area_tool");
        this.areas = areas;
        this.messages = messages;
    }

    /**
     * @param player the player it is made for, in whose language it is named
     * @return a new area tool
     */
    public ItemStack create(Player player) {
        var text = new MenuComponents(messages, player);
        var item = new ItemStack(Material.FEATHER);
        item.editMeta(meta -> {
            meta.itemName(text.locale("order.job.area.toolName"));
            meta.lore(List.of(text.locale("order.job.area.toolLore")));
            meta.getPersistentDataContainer().set(key, PersistentDataType.BOOLEAN, true);
            meta.setEnchantmentGlintOverride(true);
        });
        return item;
    }

    /**
     * @param player a player
     * @return whether the player carries an area tool
     */
    public boolean carries(Player player) {
        for (var item : player.getInventory().getContents()) {
            if (is(item)) return true;
        }
        return false;
    }

    /**
     * @param item an item
     * @return whether it is the area tool
     */
    public boolean is(@Nullable ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        var meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(key);
    }

    /**
     * Marks a corner where a player clicks a block with the tool.
     *
     * @param event the click
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onClick(PlayerInteractEvent event) {
        var block = event.getClickedBlock();
        var action = event.getAction();
        if (block == null || (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK)) return;
        if (!is(event.getPlayer().getInventory().getItemInMainHand())) return;
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) return;
        mark(event.getPlayer(), action == Action.RIGHT_CLICK_BLOCK, block);
    }

    /**
     * Marks a corner of a player's area and tells them, with the size of the area once both corners are marked.
     *
     * @param player the player
     * @param second true for corner 2, false for corner 1
     * @param block  the block marked
     */
    public void mark(Player player, boolean second, Block block) {
        var corner = new StoredAreas.Corner(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
        var area = areas.mark(player.getUniqueId(), second, corner);
        messages.sendMessage(
                player,
                "order.job.area.corner",
                Values.number("corner", second ? 2 : 1),
                Values.number("x", block.getX()),
                Values.number("y", block.getY()),
                Values.number("z", block.getZ()));
        area.ifPresent(marked -> messages.sendMessage(
                player,
                "order.job.area.marked",
                Values.number("width", marked.maxX() - marked.minX() + 1),
                Values.number("height", marked.maxY() - marked.minY() + 1),
                Values.number("length", marked.maxZ() - marked.minZ() + 1)));
    }
}
