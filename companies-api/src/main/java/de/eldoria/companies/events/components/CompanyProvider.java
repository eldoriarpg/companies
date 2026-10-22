/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.events.components;

import de.eldoria.companies.components.company.ISimpleCompany;

/**
 * An event that involves a company.
 *
 * @param <C> type of company
 */
@FunctionalInterface
public interface CompanyProvider<C extends ISimpleCompany> {
    /**
     * The company involved in the event.
     *
     * @return company
     */
    C company();
}
