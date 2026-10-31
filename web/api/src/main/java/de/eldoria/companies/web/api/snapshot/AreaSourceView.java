/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.api.snapshot;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

/**
 * How a company got an area.
 * <p>
 * Open: a value added by a newer peer reads as {@link #UNKNOWN}. Written as the constant's name.
 */
public enum AreaSourceView {
    /**
     * A member who owns the plot or region linked it.
     */
    LINKED,
    /**
     * The company bought it from the server.
     */
    BOUGHT,
    /**
     * The company rents it from the server.
     */
    RENTED,
    /**
     * A value this version does not know, sent by a newer peer.
     */
    @JsonEnumDefaultValue
    UNKNOWN
}
