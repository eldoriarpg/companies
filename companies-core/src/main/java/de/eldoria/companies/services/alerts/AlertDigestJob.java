/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services.alerts;

import de.eldoria.companies.configuration.elements.CompanySettings;
import de.eldoria.companies.data.repository.AAlertData;
import de.eldoria.companies.data.wrapper.alert.OrderAlert;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Sends the digests of rate limited alerts: once the window of an alert has passed, the company is told how many more
 * orders matched it meanwhile. Only the primary node runs it, every five minutes.
 * <p>
 * Claiming the held back matches is a conditional update, so a digest is sent once even if two servers ran the job.
 */
public final class AlertDigestJob implements Runnable {
    private final AAlertData alerts;
    private final AlertCompanies companies;
    private final Supplier<CompanySettings> settings;
    private final AlertNotices notices;
    private final Clock clock;
    private final Logger logger;

    /**
     * @param alerts    alert repository
     * @param companies reads the companies of the alerts
     * @param settings  the current company settings
     * @param notices   sends the digests
     * @param clock     clock giving the current time
     * @param logger    logs failed runs
     */
    public AlertDigestJob(
            AAlertData alerts,
            AlertCompanies companies,
            Supplier<CompanySettings> settings,
            AlertNotices notices,
            Clock clock,
            Logger logger) {
        this.alerts = alerts;
        this.companies = companies;
        this.settings = settings;
        this.notices = notices;
        this.clock = clock;
        this.logger = logger;
    }

    /**
     * Starts sending digests every five minutes.
     *
     * @param executor executor running the job
     */
    public void start(ScheduledExecutorService executor) {
        executor.scheduleAtFixedRate(this, 1L, 5L, TimeUnit.MINUTES);
    }

    @Override
    public void run() {
        var current = settings.get();
        if (!current.alerts().enabled()) return;
        try {
            var now = LocalDateTime.now(clock);
            var window = Duration.ofMinutes(current.alerts().minMinutesBetween());
            for (var alert : alerts.retrieveDigestDue(now, window).join()) {
                digest(alert, now);
            }
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "Could not send the order alert digests", e);
        }
    }

    private void digest(OrderAlert alert, LocalDateTime now) {
        var count = alerts.submitDigestClaim(alert, now).join();
        if (count <= 0) return;
        companies.profile(alert.company()).join().ifPresent(company -> notices.digest(company, alert, count));
    }
}
