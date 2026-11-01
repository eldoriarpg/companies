/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.demo;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.List;

/**
 * The guide of the demo: a written book that explains what to try, given with {@code /demo guide} and, where
 * {@code onFirstJoin} is on, to a player joining for the first time. Its title, author and pages are MiniMessage from
 * the {@code guide} section of the config; the nightly reset forgets who joined, so every visitor gets it once a day.
 *
 * @param title       the title of the book
 * @param author      its author
 * @param pages       its pages
 * @param onFirstJoin whether a player joining for the first time gets it
 */
record GuideBook(String title, String author, List<String> pages, boolean onFirstJoin) implements Listener {
    /**
     * Copies the pages.
     */
    GuideBook {
        pages = List.copyOf(pages);
    }

    /**
     * @param guide the {@code guide} section
     * @return the guide
     */
    static GuideBook of(ConfigurationSection guide) {
        return new GuideBook(
                guide.getString("title", "Companies demo"),
                guide.getString("author", "Companies"),
                guide.getStringList("pages"),
                guide.getBoolean("onFirstJoin"));
    }

    /**
     * @return the pages as they are written into the book
     */
    List<Component> pageTexts() {
        var mini = MiniMessage.miniMessage();
        return pages.stream().map(mini::deserialize).toList();
    }

    /**
     * @return the book
     */
    ItemStack book() {
        var book = new ItemStack(Material.WRITTEN_BOOK);
        var meta = (BookMeta) book.getItemMeta();
        var mini = MiniMessage.miniMessage();
        meta.title(mini.deserialize(title));
        meta.author(mini.deserialize(author));
        meta.addPages(pageTexts().toArray(Component[]::new));
        book.setItemMeta(meta);
        return book;
    }

    /**
     * Gives the book, dropping it where the player stands when the inventory is full.
     *
     * @param player the player
     */
    void give(Player player) {
        for (var left : player.getInventory().addItem(book()).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), left);
        }
    }

    /**
     * Gives the book to a player joining for the first time.
     *
     * @param event the join
     */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (onFirstJoin && !event.getPlayer().hasPlayedBefore()) give(event.getPlayer());
    }
}
