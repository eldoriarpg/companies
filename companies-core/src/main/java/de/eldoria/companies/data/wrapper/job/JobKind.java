/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.job;

/**
 * The kind of work a job asks for. Only {@link #DIG} can be created so far; the other kinds follow.
 */
public enum JobKind {
    /**
     * Clear the area: every block in it must be air, water or lava.
     */
    DIG,
    /**
     * Shape the area: solid up to a target height, empty above.
     */
    TERRAFORM,
    /**
     * Keep a field harvested and replanted, paid per item.
     */
    HARVEST,
    /**
     * Build a schematic in the area.
     */
    BUILD,
    /**
     * Any work described in words, approved by the owner.
     */
    CONTRACT;

    /**
     * @return whether the job has an area in a world that companies get access to
     */
    public boolean area() {
        return this != CONTRACT;
    }
}
