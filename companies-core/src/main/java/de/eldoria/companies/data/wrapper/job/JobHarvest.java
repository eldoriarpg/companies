/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.job;

/**
 * The money of a harvest job: its price is a budget paid out item by item as the company harvests.
 *
 * @param unitPrice what the company gets for each item it harvests into the stock
 * @param paid      what was paid out of the budget so far, before tax
 */
public record JobHarvest(double unitPrice, double paid) {}
