/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.perk;

import java.time.LocalDateTime;

/**
 * A perk a company owns, stored in {@code company_perks}.
 *
 * @param company id of the company
 * @param perk    id of the perk, lower case
 * @param bought  when the company got it, in UTC
 */
public record OwnedPerk(int company, String perk, LocalDateTime bought) {}
