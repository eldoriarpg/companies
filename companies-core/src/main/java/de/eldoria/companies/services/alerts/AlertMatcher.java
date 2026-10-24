/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services.alerts;

import de.eldoria.companies.configuration.elements.CompanySettings;
import de.eldoria.companies.data.repository.AAlertData;
import de.eldoria.companies.data.wrapper.alert.HitResult;
import de.eldoria.companies.data.wrapper.alert.OrderAlert;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.events.order.OrderCreatedEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Matches every new order against the enabled alerts of all companies and notifies the companies it matches.
 * <p>
 * It runs on the server where the order was created, which is the only one that sees the creation. The alerts are read
 * from the database for every order, so alerts saved on another server count at once; the notifications go through the
 * inbox, so members on other servers get them as well.
 * <p>
 * A company is only told about orders its search would show: no order of its own members unless member orders are
 * allowed, none larger than its level allows, none while it works on as many orders as its level allows, and none
 * while its level allows no alerts. The owner of the order is only used for this check and never sent.
 * <p>
 * Each alert notifies at most once per {@code alerts.minMinutesBetween}; matches in between are counted for the
 * {@link AlertDigestJob}. Several alerts of one company that match the same order send one notification naming them
 * all.
 */
public final class AlertMatcher implements Listener {
    private final AAlertData alerts;
    private final AlertCompanies companies;
    private final Supplier<CompanySettings> settings;
    private final AlertNotices notices;
    private final Clock clock;
    private final Executor executor;
    private final Logger logger;

    /**
     * @param alerts    alert repository
     * @param companies reads the companies of the alerts
     * @param settings  the current company settings
     * @param notices   sends the notifications
     * @param clock     clock giving the current time
     * @param executor  runs the matching off the event's thread
     * @param logger    logs failed matches
     */
    public AlertMatcher(
            AAlertData alerts,
            AlertCompanies companies,
            Supplier<CompanySettings> settings,
            AlertNotices notices,
            Clock clock,
            Executor executor,
            Logger logger) {
        this.alerts = alerts;
        this.companies = companies;
        this.settings = settings;
        this.notices = notices;
        this.clock = clock;
        this.executor = executor;
        this.logger = logger;
    }

    /**
     * Matches a created order on the executor.
     *
     * @param event creation event
     */
    @EventHandler
    public void onOrderCreated(OrderCreatedEvent event) {
        if (!(event.order() instanceof FullOrder order)) return;
        executor.execute(() -> match(order));
    }

    /**
     * Matches an order against the enabled alerts and notifies the companies it matches. Blocks until done.
     *
     * @param order the new order
     */
    public void match(FullOrder order) {
        var current = settings.get();
        if (!current.alerts().enabled()) return;
        List<OrderAlert> enabled;
        try {
            enabled = alerts.retrieveEnabled().join();
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "Could not read the order alerts", e);
            return;
        }
        var byCompany = enabled.stream()
                .filter(alert -> alert.filter().matches(order))
                .collect(Collectors.groupingBy(OrderAlert::company, LinkedHashMap::new, Collectors.toList()));
        var now = LocalDateTime.now(clock);
        var window = Duration.ofMinutes(current.alerts().minMinutesBetween());
        byCompany.forEach((company, matched) -> {
            try {
                notifyCompany(company, matched, order, current, now, window);
            } catch (RuntimeException e) {
                logger.log(Level.SEVERE, "Could not notify company " + company + " about order " + order.id(), e);
            }
        });
    }

    private void notifyCompany(
            int companyId,
            List<OrderAlert> matched,
            FullOrder order,
            CompanySettings current,
            LocalDateTime now,
            Duration window) {
        var company = companies.profile(companyId).join();
        if (company.isEmpty() || !sees(company.get(), order, current)) return;
        var notifying = matched.stream()
                .filter(alert -> alerts.submitHit(alert.id(), now, window).join() == HitResult.NOTIFY)
                .map(OrderAlert::name)
                .toList();
        if (notifying.isEmpty()) return;
        notices.orderMatched(
                company.get(),
                notifying,
                order,
                current.taxPercent(company.get().level()));
    }

    /**
     * Whether a company would see an order in its search and may use alerts at its level.
     *
     * @param company the company
     * @param order   the order
     * @param current the company settings
     * @return true when the company is told about the order
     */
    private boolean sees(CompanyProfile company, FullOrder order, CompanySettings current) {
        if (current.maxAlerts(company.level()) <= 0) return false;
        if (!company.mayAcceptOrderOf(order.owner(), current.allowMemberOrders())) return false;
        var level = current.levelSettings(company.level());
        if (level.exceedsOrderSize(order.amount())) return false;
        var accepted = companies.acceptedOrders(company).join();
        return accepted != null && accepted < level.maxOrders();
    }
}
