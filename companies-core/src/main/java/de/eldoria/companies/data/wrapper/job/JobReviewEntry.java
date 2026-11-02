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
 * One step of the review of a job's work, kept so the owner and the company see how it went.
 *
 * @param job    id of the job
 * @param kind   what happened
 * @param by     the player who did it
 * @param reason why, for a rejection; null otherwise
 * @param at     when
 */
public record JobReviewEntry(
        int job, Kind kind, UUID by, @Nullable String reason, LocalDateTime at) {
    /**
     * What happened.
     */
    public enum Kind {
        /**
         * The company said the work is done.
         */
        DONE(0),
        /**
         * The owner sent the work back, with a reason.
         */
        REJECTED(1);

        private final int code;

        Kind(int code) {
            this.code = code;
        }

        /**
         * @return the number the kind is stored as
         */
        public int code() {
            return code;
        }

        /**
         * @param code a stored number
         * @return the kind stored as it
         * @throws IllegalArgumentException for a number no kind is stored as
         */
        public static Kind of(int code) {
            for (var kind : values()) {
                if (kind.code == code) return kind;
            }
            throw new IllegalArgumentException("Unknown job review kind " + code);
        }
    }
}
