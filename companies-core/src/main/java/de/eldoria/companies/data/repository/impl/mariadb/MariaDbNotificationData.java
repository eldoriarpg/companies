/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.mariadb;

import de.eldoria.companies.data.repository.ANotificationData;
import de.eldoria.companies.services.notifications.MissedNotifications;
import de.eldoria.companies.services.notifications.Notification;
import de.eldoria.companies.services.notifications.NotificationData;
import org.bukkit.OfflinePlayer;
import org.intellij.lang.annotations.Language;

import java.util.concurrent.ExecutorService;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_BYTES;

public class MariaDbNotificationData extends ANotificationData {
    /**
     * Create a new QueryFactoryholder
     *
     * @param executorService executor for futures
     */
    public MariaDbNotificationData(ExecutorService executorService) {
        super(executorService);
    }

    @Override
    protected MissedNotifications getMissedNotifications(OfflinePlayer player) {
        @Language("mariadb")
        var query = """
                SELECT created, notification_data
                FROM company_notification
                WHERE user_uuid = ?""";
        var notifications = query(query)
                .single(call().bind(player.getUniqueId(), UUID_BYTES))
                .map(rs -> new Notification(
                        rs.getTimestamp("created")
                                .toLocalDateTime(),
                        NotificationData.fromJson(rs.getString("notification_data"))))
                .all();
        return MissedNotifications.create(notifications);
    }

    @Override
    protected void saveNotifications(OfflinePlayer player, NotificationData notificationData) {
        @Language("mariadb")
        var query = """
                INSERT INTO company_notification(user_uuid, notification_data)
                VALUES (?, ?)""";
        query(query)
                .single(call().bind(player.getUniqueId(), UUID_BYTES)
                        .bind(notificationData.toJson()))
                .update();
    }

    @Override
    protected void clearNotifications(OfflinePlayer player) {
        @Language("mariadb")
        var query = """
                DELETE
                FROM company_notification
                WHERE user_uuid = ?""";
        query(query)
                .single(call().bind(player.getUniqueId(), UUID_BYTES))
                .update();
    }
}
