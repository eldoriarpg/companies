/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.inbox;

import de.eldoria.companies.services.notifications.NotificationArgs;
import de.eldoria.companies.services.notifications.NotificationType;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * A stored notification.
 *
 * @param id        id of the entry
 * @param recipient player the notification is for
 * @param type      kind of notification; unknown stored types are read as {@link NotificationType#LEGACY}
 * @param args      values for the placeholders of the message
 * @param created   when it was sent
 * @param delivered whether it was shown live, counted in a join summary or muted
 * @param readAt    when it was read, or null while unread
 */
public record InboxEntry(
        long id,
        UUID recipient,
        NotificationType type,
        NotificationArgs args,
        LocalDateTime created,
        boolean delivered,
        @Nullable LocalDateTime readAt) {
    /**
     * @return true once the entry was read
     */
    public boolean read() {
        return readAt != null;
    }

    /**
     * @return when the entry was read, or empty while unread
     */
    public Optional<LocalDateTime> readTime() {
        return Optional.ofNullable(readAt);
    }
}
