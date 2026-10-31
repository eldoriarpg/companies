/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.api.write;

/**
 * Why an area was not offered.
 */
public enum OfferProblem {
    /**
     * No area of a loaded plugin lies at the location, or the reference names none.
     */
    NO_AREA,
    /**
     * The area's plugin can not move it to a company.
     */
    NOT_MOVABLE,
    /**
     * Nobody owns the area, and its plugin can not leave it to nobody.
     */
    NO_KEEPER,
    /**
     * A company has the area.
     */
    IN_USE,
    /**
     * The area is offered already.
     */
    ALREADY_OFFERED
}
