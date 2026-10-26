/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders.system;

import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.configuration.elements.systemorders.GuessedSettings;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.orders.OrderAcceptService;
import de.eldoria.companies.orders.SystemOwner;
import de.eldoria.companies.util.Times;
import de.eldoria.eldoutilities.messages.Replacement;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Caps the guessed orders one company may take per day of the server's calendar, {@code perCompanyDaily} of the
 * guessed settings, so workers cannot farm the server's cheap orders. An order counts on the day the company took or
 * finished it.
 */
public final class GuessedLimit implements OrderAcceptService.Limit {
    private final AOrderData orders;
    private final Supplier<GuessedSettings> settings;
    private final Clock clock;

    /**
     * @param orders   order repository
     * @param settings the current guessed order settings
     * @param clock    clock giving the current time in UTC
     */
    public GuessedLimit(AOrderData orders, Supplier<GuessedSettings> settings, Clock clock) {
        this.orders = orders;
        this.settings = settings;
        this.clock = clock;
    }

    @Override
    public Optional<CommandFailure> refusal(CompanyProfile company, SimpleOrder order) {
        var cap = settings.get().perCompanyDaily();
        if (cap <= 0 || !SystemOwner.is(order.owner())) return Optional.empty();
        if (!orders.systemRule(order.id()).filter(GuessedSettings.RULE::equals).isPresent()) return Optional.empty();
        var dayStart = Times.startOfDay(LocalDate.now(clock.withZone(Times.zone())));
        if (orders.systemOrdersTaken(company.id(), GuessedSettings.RULE, dayStart) < cap) return Optional.empty();
        return Optional.of(CommandFailure.actionBar("error.guessedLimit", Replacement.create("max", cap)));
    }
}
