package de.eldoria.companies.services.notifications;

import com.google.gson.internal.LinkedHashTreeMap;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class MissedNotifications implements Iterable<Map.Entry<LocalDate, List<Notification>>> {
    private final Map<LocalDate, List<Notification>> missed;

    private MissedNotifications(Map<LocalDate, List<Notification>> missed) {
        this.missed = missed;
    }

    public static MissedNotifications create(List<Notification> notifications) {
        Map<LocalDate, List<Notification>> missed = new LinkedHashTreeMap<>(LocalDate::compareTo);
        for (var notification : notifications) {
            missed.computeIfAbsent(notification.created().toLocalDate(), k -> new ArrayList<>()).add(notification);
        }
        return new MissedNotifications(missed);
    }

    public boolean isEmpty() {
        return missed.isEmpty();
    }

    @NotNull
    @Override
    public Iterator<Map.Entry<LocalDate, List<Notification>>> iterator() {
        return missed.entrySet().iterator();
    }
}
