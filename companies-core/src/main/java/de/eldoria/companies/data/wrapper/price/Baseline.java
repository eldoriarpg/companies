/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.price;

import de.eldoria.companies.components.price.PriceBasis;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The price of one item of a material the shops of the server give it.
 *
 * @param material name of the material, upper case
 * @param sellUnit what shops pay a player for one item, null when unknown
 * @param buyUnit  what a player pays shops for one item, null when unknown
 * @param unit     the baseline: between the two sides, within the prices of the server
 * @param basis    the least trusted kind of price the sides were worked out from
 * @param sources  the ids of the sources whose prices the sides were worked out from, sorted
 */
public record Baseline(
        String material,
        @Nullable Double sellUnit,
        @Nullable Double buyUnit,
        double unit,
        PriceBasis basis,
        List<String> sources) {
    /**
     * Keeps a copy of the sources.
     */
    public Baseline {
        sources = List.copyOf(sources);
    }
}
