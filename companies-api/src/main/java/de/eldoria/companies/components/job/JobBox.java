/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.job;

import java.util.Objects;

/**
 * A block box for a job request, corners included and put in order, so either corner may come first.
 *
 * @param world name of the world
 * @param minX  lowest x
 * @param minY  lowest y
 * @param minZ  lowest z
 * @param maxX  highest x
 * @param maxY  highest y
 * @param maxZ  highest z
 */
public record JobBox(String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) implements IJobArea {
    /**
     * Orders the corners.
     */
    public JobBox {
        Objects.requireNonNull(world, "world");
        int lowX = Math.min(minX, maxX), lowY = Math.min(minY, maxY), lowZ = Math.min(minZ, maxZ);
        maxX = Math.max(minX, maxX);
        maxY = Math.max(minY, maxY);
        maxZ = Math.max(minZ, maxZ);
        minX = lowX;
        minY = lowY;
        minZ = lowZ;
    }
}
