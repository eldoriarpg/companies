/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.postgres;

import de.eldoria.companies.data.repository.impl.mariadb.MariaDbNotificationData;
import de.eldoria.companies.services.notifications.NotificationData;
import org.bukkit.OfflinePlayer;
import org.intellij.lang.annotations.Language;

import java.util.concurrent.ExecutorService;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_BYTES;

public class PostgresNotificationData extends MariaDbNotificationData {
    /**
     * Create a new QueryFactoryholder
     *
     * @param executorService executor for futures
     */
    public PostgresNotificationData(ExecutorService executorService) {
        super(executorService);
    }

    @Override
    protected void saveNotifications(OfflinePlayer player, NotificationData notificationData) {
        @Language("postgresql")
        var query = """
                INSERT INTO company_notification(user_uuid, notification_data)
                VALUES (?, ?::jsonb)""";
        query(query)
                .single(call().bind(player.getUniqueId(), UUID_BYTES)
                        .bind(notificationData.toJson()))
                .update();
    }
}
