/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.api.snapshot;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

/**
 * Where a company stands with a perk.
 */
public enum PerkStateView {
    /**
     * It owns the perk and may use it.
     */
    OWNED,
    /**
     * It owns the perk but may not use it: its level is too low, or the server switched the perk off.
     */
    PAUSED,
    /**
     * It may buy the perk.
     */
    AVAILABLE,
    /**
     * Its level is too low to buy the perk.
     */
    LOCKED,
    /**
     * A value this version does not know, sent by a newer peer.
     */
    @JsonEnumDefaultValue
    UNKNOWN
}
