/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.job;

/**
 * What a harvest job pays: a price for each item, out of its budget, which is its price.
 */
public interface IJobHarvest {
    /**
     * What each harvested item pays.
     *
     * @return price per item
     */
    double unitPrice();

    /**
     * What was paid for harvested items so far.
     *
     * @return money paid
     */
    double paid();
}
