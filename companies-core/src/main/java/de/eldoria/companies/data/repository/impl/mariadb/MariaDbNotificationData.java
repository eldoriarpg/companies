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
import org.bukkit.plugin.Plugin;

import javax.sql.DataSource;
import java.util.concurrent.ExecutorService;

public class MariaDbNotificationData extends ANotificationData {
    /**
     * Create a new QueryFactoryholder
     *
     * @param dataSource      datasource
     * @param executorService
     */
    public MariaDbNotificationData(DataSource dataSource, Plugin plugin, ExecutorService executorService) {
        super(plugin, dataSource, executorService);
    }

    @Override
    protected MissedNotifications getMissedNotifications(OfflinePlayer player) {
        var notifications = builder(Notification.class)
                .query("SELECT created, notification_data FROM company_notification WHERE user_uuid = ?")
                .parameter(stmt -> stmt.setUuidAsBytes(player.getUniqueId()))
                .readRow(rs -> new Notification(
                        rs.getTimestamp("created").toLocalDateTime(),
                        NotificationData.fromJson(rs.getString("notification_data"))))
                .allSync();
        return MissedNotifications.create(notifications);
    }

    @Override
    protected void saveNotifications(OfflinePlayer player, NotificationData notificationData) {
        builder()
                .query("INSERT INTO company_notification(user_uuid, notification_data) VALUES(?,?)")
                .parameter(stmt -> stmt.setUuidAsBytes(player.getUniqueId()).setString(notificationData.toJson()))
                .update()
                .sendSync();
    }

    @Override
    protected void clearNotifications(OfflinePlayer player) {
        builder().query("DELETE FROM company_notification WHERE user_uuid = ?")
                .parameter(stmt -> stmt.setUuidAsBytes(player.getUniqueId()))
                .update().sendSync();
    }
}
