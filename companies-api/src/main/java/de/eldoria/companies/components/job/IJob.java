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
 * A job: work a company takes like an order, paid its price when the work checks out. The owner paid price and fee
 * when posting it, unless a plugin posted it uncharged. Area kinds work in a block box of the owner's land; a contract
 * is work described in words.
 */
public interface IJob {
    /**
     * The id of the job.
     *
     * @return the id of the job
     */
    int id();

    /**
     * The player who posted and pays for it.
     *
     * @return the player who posted and pays for it
     */
    UUID owner();

    /**
     * What work it asks for.
     *
     * @return what work it asks for
     */
    JobKind kind();

    /**
     * The title shown to companies.
     *
     * @return the title shown to companies
     */
    String title();

    /**
     * What the owner wrote about it.
     *
     * @return what the owner wrote about it, empty for nothing
     */
    Optional<String> description();

    /**
     * What the company is paid in all.
     *
     * @return what the company is paid in all
     */
    double price();

    /**
     * The fee the owner paid on top.
     *
     * @return the fee the owner paid on top
     */
    double fee();

    /**
     * Where it is in its life.
     *
     * @return where it is in its life
     */
    JobState state();

    /**
     * The company working on it.
     *
     * @return the id of the company working on it, empty while nobody does
     */
    OptionalInt company();

    /**
     * The server whose world holds the area.
     *
     * @return the server whose world holds the area
     */
    UUID node();

    /**
     * The block box of the work. A contract has a place to look at, or an empty box with a blank world.
     *
     * @return the block box of the work
     */
    IJobArea area();

    /**
     * How long a company has to finish once it took the job, without extensions.
     *
     * @return how long a company has to finish once it took the job, without extensions
     */
    int dueHours();

    /**
     * When it was posted.
     *
     * @return when it was posted
     */
    LocalDateTime created();

    /**
     * When it expires while nobody takes it.
     *
     * @return when it expires while nobody takes it
     */
    LocalDateTime expires();

    /**
     * When the company took it.
     *
     * @return when the company took it, empty while open
     */
    Optional<LocalDateTime> taken();

    /**
     * When the company's time runs out.
     *
     * @return when the company's time runs out, empty while open
     */
    Optional<LocalDateTime> due();

    /**
     * When it reached a final state.
     *
     * @return when it reached a final state, empty while it runs
     */
    Optional<LocalDateTime> closed();

    /**
     * Whether the owner paid price and fee; a job posted uncharged by a plugin refunds nothing.
     *
     * @return whether the owner paid price and fee; a job posted uncharged by a plugin refunds nothing
     */
    boolean charged();
}
