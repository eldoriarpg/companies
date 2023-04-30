/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.events.components;

import de.eldoria.companies.components.company.ISimpleCompany;

@FunctionalInterface
public interface CompanyProvider<C extends ISimpleCompany> {
    C company();
}
