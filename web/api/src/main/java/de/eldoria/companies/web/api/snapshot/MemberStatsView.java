/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.api.snapshot;

/**
 * What a member achieved for their company.
 *
 * @param orders orders worked on
 * @param items items delivered
 * @param earned money earned
 */
public record MemberStatsView(int orders, int items, double earned) {}
