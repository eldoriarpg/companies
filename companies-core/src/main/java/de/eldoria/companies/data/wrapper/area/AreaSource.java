/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.area;

/**
 * How a company got an area.
 */
public enum AreaSource {
    /**
     * A member who owns the area linked it; it stays theirs, the company only uses it.
     */
    LINKED,
    /**
     * The company bought it from the server; its owner holds it for the company.
     */
    BOUGHT,
    /**
     * The company rents it from the server for a time, paid from the treasury ahead.
     */
    RENTED
}
