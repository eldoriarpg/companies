/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.areas;

import de.eldoria.companies.data.repository.AAreaData;
import de.eldoria.companies.data.wrapper.area.CompanyArea;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runs the rents of company areas on the primary server, every {@value #CHECK_SECONDS} seconds:
 * <ul>
 *     <li>A rent due that renews is paid from the treasury for another period of its offer; when the treasury can not
 *     pay, the rent ends.</li>
 *     <li>A rent due that was cancelled ends.</li>
 *     <li>The company is told {@code rentWarnHours} before a renewing rent is due.</li>
 * </ul>
 * A rent more than a period overdue, as after the primary server was down, is paid once from now on instead of for
 * every period missed. A payment that fails for another reason than the balance is tried again on the next check. An
 * ended rent stays stored until the sync on the area's server gives the area back. Areas of a company that is gone
 * are left to that sync as well. A rent whose offer can not be read is skipped and tried again on the next check.
 */
public final class AreaRents {
    /**
     * How often the rents are checked, in seconds.
     */
    public static final int CHECK_SECONDS = 60;

    private final AAreaData areas;
    private final IntFunction<Optional<CompanyProfile>> companies;
    private final Payer payer;
    private final Notices notices;
    private final IntSupplier warnHours;
    private final Supplier<LocalDateTime> now;
    private final Logger logger;

    /**
     * @param areas     the areas of companies
     * @param companies a company by its id, empty when it is gone
     * @param payer     pays a rent from the treasury
     * @param notices   tells a company about its rents
     * @param warnHours how many hours before a rent is due the company is told
     * @param now       the time now, in UTC
     * @param logger    reports failures
     */
    public AreaRents(
            AAreaData areas,
            IntFunction<Optional<CompanyProfile>> companies,
            Payer payer,
            Notices notices,
            IntSupplier warnHours,
            Supplier<LocalDateTime> now,
            Logger logger) {
        this.areas = areas;
        this.companies = companies;
        this.payer = payer;
        this.notices = notices;
        this.warnHours = warnHours;
        this.now = now;
        this.logger = logger;
    }

    /**
     * How paying a rent ended.
     */
    public enum Payment {
        /**
         * Paid, and the next period stored.
         */
        PAID,
        /**
         * The treasury could not pay.
         */
        UNPAID,
        /**
         * Storing the period failed; nothing was charged.
         */
        FAILED
    }

    /**
     * Pays a rent from a company's treasury.
     */
    @FunctionalInterface
    public interface Payer {
        /**
         * @param company the company paying
         * @param area    the rented area, whose price is the rent
         * @param renewal stores the next period, completing with false when it was not stored
         * @return how it ended; nothing is charged unless it is {@link Payment#PAID}
         */
        Payment pay(CompanyProfile company, CompanyArea area, Supplier<Boolean> renewal);
    }

    /**
     * Tells a company about the rents of its areas.
     */
    public interface Notices {
        /**
         * @param company the company
         * @param area    the area
         * @param left    how long until the rent is due
         */
        void due(CompanyProfile company, CompanyArea area, Duration left);

        /**
         * @param company the company
         * @param area    the area
         * @param period  the time paid for
         */
        void renewed(CompanyProfile company, CompanyArea area, Duration period);

        /**
         * @param company the company
         * @param area    the area, whose rent the treasury could not pay
         */
        void unpaid(CompanyProfile company, CompanyArea area);

        /**
         * @param company the company
         * @param area    the area, whose cancelled rent ran out
         */
        void ended(CompanyProfile company, CompanyArea area);
    }

    /**
     * Checks the rents every {@value #CHECK_SECONDS} seconds.
     *
     * @param executor runs the checks
     */
    public void schedule(ScheduledExecutorService executor) {
        executor.scheduleWithFixedDelay(this::check, CHECK_SECONDS, CHECK_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Checks the rents now. Blocks; call it off the main thread.
     */
    public synchronized void check() {
        try {
            var time = now.get();
            for (var area : areas.rents()) handle(area, time);
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "Could not check the rents of company areas", e);
        }
    }

    private void handle(CompanyArea area, LocalDateTime time) {
        var company = companies.apply(area.company());
        if (company.isEmpty()) return;
        var until = area.rentUntil();
        if (until == null) {
            if (areas.endRent(area.id())) notices.ended(company.get(), area);
            return;
        }
        if (!until.isAfter(time)) {
            due(company.get(), area, until, time);
            return;
        }
        var warn = warnHours.getAsInt();
        if (!area.renew() || area.rentWarned() || warn <= 0) return;
        if (until.minusHours(warn).isAfter(time)) return;
        areas.rentWarned(area.id());
        notices.due(company.get(), area, Duration.between(time, until));
    }

    private void due(CompanyProfile company, CompanyArea area, LocalDateTime until, LocalDateTime time) {
        if (!area.renew()) {
            if (areas.endRent(area.id())) notices.ended(company, area);
            return;
        }
        var offer = areas.offer(area.offer());
        if (offer.isEmpty() || offer.get().periodDays() <= 0) {
            logger.warning("Could not renew the rent of company area " + area.id() + ", its offer is not readable");
            return;
        }
        var period = Duration.ofDays(offer.get().periodDays());
        var next = (until.plus(period).isAfter(time) ? until : time).plus(period);
        switch (payer.pay(company, area, () -> areas.renewed(area.id(), until, next))) {
            case PAID -> notices.renewed(company, area, period);
            case UNPAID -> {
                if (areas.endRent(area.id())) notices.unpaid(company, area);
            }
            case FAILED -> logger.warning("Could not store the next period of the rent of company area " + area.id());
        }
    }
}
