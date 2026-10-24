/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.order;

import java.util.UUID;

/**
 * Who works on a claimed order: a company or a single player. A claim has exactly one claimant.
 */
public sealed interface Claimant {
    /**
     * A company claimed the order.
     *
     * @param companyId id of the company
     */
    record Company(int companyId) implements Claimant {}

    /**
     * A player claimed the order to work on it alone.
     *
     * @param player id of the player
     */
    record Solo(UUID player) implements Claimant {}
}
