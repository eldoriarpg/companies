/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.offer;

import java.util.Objects;

/**
 * The place of a company in a settled contest.
 *
 * @param place       its place, from 1
 * @param company     id of the company
 * @param companyName name of the company when the contest was settled
 * @param delivered   items it delivered
 * @param prize       the prize money that reached the company: 0 when its place has no prize or paying it failed
 */
public record ContestPlace(int place, int company, String companyName, int delivered, double prize) {
    /**
     * Checks that the name is set.
     *
     * @param companyName name of the company
     */
    public ContestPlace {
        Objects.requireNonNull(companyName, "companyName");
    }
}
