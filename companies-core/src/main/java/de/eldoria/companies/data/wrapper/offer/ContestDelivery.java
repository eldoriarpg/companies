/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.offer;

/**
 * What came of a delivery into a contest.
 *
 * @param outcome how it went
 * @param items   items taken into the contest, 0 unless it was delivered
 * @param gross   what the items are paid before tax
 * @param state   the contest's state after the delivery
 */
public record ContestDelivery(Outcome outcome, int items, double gross, OfferState state) {
    /**
     * @param outcome why nothing was delivered
     * @param state   the contest's state
     * @return a delivery of nothing
     */
    public static ContestDelivery refused(Outcome outcome, OfferState state) {
        return new ContestDelivery(outcome, 0, 0, state);
    }

    /**
     * How a delivery into a contest went.
     */
    public enum Outcome {
        /**
         * Items were taken and are paid.
         */
        DELIVERED,
        /**
         * There is no such contest.
         */
        UNKNOWN,
        /**
         * The contest does not run, it waits, is paused or closed.
         */
        NOT_ACTIVE,
        /**
         * The company has not joined the contest.
         */
        NOT_JOINED,
        /**
         * The contest misses no items, or its money cannot pay one more.
         */
        FULL,
        /**
         * Too many deliveries at once; nothing was taken.
         */
        BUSY
    }
}
