/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.order;

/**
 * What an order line accepts.
 */
public enum LineKind {
    /**
     * Every item of the template's material.
     */
    MATERIAL,
    /**
     * Only items matching the template's data, following the item matching settings of the server.
     */
    EXACT,
    /**
     * Every item meeting the line's {@link ItemCriteria}.
     */
    CRITERIA;

    /**
     * The kind of a line.
     *
     * @param exact    whether the line requests the data of its template
     * @param criteria whether the line has criteria
     * @return the kind
     */
    public static LineKind of(boolean exact, boolean criteria) {
        if (criteria) return CRITERIA;
        return exact ? EXACT : MATERIAL;
    }
}
