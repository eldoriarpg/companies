/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.api.write;

/**
 * Why a job was not posted.
 */
public enum JobProblem {
    /**
     * Jobs are switched off on this server.
     */
    DISABLED,
    /**
     * The world of the area is not on this server.
     */
    NO_WORLD,
    /**
     * The area reaches above or below the world.
     */
    HEIGHT,
    /**
     * The area lies on land the owner does not own, such as another player's plot or region.
     */
    FOREIGN_LAND,
    /**
     * The area lies on land nobody owns, and the server does not allow jobs there in this world.
     */
    UNOWNED_LAND,
    /**
     * The title is blank or too long.
     */
    TITLE,
    /**
     * The description is too long.
     */
    DESCRIPTION,
    /**
     * The price is not above 0 or too high.
     */
    PRICE,
    /**
     * The time to finish is below an hour or above the most the server allows.
     */
    DUE,
    /**
     * The area holds more blocks than the server allows for the kind.
     */
    VOLUME,
    /**
     * The area touches more chunks than the server allows.
     */
    CHUNKS,
    /**
     * The area overlaps a job that waits or is worked on.
     */
    OVERLAP,
    /**
     * The owner has as many jobs waiting as the server allows.
     */
    TOO_MANY,
    /**
     * The owner can not pay price and fee.
     */
    FUNDS,
    /**
     * The terraform height lies outside the area.
     */
    TARGET,
    /**
     * The terraform surface is a block a company can not lay for good, such as an unbreakable block, concrete powder or
     * copper that oxidises.
     */
    SURFACE,
    /**
     * The harvest item price is below the lowest price or above the job's price.
     */
    UNIT_PRICE,
    /**
     * The job could not be stored; nothing was charged.
     */
    FAILED,
    /**
     * A build job was stored and taken by a company before its blueprint could be saved. Its money stays held until an
     * admin settles the job; nothing was refunded.
     */
    STRANDED
}
