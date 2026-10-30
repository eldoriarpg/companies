/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.job;

import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * How the end of a job's work is reviewed: the company saying it is done, the owner rejecting it, and a dispute an
 * admin settles.
 *
 * @param doneAt     when the company said the work is done, null while it did not or after a rejection
 * @param doneBy     the member who said so, null with {@code doneAt}
 * @param rejected   why the owner rejected the work last, null when they never did
 * @param disputeBy  the player who opened the dispute, null without one
 * @param dispute    why the dispute was opened, null without one
 * @param disputedAt when the dispute was opened, null without one
 * @param settled    the percent of the price an admin gave the company, null while not settled
 */
public record JobReview(
        @Nullable LocalDateTime doneAt,
        @Nullable UUID doneBy,
        @Nullable String rejected,
        @Nullable UUID disputeBy,
        @Nullable String dispute,
        @Nullable LocalDateTime disputedAt,
        @Nullable Integer settled) {
    /**
     * A job whose work was never reviewed.
     */
    public static final JobReview NONE = new JobReview(null, null, null, null, null, null, null);

    /**
     * The longest reason of a rejection or a dispute.
     */
    public static final int MAX_REASON = 256;
}
