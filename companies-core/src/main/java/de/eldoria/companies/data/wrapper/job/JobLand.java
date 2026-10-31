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
     * An area of the owner, of an area provider that lets jobs on its land, such as a PlotSquared plot or a claim: the
     * company's members are made builders there while they work. The land's reference is {@code provider:ref}.
     */
    AREA,
    /**
     * A WorldGuard region the owner may change: a job region gives the company's members access while they work.
     */
    REGION,
    /**
     * Land of nobody, in a world where the server allows jobs there: a job region only marks who works there.
     */
    UNOWNED,
    /**
     * No land at all: a contract, whose area only marks a place to look at and gives nobody access.
     */
    NONE
}
