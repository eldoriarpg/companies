/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.order;

import de.eldoria.eldoutilities.localization.MessageComposer;
import net.milkbowl.vault.economy.Economy;

public class MaterialPrice implements de.eldoria.companies.components.order.IMaterialPrice {
    private final String material;
    private final double avgPrice;
    private final double minPrice;
    private final double maxPrice;

    public MaterialPrice(String material) {
        this.material = material;
        avgPrice = 0;
        minPrice = 0;
        maxPrice = 0;
    }

    public MaterialPrice(String material, double avgPrice, double minPrice, double maxPrice) {
        this.material = material;
        this.avgPrice = avgPrice;
        this.minPrice = minPrice;
        this.maxPrice = maxPrice;
    }

    @Override
    public String material() {
        return material;
    }

    @Override
    public double avgPrice() {
        return avgPrice;
    }

    @Override
    public double minPrice() {
        return minPrice;
    }

    @Override
    public double maxPrice() {
        return maxPrice;
    }

    public String asComponent(Economy economy) {
        return MessageComposer.create()
                .text("<yellow>").localeCode("words.avg").text(": %s", economy.format(avgPrice)).newLine()
                .text("<green>").localeCode("words.min").text(": %s", economy.format(minPrice)).newLine()
                .text("<red>").localeCode("words.max").text(": %s", economy.format(maxPrice))
                .build();
    }

    @Override
    public String toString() {
        return "MaterialPrice{" +
               "material='" + material + '\'' +
               ", avgPrice=" + avgPrice +
               ", minPrice=" + minPrice +
               ", maxPrice=" + maxPrice +
               '}';
    }
}
