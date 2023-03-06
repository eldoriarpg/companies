/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.order;

public interface IMaterialPrice {
    String material();

    double avgPrice();

    double minPrice();

    double maxPrice();
}
