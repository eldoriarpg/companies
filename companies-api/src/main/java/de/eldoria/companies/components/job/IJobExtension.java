/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.job;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The extra time of a taken job: what its owner gave and what its company asks for.
 */
public interface IJobExtension {
    /**
     * The hours the owner added to the job's time in all.
     *
     * @return hours, 0 when none
     */
    int extendedHours();

    /**
     * How often the company asked for more time.
     *
     * @return count
     */
    int requests();

    /**
     * The hours the company asks for now.
     *
     * @return hours, empty when it asks for nothing
     */
    OptionalInt askedHours();

    /**
     * Why the company asks for more time.
     *
     * @return reason, empty when it asks for nothing
     */
    Optional<String> reason();

    /**
     * When the company asked.
     *
     * @return time in UTC, empty when it asks for nothing
     */
    Optional<LocalDateTime> askedAt();
}
