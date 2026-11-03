/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.perk;

import de.eldoria.companies.components.company.ICompanyPerk;

import java.time.LocalDateTime;

/**
 * A perk a company owns, with whether it may use it now.
 *
 * @param company id of the company
 * @param perk    id of the perk, lower case
 * @param bought  when the company got it, in UTC
 * @param usable  whether the server offers it and the company has reached its minimum level
 */
public record CompanyPerk(int company, String perk, LocalDateTime bought, boolean usable) implements ICompanyPerk {}
