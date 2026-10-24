/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository;

/**
 * The repositories of the economy features: company treasuries, repeating orders, order alerts and saved items.
 *
 * @param treasury   company treasuries and their log
 * @param series     repeating orders
 * @param alerts     order alerts of the companies
 * @param savedItems the items players saved to order again
 */
public record EconomyData(ATreasuryData treasury, ASeriesData series, AAlertData alerts, ASavedItemData savedItems) {}
