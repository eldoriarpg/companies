package de.eldoria.companies.data.repository;

import de.chojo.sqlutil.base.QueryFactoryHolder;
import de.chojo.sqlutil.wrapper.QueryBuilderConfig;
import de.eldoria.companies.services.notifications.MissedNotifications;
import de.eldoria.companies.services.notifications.NotificationData;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

import javax.sql.DataSource;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;

public abstract class ANotificationData extends QueryFactoryHolder {

    private ExecutorService executorService;

    /**
     * Create a new QueryFactoryholder
     *
     * @param dataSource datasource
     */
    public ANotificationData(Plugin plugin, DataSource dataSource, ExecutorService executorService) {
        super(dataSource, QueryBuilderConfig.builder()
                .withExceptionHandler(e -> plugin.getLogger().log(Level.SEVERE, "Query exception", e))
                .build());
        this.executorService = executorService;
    }

    public CompletableFuture<MissedNotifications> retrieveNotifications(OfflinePlayer player) {
        return CompletableFuture.supplyAsync(() -> getMissedNotifications(player), executorService);
    }

    protected abstract MissedNotifications getMissedNotifications(OfflinePlayer player);

    public CompletableFuture<Void> submitNotifications(OfflinePlayer player, NotificationData notificationData) {
        return CompletableFuture.runAsync(() -> saveNotifications(player, notificationData), executorService);
    }

    protected abstract void saveNotifications(OfflinePlayer player, NotificationData notificationData);

    public CompletableFuture<Void> submitNotificationClear(OfflinePlayer player) {
        return CompletableFuture.runAsync(() -> clearNotifications(player), executorService);
    }

    protected abstract void clearNotifications(OfflinePlayer player);
}
