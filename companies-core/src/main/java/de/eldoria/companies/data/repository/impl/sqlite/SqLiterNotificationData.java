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
import org.bukkit.plugin.Plugin;

import javax.sql.DataSource;
import java.util.concurrent.ExecutorService;

public class SqLiterNotificationData extends MariaDbNotificationData {

    /**
     * Create a new QueryFactoryholder
     *
     * @param dataSource      datasource
     * @param executorService
     */
    public SqLiterNotificationData(DataSource dataSource, Plugin plugin, ExecutorService executorService) {
        super(dataSource, plugin, executorService);
    }

    @Override
    protected MissedNotifications getMissedNotifications(OfflinePlayer player) {
        var notifications = builder(Notification.class)
                .query("SELECT created, notification_data FROM company_notification WHERE user_uuid = ?")
                .parameter(stmt -> stmt.setUuidAsBytes(player.getUniqueId()))
                .readRow(rs -> new Notification(
                        SqLiteAdapter.getTimestamp(rs, "created"),
                        NotificationData.fromJson(rs.getString("notification_data"))))
                .allSync();
        return MissedNotifications.create(notifications);
    }
}
