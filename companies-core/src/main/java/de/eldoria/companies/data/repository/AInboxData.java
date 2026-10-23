/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository;

import de.eldoria.companies.data.wrapper.inbox.InboxEntry;
import de.eldoria.companies.data.wrapper.inbox.InboxStatus;
import de.eldoria.companies.services.notifications.NotificationArgs;
import de.eldoria.companies.services.notifications.NotificationType;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * The notification inbox of every player and the categories they muted.
 * <p>
 * Every notification is stored, whether its recipient is online or not. A pending entry is delivered exactly once:
 * {@link #submitClaim(long, LocalDateTime)} succeeds for one caller only, so a player who switches servers between a
 * poll and the display never sees an entry twice. Times are bound from Java, never taken from the database clock. All
 * futures complete on the executor given to the constructor.
 */
public abstract class AInboxData {
    /**
     * The most players one statement asks for; longer lists are split.
     */
    protected static final int PLAYER_CHUNK = 500;

    private final ExecutorService executor;

    protected AInboxData(ExecutorService executor) {
        this.executor = executor;
    }

    /**
     * Stores a notification.
     *
     * @param recipient player the notification is for
     * @param type      kind of notification
     * @param args      values for the placeholders of the message
     * @param created   current time
     * @param status    whether it is pending, delivered or read; a read entry is read at {@code created}
     * @return id of the entry, or empty when it could not be stored
     */
    public CompletableFuture<Optional<Long>> submit(
            UUID recipient, NotificationType type, NotificationArgs args, LocalDateTime created, InboxStatus status) {
        return CompletableFuture.supplyAsync(() -> save(recipient, type, args, created, status), executor);
    }

    protected abstract Optional<Long> save(
            UUID recipient, NotificationType type, NotificationArgs args, LocalDateTime created, InboxStatus status);

    /**
     * The pending entries of some players, oldest first.
     *
     * @param recipients players to look for, usually the ones online on this server
     * @param limit      most entries to return
     * @return pending entries
     */
    public CompletableFuture<List<InboxEntry>> retrievePending(Collection<UUID> recipients, int limit) {
        var players = List.copyOf(recipients);
        return CompletableFuture.supplyAsync(() -> getPending(players, limit), executor);
    }

    protected abstract List<InboxEntry> getPending(List<UUID> recipients, int limit);

    /**
     * Marks a pending entry as delivered and read, because it is about to be shown live.
     *
     * @param id  id of the entry
     * @param now current time
     * @return true for exactly one caller; false when the entry was delivered already or is gone
     */
    public CompletableFuture<Boolean> submitClaim(long id, LocalDateTime now) {
        return CompletableFuture.supplyAsync(() -> claim(id, now), executor);
    }

    protected abstract boolean claim(long id, LocalDateTime now);

    /**
     * Marks every pending entry of a player as delivered without reading them, because a join summary counts them.
     *
     * @param recipient player
     * @return number of entries marked
     */
    public CompletableFuture<Integer> submitDelivered(UUID recipient) {
        return CompletableFuture.supplyAsync(() -> markDelivered(recipient), executor);
    }

    protected abstract int markDelivered(UUID recipient);

    /**
     * Counts the unread entries of a player, leaving out muted categories.
     *
     * @param recipient player
     * @param mutedMask muted categories, see {@link de.eldoria.companies.services.notifications.NotificationCategory}
     * @return number of unread entries
     */
    public CompletableFuture<Integer> retrieveUnreadCount(UUID recipient, long mutedMask) {
        return CompletableFuture.supplyAsync(() -> countUnread(recipient, mutedMask), executor);
    }

    protected abstract int countUnread(UUID recipient, long mutedMask);

    /**
     * One page of a player's inbox, newest first.
     *
     * @param recipient player
     * @param page      page, starting at 1
     * @param size      entries per page
     * @return entries of the page
     */
    public CompletableFuture<List<InboxEntry>> retrievePage(UUID recipient, int page, int size) {
        return CompletableFuture.supplyAsync(() -> getPage(recipient, page, size), executor);
    }

    protected abstract List<InboxEntry> getPage(UUID recipient, int page, int size);

    /**
     * Marks entries of a player as read. Entries of other players and entries read before are left alone.
     *
     * @param recipient player
     * @param ids       ids of the entries
     * @param now       current time
     * @return number of entries marked
     */
    public CompletableFuture<Integer> submitRead(UUID recipient, Collection<Long> ids, LocalDateTime now) {
        var entries = List.copyOf(ids);
        if (entries.isEmpty()) return CompletableFuture.completedFuture(0);
        return CompletableFuture.supplyAsync(() -> markRead(recipient, entries, now), executor);
    }

    protected abstract int markRead(UUID recipient, List<Long> ids, LocalDateTime now);

    /**
     * Marks every unread entry of a player as read.
     *
     * @param recipient player
     * @param now       current time
     * @return number of entries marked
     */
    public CompletableFuture<Integer> submitAllRead(UUID recipient, LocalDateTime now) {
        return CompletableFuture.supplyAsync(() -> markAllRead(recipient, now), executor);
    }

    protected abstract int markAllRead(UUID recipient, LocalDateTime now);

    /**
     * Deletes the read entries of a player.
     *
     * @param recipient player
     * @return number of entries deleted
     */
    public CompletableFuture<Integer> submitReadDeletion(UUID recipient) {
        return CompletableFuture.supplyAsync(() -> deleteRead(recipient), executor);
    }

    protected abstract int deleteRead(UUID recipient);

    /**
     * Deletes every entry sent before a point in time, read or not.
     *
     * @param before entries older than this are deleted
     * @return number of entries deleted
     */
    public CompletableFuture<Integer> submitPurge(LocalDateTime before) {
        return CompletableFuture.supplyAsync(() -> purgeBefore(before), executor);
    }

    protected abstract int purgeBefore(LocalDateTime before);

    /**
     * The categories a player muted.
     *
     * @param player player
     * @return mute mask, 0 when nothing is muted
     */
    public CompletableFuture<Long> retrieveMuted(UUID player) {
        return CompletableFuture.supplyAsync(() -> getMuted(player), executor);
    }

    protected abstract long getMuted(UUID player);

    /**
     * Stores the categories a player muted.
     *
     * @param player player
     * @param mask   mute mask
     * @return true when the mask was stored
     */
    public CompletableFuture<Boolean> submitMuted(UUID player, long mask) {
        return CompletableFuture.supplyAsync(() -> saveMuted(player, mask), executor);
    }

    protected abstract boolean saveMuted(UUID player, long mask);
}
