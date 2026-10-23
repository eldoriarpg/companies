/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services.notifications;

import de.eldoria.companies.data.repository.AInboxData;
import de.eldoria.companies.data.wrapper.inbox.InboxEntry;
import de.eldoria.companies.data.wrapper.inbox.InboxStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

/**
 * An inbox held in memory with the semantics of the database implementations.
 */
final class MemoryInboxData extends AInboxData {
    private final List<InboxEntry> entries = new ArrayList<>();
    private final Map<UUID, Long> muted = new HashMap<>();
    private long nextId = 1;

    MemoryInboxData(ExecutorService executor) {
        super(executor);
    }

    synchronized List<InboxEntry> all() {
        return List.copyOf(entries);
    }

    synchronized InboxEntry only() {
        if (entries.size() != 1) throw new AssertionError("Expected one entry, found " + entries);
        return entries.getFirst();
    }

    @Override
    protected synchronized Optional<Long> save(
            UUID recipient, NotificationType type, NotificationArgs args, LocalDateTime created, InboxStatus status) {
        var id = nextId++;
        entries.add(new InboxEntry(
                id,
                recipient,
                type,
                args,
                created,
                status != InboxStatus.PENDING,
                status == InboxStatus.READ ? created : null));
        return Optional.of(id);
    }

    @Override
    protected synchronized List<InboxEntry> getPending(List<UUID> recipients, int limit) {
        return entries.stream()
                .filter(entry -> !entry.delivered() && recipients.contains(entry.recipient()))
                .sorted(Comparator.comparing(InboxEntry::created).thenComparing(InboxEntry::id))
                .limit(limit)
                .toList();
    }

    @Override
    protected synchronized boolean claim(long id, LocalDateTime now) {
        for (var i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            if (entry.id() != id) continue;
            if (entry.delivered()) return false;
            entries.set(i, with(entry, true, now));
            return true;
        }
        return false;
    }

    @Override
    protected synchronized int markDelivered(UUID recipient) {
        var marked = 0;
        for (var i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            if (!entry.recipient().equals(recipient) || entry.delivered()) continue;
            entries.set(i, with(entry, true, entry.readAt()));
            marked++;
        }
        return marked;
    }

    @Override
    protected synchronized int countUnread(UUID recipient, long mutedMask) {
        return (int) entries.stream()
                .filter(entry -> entry.recipient().equals(recipient) && !entry.read())
                .filter(entry -> !entry.type().category().in(mutedMask))
                .count();
    }

    @Override
    protected synchronized List<InboxEntry> getPage(UUID recipient, int page, int size) {
        return entries.stream()
                .filter(entry -> entry.recipient().equals(recipient))
                .sorted(Comparator.comparing(InboxEntry::created)
                        .thenComparing(InboxEntry::id)
                        .reversed())
                .skip((long) (page - 1) * size)
                .limit(size)
                .toList();
    }

    @Override
    protected synchronized int markRead(UUID recipient, List<Long> ids, LocalDateTime now) {
        var marked = 0;
        for (var i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            if (!entry.recipient().equals(recipient) || entry.read() || !ids.contains(entry.id())) continue;
            entries.set(i, with(entry, true, now));
            marked++;
        }
        return marked;
    }

    @Override
    protected synchronized int markAllRead(UUID recipient, LocalDateTime now) {
        var ids = entries.stream()
                .filter(entry -> entry.recipient().equals(recipient))
                .map(InboxEntry::id)
                .toList();
        return markRead(recipient, ids, now);
    }

    @Override
    protected synchronized int deleteRead(UUID recipient) {
        var before = entries.size();
        entries.removeIf(entry -> entry.recipient().equals(recipient) && entry.read());
        return before - entries.size();
    }

    @Override
    protected synchronized int purgeBefore(LocalDateTime before) {
        var count = entries.size();
        entries.removeIf(entry -> entry.created().isBefore(before));
        return count - entries.size();
    }

    @Override
    protected synchronized long getMuted(UUID player) {
        return muted.getOrDefault(player, 0L);
    }

    @Override
    protected synchronized boolean saveMuted(UUID player, long mask) {
        muted.put(player, mask);
        return true;
    }

    private static InboxEntry with(InboxEntry entry, boolean delivered, LocalDateTime readAt) {
        return new InboxEntry(
                entry.id(), entry.recipient(), entry.type(), entry.args(), entry.created(), delivered, readAt);
    }
}
