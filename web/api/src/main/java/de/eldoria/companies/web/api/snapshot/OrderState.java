/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.api.snapshot;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

/**
 * The state of an order.
 * <p>
 * Open: a value added by a newer peer reads as {@link #UNKNOWN}. Written as the constant's name.
 */
public enum OrderState {
    /**
     * Waiting for a company or solo worker.
     */
    UNCLAIMED,
    /**
     * Claimed and being delivered.
     */
    CLAIMED,
    /**
     * Fully delivered, waiting for the owner to receive the items.
     */
    DELIVERED,
    /**
     * Received by the owner.
     */
    RECEIVED,
    /**
     * A value this version does not know, sent by a newer peer.
     */
    @JsonEnumDefaultValue
    UNKNOWN
}
