/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.job;

/**
 * The block box of a job, corners included. A contract has no real area; its box is a place to look at or empty.
 */
public interface IJobArea {
    /**
     * The name of the world.
     *
     * @return the name of the world
     */
    String world();

    /**
     * The lowest x.
     *
     * @return the lowest x
     */
    int minX();

    /**
     * The lowest y.
     *
     * @return the lowest y
     */
    int minY();

    /**
     * The lowest z.
     *
     * @return the lowest z
     */
    int minZ();

    /**
     * The highest x.
     *
     * @return the highest x
     */
    int maxX();

    /**
     * The highest y.
     *
     * @return the highest y
     */
    int maxY();

    /**
     * The highest z.
     *
     * @return the highest z
     */
    int maxZ();
}
