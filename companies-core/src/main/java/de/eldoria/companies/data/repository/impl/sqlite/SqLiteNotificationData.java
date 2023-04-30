/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.sqlite;

import de.eldoria.companies.data.repository.impl.mariadb.MariaDbNotificationData;
import de.eldoria.companies.services.notifications.MissedNotifications;
import de.eldoria.companies.services.notifications.Notification;
import de.eldoria.companies.services.notifications.NotificationData;
import org.bukkit.OfflinePlayer;
import org.intellij.lang.annotations.Language;

import java.util.concurrent.ExecutorService;

import static de.eldoria.companies.data.StaticQueryAdapter.builder;

public class SqLiteNotificationData extends MariaDbNotificationData {

    /**
     * Create a new QueryFactoryholder
     *
     * @param executorService executor for futures
     */
    public SqLiteNotificationData(ExecutorService executorService) {
        super(executorService);
    }

    @Override
    protected MissedNotifications getMissedNotifications(OfflinePlayer player) {
        @Language("sqlite")
        var query = """
                SELECT created, notification_data
                FROM company_notification
                WHERE user_uuid = ?""";
        var notifications = builder(Notification.class)
                .query(query)
                .parameter(stmt -> stmt.setUuidAsBytes(player.getUniqueId()))
                .readRow(rs -> new Notification(
                        SqLiteAdapter.getTimestamp(rs, "created"),
                        NotificationData.fromJson(rs.getString("notification_data"))))
                .allSync();
        return MissedNotifications.create(notifications);
    }
}
