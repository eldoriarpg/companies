/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.offer;

/**
 * Where a prize of a settled contest is. A prize is paid at most once: it is claimed for paying before the money
 * moves, and a claimed prize whose payment had no known outcome is never tried again.
 */
public enum PrizeState {
    /**
     * Reserved for its company and waiting to be paid.
     */
    PENDING,
    /**
     * Claimed for paying; stays so when the payment had no known outcome, such as after a crash.
     */
    PAYING,
    /**
     * Paid to its company.
     */
    PAID,
    /**
     * Its payment was refused; the money stays with the contest.
     */
    FAILED,
    /**
     * No company reached the place, or the contest was cancelled; the money stays with the contest.
     */
    NONE
}
