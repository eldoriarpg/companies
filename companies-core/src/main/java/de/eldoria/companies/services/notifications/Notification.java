/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services.notifications;

import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;

public record Notification(LocalDateTime created, NotificationData data) implements Comparable<Notification> {

    @Override
    public int compareTo(@NotNull Notification o) {
        return created.compareTo(o.created);
    }
}
