/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.api.snapshot;

import org.jspecify.annotations.Nullable;

/**
 * A company's treasury.
 *
 * @param balance        the balance
 * @param share          the share of each payout the company keeps, in percent, after the server's cap
 * @param maxShare       the highest share the server allows, in percent
 * @param minTransaction the smallest amount paid in or out
 */
public record TreasuryView(
        double balance,
        @Nullable Integer share,
        @Nullable Integer maxShare,
        @Nullable Double minTransaction) {}
