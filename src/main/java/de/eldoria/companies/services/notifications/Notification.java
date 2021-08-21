package de.eldoria.companies.services.notifications;

import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;

public class Notification implements Comparable<Notification> {
    private final LocalDateTime created;
    private final NotificationData data;

    public Notification(LocalDateTime created, NotificationData data) {
        this.created = created;
        this.data = data;
    }

    @Override
    public int compareTo(@NotNull Notification o) {
        return created.compareTo(o.created);
    }

    public LocalDateTime created() {
        return created;
    }

    public NotificationData data() {
        return data;
    }
}
