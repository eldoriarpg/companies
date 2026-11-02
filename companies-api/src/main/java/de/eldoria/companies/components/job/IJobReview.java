/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.job;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Where a contract stands with its owner: done, rejected, disputed or settled.
 */
public interface IJobReview {
    /**
     * When the company said the contract is done.
     *
     * @return time in UTC, empty when it did not
     */
    Optional<LocalDateTime> doneAt();

    /**
     * The member who said the contract is done.
     *
     * @return member, empty when nobody did
     */
    Optional<UUID> doneBy();

    /**
     * Why the owner rejected the work the last time.
     *
     * @return reason, empty when the owner never rejected it
     */
    Optional<String> rejection();

    /**
     * Who opened the dispute about the job.
     *
     * @return player, empty without a dispute
     */
    Optional<UUID> disputedBy();

    /**
     * Why the dispute was opened.
     *
     * @return reason, empty without a dispute
     */
    Optional<String> dispute();

    /**
     * When the dispute was opened.
     *
     * @return time in UTC, empty without a dispute
     */
    Optional<LocalDateTime> disputedAt();

    /**
     * The share of the price an admin gave the company when settling the dispute.
     *
     * @return percent, empty when it was not settled
     */
    OptionalInt settledPercent();
}
