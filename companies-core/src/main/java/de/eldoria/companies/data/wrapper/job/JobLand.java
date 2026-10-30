/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.job;

/**
 * Whose land the area of a job lies on, which decides how the company gets access to it.
 */
public enum JobLand {
    /**
     * A PlotSquared plot of the owner: the company's members are trusted on it while they work.
     */
    PLOT,
    /**
     * A WorldGuard region the owner may change: a job region gives the company's members access while they work.
     */
    REGION,
    /**
     * Land of nobody, in a world where the server allows jobs there: a job region only marks who works there.
     */
    UNOWNED
}
