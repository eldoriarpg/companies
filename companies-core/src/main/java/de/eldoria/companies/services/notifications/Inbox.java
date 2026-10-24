/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services.notifications;

import de.eldoria.companies.configuration.elements.NotificationSettings;
import de.eldoria.companies.data.repository.AInboxData;
import de.eldoria.companies.data.wrapper.inbox.InboxEntry;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * What a player does with their own inbox: reading it a page at a time, marking and clearing read entries, and muting
 * categories. Works for every player, with or without a company.
 */
public final class Inbox {
    private final AInboxData data;
    private final Supplier<NotificationSettings> settings;

    /**
     * @param data     the stored notifications
     * @param settings current notification settings
     */
    public Inbox(AInboxData data, Supplier<NotificationSettings> settings) {
        this.data = data;
        this.settings = settings;
    }

    /**
     * Reads a page and marks its unread entries read, because the player sees them in full now.
     *
     * @param player player whose inbox is read
     * @param number page number, from one
     * @param now    current time
     * @return the page as it was before it was marked read
     */
    public CompletableFuture<InboxPage> page(UUID player, int number, LocalDateTime now) {
        var page = Math.max(1, number);
        var size = settings.get().pageSize();
        var entries = data.retrievePage(player, page, size);
        var following = data.retrievePage(player, page * size + 1, 1);
        return entries.thenCombine(following, (shown, next) -> new InboxPage(shown, page, !next.isEmpty(), now))
                .thenCompose(shown -> markShown(player, shown, now).thenApply(marked -> shown));
    }

    private CompletableFuture<Integer> markShown(UUID player, InboxPage page, LocalDateTime now) {
        var unread = page.entries().stream()
                .filter(entry -> !entry.read())
                .map(InboxEntry::id)
                .toList();
        return data.submitRead(player, unread, now);
    }

    /**
     * Marks every unread entry read.
     *
     * @param player player whose inbox is changed
     * @param now    current time
     * @return number of entries marked
     */
    public CompletableFuture<Integer> readAll(UUID player, LocalDateTime now) {
        return data.submitAllRead(player, now);
    }

    /**
     * Deletes the read entries.
     *
     * @param player player whose inbox is changed
     * @return number of entries deleted
     */
    public CompletableFuture<Integer> clearRead(UUID player) {
        return data.submitReadDeletion(player);
    }

    /**
     * The categories a player muted.
     *
     * @param player player
     * @return mute mask
     */
    public CompletableFuture<Long> muted(UUID player) {
        return data.retrieveMuted(player);
    }

    /**
     * Mutes or unmutes a category.
     *
     * @param player   player
     * @param category a mutable category
     * @param mute     true to mute, false to unmute
     * @return the mute mask now stored; the old one when storing failed
     * @throws IllegalArgumentException when the category cannot be muted
     */
    public CompletableFuture<Long> mute(UUID player, NotificationCategory category, boolean mute) {
        if (!category.mutable()) throw new IllegalArgumentException("Category cannot be muted: " + category);
        return data.retrieveMuted(player).thenCompose(mask -> {
            var changed = mute ? mask | category.bit() : mask & ~category.bit();
            return data.submitMuted(player, changed).thenApply(stored -> Boolean.TRUE.equals(stored) ? changed : mask);
        });
    }
}
