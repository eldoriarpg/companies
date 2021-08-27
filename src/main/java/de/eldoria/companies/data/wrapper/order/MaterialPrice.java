package de.eldoria.companies.data.wrapper.order;

import de.eldoria.eldoutilities.localization.MessageComposer;
import net.milkbowl.vault.economy.Economy;

public class MaterialPrice {
    private final String material;
    private final double avgPrice;
    private final double minPrice;
    private final double maxPrice;

    public MaterialPrice(String material, double avgPrice, double minPrice, double maxPrice) {
        this.material = material;
        this.avgPrice = avgPrice;
        this.minPrice = minPrice;
        this.maxPrice = maxPrice;
    }

    public String material() {
        return material;
    }

    public double avgPrice() {
        return avgPrice;
    }

    public double minPrice() {
        return minPrice;
    }

    public double maxPrice() {
        return maxPrice;
    }

    public String asComponent(Economy economy) {
        return MessageComposer.create()
                .localeCode("Avg").text(": %s", economy.format(avgPrice)).newLine()
                .localeCode("Min").text(": %s", economy.format(minPrice)).newLine()
                .localeCode("Max").text(": %s", economy.format(maxPrice))
                .build();
    }
}
