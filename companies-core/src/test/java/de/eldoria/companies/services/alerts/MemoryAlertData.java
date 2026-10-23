/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services.alerts;

import com.google.common.util.concurrent.MoreExecutors;
import de.eldoria.companies.data.repository.AAlertData;
import de.eldoria.companies.data.wrapper.alert.AlertResult;
import de.eldoria.companies.data.wrapper.alert.HitResult;
import de.eldoria.companies.data.wrapper.alert.OrderAlert;
import de.eldoria.companies.orders.query.OrderFilter;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Alerts kept in memory with the rules of the database: unique names per company, the limit checked on save, the
 * conditional rate limit and digest claims.
 */
final class MemoryAlertData extends AAlertData {
    private final Map<Integer, OrderAlert> alerts = new LinkedHashMap<>();
    private int nextId = 1;
    private int lastLimit = -1;

    MemoryAlertData() {
        super(null, MoreExecutors.newDirectExecutorService());
    }

    /**
     * Stores an enabled alert directly.
     *
     * @param company id of the company
     * @param name    name of the alert
     * @param filter  orders it matches
     * @return the stored alert
     */
    synchronized OrderAlert put(int company, String name, OrderFilter filter) {
        var alert = new OrderAlert(
                nextId++, company, name, filter, UUID.randomUUID(), true, LocalDateTime.MIN, Optional.empty(), 0);
        alerts.put(alert.id(), alert);
        return alert;
    }

    /**
     * @param id id of the alert
     * @return the alert as stored now
     */
    synchronized OrderAlert get(int id) {
        return alerts.get(id);
    }

    /**
     * @return the limit passed with the last save
     */
    int lastLimit() {
        return lastLimit;
    }

    @Override
    protected synchronized AlertResult saveAlert(
            int company, String name, OrderFilter filter, UUID creator, int maxAlerts, LocalDateTime now) {
        lastLimit = maxAlerts;
        var own = getByCompany(company);
        if (own.stream().anyMatch(alert -> alert.name().equals(name))) return AlertResult.NAME_TAKEN;
        if (own.size() >= maxAlerts) return AlertResult.LIMIT_REACHED;
        var alert = new OrderAlert(nextId++, company, name, filter, creator, true, now, Optional.empty(), 0);
        alerts.put(alert.id(), alert);
        return AlertResult.CREATED;
    }

    @Override
    protected synchronized List<OrderAlert> getByCompany(int company) {
        return alerts.values().stream()
                .filter(alert -> alert.company() == company)
                .sorted(Comparator.comparing(OrderAlert::name))
                .toList();
    }

    @Override
    protected synchronized List<OrderAlert> getEnabled() {
        return alerts.values().stream().filter(OrderAlert::enabled).toList();
    }

    @Override
    protected synchronized boolean remove(int company, String name) {
        return find(company, name)
                .map(alert -> alerts.remove(alert.id()) != null)
                .orElse(false);
    }

    @Override
    protected synchronized boolean saveEnabled(int company, String name, boolean enabled) {
        var found = find(company, name);
        found.ifPresent(alert -> alerts.put(alert.id(), with(alert, enabled, alert.lastNotified(), alert.pending())));
        return found.isPresent();
    }

    @Override
    protected synchronized HitResult hit(int id, LocalDateTime now, LocalDateTime notifiedBefore) {
        var alert = alerts.get(id);
        if (alert == null || !alert.enabled()) return HitResult.GONE;
        if (alert.lastNotified().map(last -> !last.isAfter(notifiedBefore)).orElse(true)) {
            alerts.put(id, with(alert, true, Optional.of(now), 0));
            return HitResult.NOTIFY;
        }
        alerts.put(id, with(alert, true, alert.lastNotified(), alert.pending() + 1));
        return HitResult.SUPPRESSED;
    }

    @Override
    protected synchronized List<OrderAlert> getDigestDue(LocalDateTime notifiedBefore) {
        return alerts.values().stream()
                .filter(OrderAlert::enabled)
                .filter(alert -> alert.pending() > 0)
                .filter(alert -> alert.lastNotified()
                        .map(last -> !last.isAfter(notifiedBefore))
                        .orElse(false))
                .toList();
    }

    @Override
    protected synchronized boolean claimDigest(OrderAlert claimed, LocalDateTime now) {
        var alert = alerts.get(claimed.id());
        if (alert == null || alert.pending() != claimed.pending()) return false;
        alerts.put(alert.id(), with(alert, alert.enabled(), Optional.of(now), 0));
        return true;
    }

    private Optional<OrderAlert> find(int company, String name) {
        return alerts.values().stream()
                .filter(alert -> alert.company() == company && alert.name().equals(name))
                .findFirst();
    }

    private static OrderAlert with(
            OrderAlert alert, boolean enabled, Optional<LocalDateTime> lastNotified, int pending) {
        return new OrderAlert(
                alert.id(),
                alert.company(),
                alert.name(),
                alert.filter(),
                alert.creator(),
                enabled,
                alert.created(),
                lastNotified,
                pending);
    }
}
