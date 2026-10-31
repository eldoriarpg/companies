/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.area;

/**
 * What an {@link AreaProvider} can do with the areas of its plugin. Commands and the area market only offer what the
 * provider of an area supports.
 */
public enum AreaCapability {
    /**
     * An owner can link an area they own to their company; the area stays theirs.
     */
    LINK_OWNED,
    /**
     * The ownership of an area can be moved to a player, and the area given back to the server, so it can be sold.
     */
    TRANSFER,
    /**
     * Players can be given and taken the right to build in an area, so the builders of a company can be kept in step
     * with its members.
     */
    TRUST_LIST,
    /**
     * Jobs may lie on its areas: a player posts a job on an area they own, and while a company works on it, its members
     * are made builders there and taken off again when the job ends. Only counts together with {@link #TRUST_LIST}.
     */
    JOB_LAND
}
