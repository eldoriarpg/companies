/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository;

/**
 * The repositories of the economy features: company treasuries, repeating orders and order alerts.
 *
 * @param treasury company treasuries and their log
 * @param series   repeating orders
 * @param alerts   order alerts of the companies
 */
public record EconomyData(ATreasuryData treasury, ASeriesData series, AAlertData alerts) {}
