/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.api.write;

/**
 * What came of a change to a job through the API.
 */
public enum JobChange {
    /**
     * The change was made.
     */
    DONE,
    /**
     * No job has this id, or it is not the player's.
     */
    UNKNOWN,
    /**
     * The player is in no company.
     */
    NO_COMPANY,
    /**
     * The member lacks the {@code manage_orders} permission of their company.
     */
    NO_PERMISSION,
    /**
     * The job is not in a state that allows the change, such as one already taken or closed.
     */
    CLOSED,
    /**
     * The job is held by another company, or by none.
     */
    NOT_YOURS,
    /**
     * The company has no work slot free.
     */
    LIMIT,
    /**
     * The job belongs to a member of the company.
     */
    OWN,
    /**
     * The job is disputed; an admin settles it first.
     */
    DISPUTED,
    /**
     * The change does not fit the job's kind, such as finishing a contract by its area.
     */
    WRONG_KIND,
    /**
     * The area of the job is not done yet.
     */
    NOT_DONE,
    /**
     * A reason is missing or too long.
     */
    REASON,
    /**
     * The area lies on another server, so it can not be checked here.
     */
    OTHER_SERVER,
    /**
     * The world of the job's area is not loaded, or its check took too long.
     */
    NOT_LOADED,
    /**
     * A listener cancelled the change.
     */
    REFUSED,
    /**
     * Something went wrong; the server log names it.
     */
    FAILED
}
