/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.treasury;

import de.eldoria.companies.components.company.ITreasury;
import de.eldoria.companies.util.Money;

import java.math.BigDecimal;

/**
 * The treasury of a company. A company that never used its treasury has a balance and share of 0.
 *
 * @param company     id of the company
 * @param cents       balance in cents
 * @param payoutShare share of each payout after tax the company chose, in percent; the configured cap still applies
 */
public record Treasury(int company, long cents, int payoutShare) implements ITreasury {
    /**
     * The treasury of a company without a stored row.
     *
     * @param company id of the company
     * @return empty treasury
     */
    public static Treasury empty(int company) {
        return new Treasury(company, 0, 0);
    }

    @Override
    public BigDecimal balance() {
        return Money.fromCents(cents);
    }
}
