/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.api.write;

import de.eldoria.companies.components.area.IAreaOffer;

import java.util.Objects;

/**
 * The outcome of {@link de.eldoria.companies.api.AreaApi#offer(AreaOfferRequest)}: the stored offer or why the area
 * was not offered.
 */
public sealed interface OfferResult {
    /**
     * Whether the area was offered.
     *
     * @return true for {@link Offered}
     */
    boolean offered();

    /**
     * The area is offered.
     *
     * @param offer the stored offer
     */
    record Offered(IAreaOffer offer) implements OfferResult {
        /**
         * Creates the result.
         */
        public Offered {
            Objects.requireNonNull(offer, "offer");
        }

        @Override
        public boolean offered() {
            return true;
        }
    }

    /**
     * The area was not offered.
     *
     * @param problem why
     */
    record Rejected(OfferProblem problem) implements OfferResult {
        /**
         * Creates the result.
         */
        public Rejected {
            Objects.requireNonNull(problem, "problem");
        }

        @Override
        public boolean offered() {
            return false;
        }
    }
}
