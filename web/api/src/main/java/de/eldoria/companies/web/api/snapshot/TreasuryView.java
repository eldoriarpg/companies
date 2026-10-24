/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.api.snapshot;

/**
 * A company's treasury.
 *
 * @param balance the balance
 */
public record TreasuryView(double balance) {}
