/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.job;

import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;

/**
 * The extra time a job got after a company took it, and the company's open request for more.
 *
 * @param extendedHours the hours the owner added, in all, for the company holding the job
 * @param requests      how often the company holding the job asked for more time
 * @param askedHours    the hours the company asks for now, null without an open request
 * @param reason        why the company asks, null without an open request
 * @param askedAt       when it asked, null without an open request
 */
public record JobExtension(
        int extendedHours,
        int requests,
        @Nullable Integer askedHours,
        @Nullable String reason,
        @Nullable LocalDateTime askedAt) {
    /**
     * A job that never got more time.
     */
    public static final JobExtension NONE = new JobExtension(0, 0, null, null, null);

    /**
     * The longest reason of a request, as long as that of a rejection or a dispute.
     */
    public static final int MAX_REASON = JobReview.MAX_REASON;

    /**
     * @return whether the company asks for more time right now
     */
    public boolean asked() {
        return askedHours != null;
    }
}
