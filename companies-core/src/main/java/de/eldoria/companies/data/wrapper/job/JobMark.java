/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.job;

/**
 * A position of a taken job's area the work counting remembers for the company working on it, so it survives a
 * restart.
 *
 * @param job     id of the job
 * @param company id of the company the position was remembered for
 * @param kind    what is remembered about the position
 * @param pos     the position packed into one number, see {@code JobAccess.key}
 */
public record JobMark(int job, int company, Kind kind, long pos) {
    /**
     * What is remembered about a position.
     */
    public enum Kind {
        /**
         * A block was placed there while the job ran, so breaking it again is no work.
         */
        PLACED(0),
        /**
         * The position was filled as the job asks and counted, so filling it again is no work.
         */
        FILLED(1);

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
            throw new IllegalArgumentException("Unknown job mark kind " + code);
        }
    }
}
