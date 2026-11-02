/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard.views;

import de.eldoria.companies.components.company.ICompanyProfile;
import de.eldoria.companies.configuration.elements.CompanySettings;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.orders.OrderMaterials;
import de.eldoria.companies.services.events.ActiveEvents;
import de.eldoria.companies.services.events.EventPreviews;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/**
 * What the server events running now do to orders, for the company of a viewing player or for a player working alone,
 * as the in-game order search and order view show it: the marks of found orders and the payout of one order.
 */
public interface OrderEventPreviews {
    /**
     * Knows no server events.
     */
    OrderEventPreviews NONE = new OrderEventPreviews() {
        @Override
        public Map<Integer, EventPreviews.EventMark> marks(List<FullOrder> orders, Optional<ICompanyProfile> company) {
            return Map.of();
        }

        @Override
        public Optional<EventPreviews.Preview> preview(FullOrder order, Optional<ICompanyProfile> company) {
            return Optional.empty();
        }
    };

    /**
     * @param orders  the orders
     * @param company the viewer's company, empty for a player working alone
     * @return the mark of each order an event boosts, by order id
     */
    Map<Integer, EventPreviews.EventMark> marks(List<FullOrder> orders, Optional<ICompanyProfile> company);

    /**
     * @param order   the order
     * @param company the viewer's company, empty for a player working alone
     * @return the payout of the whole order with the events, empty when no event changes it
     */
    Optional<EventPreviews.Preview> preview(FullOrder order, Optional<ICompanyProfile> company);

    /**
     * The previews of this node's events.
     *
     * @param active    the events of this node
     * @param companies the current company settings, for the tax of a level
     * @param soloTax   the tax of players working alone, in percent
     * @param cap       the cap of all multipliers together
     * @param clock     the current time in UTC
     * @return the previews
     */
    static OrderEventPreviews of(
            ActiveEvents active,
            Supplier<CompanySettings> companies,
            DoubleSupplier soloTax,
            DoubleSupplier cap,
            Clock clock) {
        return new OrderEventPreviews() {
            @Override
            public Map<Integer, EventPreviews.EventMark> marks(
                    List<FullOrder> orders, Optional<ICompanyProfile> company) {
                return EventPreviews.marks(orders, level(company), active, cap.getAsDouble(), LocalDateTime.now(clock));
            }

            @Override
            public Optional<EventPreviews.Preview> preview(FullOrder order, Optional<ICompanyProfile> company) {
                var settings = companies.get();
                var tax =
                        company.map(found -> settings.taxPercent(found.level())).orElseGet(soloTax::getAsDouble);
                var matching =
                        active.matching(LocalDateTime.now(clock), OrderMaterials.accepted(order), level(company));
                return EventPreviews.preview(order, matching, tax, cap.getAsDouble());
            }

            private OptionalInt level(Optional<ICompanyProfile> company) {
                return company.map(found -> OptionalInt.of(found.level())).orElse(OptionalInt.empty());
            }
        };
    }
}
