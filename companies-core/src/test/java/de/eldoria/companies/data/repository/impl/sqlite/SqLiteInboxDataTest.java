/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.sqlite;

import de.eldoria.companies.data.wrapper.inbox.InboxEntry;
import de.eldoria.companies.data.wrapper.inbox.InboxStatus;
import de.eldoria.companies.services.notifications.NotificationArgs;
import de.eldoria.companies.services.notifications.NotificationCategory;
import de.eldoria.companies.services.notifications.NotificationType;
import de.eldoria.companies.util.TestExecutors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_BYTES;

class SqLiteInboxDataTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 1, 12, 0, 0);

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    @TempDir
    Path directory;

    private ExecutorService executor;
    private SqLiteInboxData inbox;

    @BeforeEach
    void setUp() throws Exception {
        SqLiteTestDatabase.create(directory);
        executor = Executors.newFixedThreadPool(8);
        inbox = new SqLiteInboxData(executor);
    }

    @AfterEach
    void tearDown() {
        TestExecutors.drain(executor);
    }

    private long send(UUID recipient, NotificationType type, LocalDateTime created, InboxStatus status) {
        return inbox.submit(recipient, type, args(), created, status).join().orElseThrow();
    }

    private static NotificationArgs args() {
        return NotificationArgs.create().text("company_name", "Miners").money("amount", "$5.00");
    }

    private static List<Long> ids(List<InboxEntry> entries) {
        return entries.stream().map(InboxEntry::id).toList();
    }

    @Test
    void entriesKeepTypeArgumentsAndStatus() {
        var pending = send(alice, NotificationType.INVITE_RECEIVED, NOW, InboxStatus.PENDING);
        var muted = send(alice, NotificationType.PROFILE_CHANGED, NOW.plusMinutes(1), InboxStatus.DELIVERED);
        var read = send(alice, NotificationType.INVITE_REVOKED, NOW.plusMinutes(2), InboxStatus.READ);

        var page = inbox.retrievePage(alice, 1, 10).join();

        Assertions.assertEquals(List.of(read, muted, pending), ids(page));
        var readEntry = page.get(0);
        Assertions.assertEquals(NotificationType.INVITE_REVOKED, readEntry.type());
        Assertions.assertEquals(NOW.plusMinutes(2), readEntry.created());
        Assertions.assertTrue(readEntry.delivered());
        Assertions.assertEquals(NOW.plusMinutes(2), readEntry.readAt());
        Assertions.assertTrue(page.get(1).delivered());
        Assertions.assertFalse(page.get(1).read());
        Assertions.assertFalse(page.get(2).delivered());
        Assertions.assertEquals(args().values(), page.get(2).args().values());
    }

    @Test
    void pendingEntriesOfSeveralPlayersAreReturnedOldestFirstUpToTheLimit() {
        var first = send(bob, NotificationType.INVITE_RECEIVED, NOW, InboxStatus.PENDING);
        var second = send(alice, NotificationType.INVITE_RECEIVED, NOW.plusMinutes(1), InboxStatus.PENDING);
        send(alice, NotificationType.INVITE_RECEIVED, NOW.plusMinutes(2), InboxStatus.READ);
        send(alice, NotificationType.INVITE_RECEIVED, NOW.plusMinutes(3), InboxStatus.DELIVERED);
        var third = send(alice, NotificationType.INVITE_RECEIVED, NOW.plusMinutes(4), InboxStatus.PENDING);
        send(UUID.randomUUID(), NotificationType.INVITE_RECEIVED, NOW, InboxStatus.PENDING);

        Assertions.assertEquals(
                List.of(first, second, third),
                ids(inbox.retrievePending(List.of(alice, bob), 10).join()));
        Assertions.assertEquals(
                List.of(first, second),
                ids(inbox.retrievePending(List.of(alice, bob), 2).join()));
        Assertions.assertEquals(List.of(), inbox.retrievePending(List.of(), 10).join());
    }

    @Test
    void manyPlayersAreAskedForInChunks() {
        List<UUID> players = new ArrayList<>();
        for (var i = 0; i < 1200; i++) players.add(UUID.randomUUID());
        var last = send(players.get(1199), NotificationType.INVITE_RECEIVED, NOW, InboxStatus.PENDING);

        Assertions.assertEquals(
                List.of(last), ids(inbox.retrievePending(players, 10).join()));
    }

    @Test
    void anEntryIsClaimedExactlyOnce() {
        var id = send(alice, NotificationType.INVITE_RECEIVED, NOW, InboxStatus.PENDING);

        List<CompletableFuture<Boolean>> claims = new ArrayList<>();
        for (var i = 0; i < 8; i++) claims.add(inbox.submitClaim(id, NOW.plusMinutes(1)));
        var won = claims.stream()
                .map(CompletableFuture::join)
                .filter(Boolean::booleanValue)
                .count();

        Assertions.assertEquals(1, won);
        var entry = inbox.retrievePage(alice, 1, 1).join().getFirst();
        Assertions.assertTrue(entry.delivered());
        Assertions.assertEquals(NOW.plusMinutes(1), entry.readAt());
        Assertions.assertEquals(
                List.of(), inbox.retrievePending(List.of(alice), 10).join());
        Assertions.assertFalse(inbox.submitClaim(id + 100, NOW).join());
    }

    @Test
    void aJoinSummaryDeliversWithoutReading() {
        send(alice, NotificationType.INVITE_RECEIVED, NOW, InboxStatus.PENDING);
        send(alice, NotificationType.INVITE_RECEIVED, NOW, InboxStatus.PENDING);
        send(alice, NotificationType.INVITE_RECEIVED, NOW, InboxStatus.READ);
        send(bob, NotificationType.INVITE_RECEIVED, NOW, InboxStatus.PENDING);

        Assertions.assertEquals(2, inbox.submitDelivered(alice).join());
        Assertions.assertEquals(0, inbox.submitDelivered(alice).join());
        Assertions.assertEquals(2, inbox.retrieveUnreadCount(alice, 0).join());
        Assertions.assertEquals(
                1, inbox.retrievePending(List.of(alice, bob), 10).join().size());
    }

    @Test
    void mutedCategoriesAreLeftOutOfTheUnreadCount() {
        send(alice, NotificationType.INVITE_RECEIVED, NOW, InboxStatus.PENDING);
        send(alice, NotificationType.PROFILE_CHANGED, NOW, InboxStatus.DELIVERED);
        send(alice, NotificationType.APPLICATION_DENIED, NOW, InboxStatus.DELIVERED);
        send(alice, NotificationType.INVITE_REVOKED, NOW, InboxStatus.READ);

        Assertions.assertEquals(3, inbox.retrieveUnreadCount(alice, 0).join());
        Assertions.assertEquals(
                1,
                inbox.retrieveUnreadCount(alice, NotificationCategory.INVITES.bit())
                        .join());
        Assertions.assertEquals(
                0,
                inbox.retrieveUnreadCount(
                                alice,
                                NotificationCategory.mask(
                                        Set.of(NotificationCategory.INVITES, NotificationCategory.COMPANY)))
                        .join());
        Assertions.assertEquals(
                3,
                inbox.retrieveUnreadCount(alice, NotificationCategory.PAYMENTS.bit())
                        .join());
    }

    @Test
    void pagesAreNewestFirst() {
        List<Long> sent = new ArrayList<>();
        for (var i = 0; i < 5; i++) {
            sent.add(send(alice, NotificationType.INVITE_RECEIVED, NOW.plusMinutes(i), InboxStatus.READ));
        }

        Assertions.assertEquals(
                List.of(sent.get(4), sent.get(3)),
                ids(inbox.retrievePage(alice, 1, 2).join()));
        Assertions.assertEquals(
                List.of(sent.get(0)), ids(inbox.retrievePage(alice, 3, 2).join()));
        Assertions.assertEquals(List.of(), inbox.retrievePage(bob, 1, 2).join());
    }

    @Test
    void readingMarksOnlyOwnUnreadEntriesAndDeliversThem() {
        var pending = send(alice, NotificationType.INVITE_RECEIVED, NOW, InboxStatus.PENDING);
        var read = send(alice, NotificationType.INVITE_RECEIVED, NOW, InboxStatus.READ);
        var foreign = send(bob, NotificationType.INVITE_RECEIVED, NOW, InboxStatus.PENDING);

        Assertions.assertEquals(
                1,
                inbox.submitRead(alice, List.of(pending, read, foreign), NOW.plusHours(1))
                        .join());

        Assertions.assertEquals(0, inbox.retrieveUnreadCount(alice, 0).join());
        Assertions.assertEquals(1, inbox.retrieveUnreadCount(bob, 0).join());
        Assertions.assertEquals(
                List.of(), inbox.retrievePending(List.of(alice), 10).join());
        Assertions.assertEquals(
                NOW,
                inbox.retrievePage(alice, 1, 10).join().stream()
                        .filter(entry -> entry.id() == read)
                        .findFirst()
                        .orElseThrow()
                        .readAt());
        Assertions.assertEquals(0, inbox.submitRead(alice, List.of(), NOW).join());
    }

    @Test
    void allEntriesCanBeReadAndReadOnesCleared() {
        send(alice, NotificationType.INVITE_RECEIVED, NOW, InboxStatus.PENDING);
        send(alice, NotificationType.INVITE_RECEIVED, NOW, InboxStatus.DELIVERED);
        send(bob, NotificationType.INVITE_RECEIVED, NOW, InboxStatus.READ);

        Assertions.assertEquals(2, inbox.submitAllRead(alice, NOW).join());
        Assertions.assertEquals(0, inbox.retrieveUnreadCount(alice, 0).join());
        var unread = send(alice, NotificationType.INVITE_RECEIVED, NOW, InboxStatus.PENDING);

        Assertions.assertEquals(2, inbox.submitReadDeletion(alice).join());
        Assertions.assertEquals(
                List.of(unread), ids(inbox.retrievePage(alice, 1, 10).join()));
        Assertions.assertEquals(1, inbox.retrievePage(bob, 1, 10).join().size());
    }

    @Test
    void retentionDeletesOldEntriesReadOrNot() {
        send(alice, NotificationType.INVITE_RECEIVED, NOW.minusDays(40), InboxStatus.PENDING);
        send(alice, NotificationType.INVITE_RECEIVED, NOW.minusDays(31), InboxStatus.READ);
        var kept = send(alice, NotificationType.INVITE_RECEIVED, NOW.minusDays(29), InboxStatus.PENDING);

        Assertions.assertEquals(2, inbox.submitPurge(NOW.minusDays(30)).join());
        Assertions.assertEquals(
                List.of(kept), ids(inbox.retrievePage(alice, 1, 10).join()));
    }

    @Test
    void muteMasksAreStoredPerPlayer() {
        Assertions.assertEquals(0L, inbox.retrieveMuted(alice).join());

        Assertions.assertTrue(
                inbox.submitMuted(alice, NotificationCategory.ORDERS.bit()).join());
        Assertions.assertTrue(
                inbox.submitMuted(alice, NotificationCategory.LEVEL.bit()).join());
        Assertions.assertTrue(
                inbox.submitMuted(alice, NotificationCategory.LEVEL.bit()).join());

        Assertions.assertEquals(
                NotificationCategory.LEVEL.bit(), inbox.retrieveMuted(alice).join());
        Assertions.assertEquals(0L, inbox.retrieveMuted(bob).join());
    }

    @Test
    void unreadableRowsAreShownAsLegacyText() {
        query("INSERT INTO company_inbox(user_uuid, type, args, created) VALUES (?, 999, 'not json', ?)")
                .single(call().bind(alice, UUID_BYTES).bind(SqLiteAdapter.format(NOW)))
                .insert();
        query("INSERT INTO company_inbox(user_uuid, type, args, created) VALUES (?, 999, '{\"message\":\"hi\"}', ?)")
                .single(call().bind(alice, UUID_BYTES).bind(SqLiteAdapter.format(NOW)))
                .insert();

        var entries = inbox.retrievePage(alice, 1, 10).join();

        Assertions.assertEquals(2, entries.size());
        for (var entry : entries) Assertions.assertEquals(NotificationType.LEGACY, entry.type());
        Assertions.assertEquals(
                List.of("hi", "not json"),
                entries.stream()
                        .map(entry -> entry.args()
                                .values()
                                .get(NotificationArgs.LEGACY_MESSAGE)
                                .value())
                        .toList());
    }
}
