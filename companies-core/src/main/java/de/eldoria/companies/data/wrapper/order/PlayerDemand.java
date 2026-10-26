/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.order;

/**
 * What players ask for of one material in their orders nobody claimed yet.
 *
 * @param material    the material, as order lines store it
 * @param orders      open orders with the material
 * @param items       items of the material they ask for
 * @param lowestPrice the lowest price per item among them
 */
public record PlayerDemand(String material, int orders, long items, double lowestPrice) {}
