/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.job;

import org.bukkit.Material;

import java.util.Optional;

/**
 * The shape a terraform job asks for: everything above a height cleared, and its top layer of a surface block.
 */
public interface IJobTarget {
    /**
     * The height of the top layer.
     *
     * @return y
     */
    int y();

    /**
     * The block the top layer is made of.
     *
     * @return block, empty when any solid block does
     */
    Optional<Material> surface();
}
