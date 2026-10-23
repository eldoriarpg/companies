/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.stats;

import java.util.UUID;

/**
 * What a worker contributed to a company in a time range.
 *
 * @param worker worker
 * @param items  items delivered to completed orders
 * @param earned money paid out, after tax
 * @param orders completed orders the worker delivered to
 */
public record MemberStats(UUID worker, int items, double earned, int orders) {}
