/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.api.write;

/**
 * What came of {@link de.eldoria.companies.api.OrderApi#cancel(int, java.util.UUID)}.
 */
public enum CancelOrderResult {
    /**
     * The order is gone; its price went back to the owner where the owner paid it.
     */
    CANCELLED,
    /**
     * No order has the id.
     */
    UNKNOWN,
    /**
     * The order belongs to someone else.
     */
    NOT_OWNER,
    /**
     * A company or a solo worker has taken the order; it stays.
     */
    CLAIMED
}
