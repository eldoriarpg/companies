/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.event;

/**
 * What a server event changes while it runs, and what its value means.
 */
public enum EventKind {
    /**
     * Lowers the tax on payouts by its value in percentage points: 5 turns a tax of 10 % into 5 %.
     */
    TAX_REDUCTION,
    /**
     * The server pays extra on top of payouts: its value is the factor on the gross payout, 1.5 pays half of it
     * extra. The only kind that is funded and may run out of money.
     */
    REWARD_MULTIPLIER,
    /**
     * Lowers the fee of new orders and raises by its value in percent: 50 halves the fee. It has no level scope.
     */
    FEE_REDUCTION;

    /**
     * Whether events of this kind pay money and therefore have a funding.
     *
     * @return whether events of this kind pay money and therefore have a funding
     */
    public boolean funded() {
        return this == REWARD_MULTIPLIER;
    }

    /**
     * Whether events of this kind may be limited to companies of given levels.
     *
     * @return whether events of this kind may be limited to companies of given levels
     */
    public boolean levelScoped() {
        return this != FEE_REDUCTION;
    }
}
