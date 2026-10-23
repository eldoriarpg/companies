/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.company;

import java.math.BigDecimal;

/**
 * The treasury of a company: money the company holds and the share of its workers' payouts it keeps.
 */
public interface ITreasury {
    /**
     * The id of the company.
     *
     * @return company id
     */
    int company();

    /**
     * The money in the treasury.
     *
     * @return balance with two decimals, never negative
     */
    BigDecimal balance();

    /**
     * The share of each worker's payout after tax that goes to the treasury, as the company set it. The server may
     * apply a lower cap.
     *
     * @return percentage from 0 to 100
     */
    int payoutShare();
}
