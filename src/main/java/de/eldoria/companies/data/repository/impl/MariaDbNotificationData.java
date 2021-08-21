package de.eldoria.companies.data.repository.impl;

import de.chojo.sqlutil.conversion.UUIDConverter;
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
                .query("SELECT created, notification_data FROM notification WHERE user_uuid = ?")
                .paramsBuilder(stmt -> stmt.setBytes(UUIDConverter.convert(player.getUniqueId())))
                .readRow(rs -> new Notification(
                        rs.getTimestamp("created").toLocalDateTime(),
                        NotificationData.fromJson(rs.getString("notification_data"))))
                .allSync();
        return MissedNotifications.create(notifications);
    }

    @Override
    protected void saveNotifications(OfflinePlayer player, NotificationData notificationData) {
        builder()
                .query("INSERT INTO notification(user_uuid, notification_data) VALUES(?,?)")
                .paramsBuilder(stmt -> stmt.setBytes(UUIDConverter.convert(player.getUniqueId())).setString(notificationData.toJson()))
                .update()
                .executeSync();
    }

    @Override
    protected void clearNotifications(OfflinePlayer player) {
        builder().query("DELETE FROM notification WHERE user_uuid = ?")
                .paramsBuilder(stmt -> stmt.setBytes(UUIDConverter.convert(player.getUniqueId())))
                .update().executeSync();
    }
}
