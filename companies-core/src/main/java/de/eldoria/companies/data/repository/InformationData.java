/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository;

/**
 * The repositories of the information features: inbox, payout ledger with ranking and statistics, and order history.
 *
 * @param inbox   notification inbox
 * @param stats   payout ledger, ranking and member statistics
 * @param history order history
 */
public record InformationData(AInboxData inbox, AStatsData stats, AHistoryData history) {}
