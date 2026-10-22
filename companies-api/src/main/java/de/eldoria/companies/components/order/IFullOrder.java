/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.order;

import org.bukkit.Material;

import java.util.List;
import java.util.Optional;

public interface IFullOrder extends ISimpleOrder {
    List<? extends IOrderContent> contents();

    double price();

    int amount();

    int materialsAmount();

    int delivered();

    /**
     * The delivered share of the ordered items.
     *
     * @return a value from 0 to 1, or 0 when nothing is ordered
     */
    double progress();

    Optional<? extends IOrderContent> content(Material material);

    boolean isDone();
}
