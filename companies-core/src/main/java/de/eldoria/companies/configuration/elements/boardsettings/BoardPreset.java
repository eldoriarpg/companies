/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements.boardsettings;

import de.eldoria.companies.orders.query.OrderFilter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * A named board filter of the {@code boards.presets} section. A highest price or size of zero means no limit.
 */
@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "CanBeFinal"})
public class BoardPreset {
    private String label = "";
    private String name = "";
    private List<String> materials = new ArrayList<>();
    private boolean anyMaterial = true;
    private boolean exactMatch;
    private double minPrice;
    private double maxPrice;
    private int minSize;
    private int maxSize;

    public BoardPreset() {}

    /**
     * @param label     name shown on signs
     * @param materials materials of the orders shown
     * @param exact     whether the materials have to match the whole name
     */
    public BoardPreset(String label, List<String> materials, boolean exact) {
        this.label = label;
        this.materials = new ArrayList<>(materials);
        this.exactMatch = exact;
    }

    /**
     * The label shown on signs, or the fallback when none is set.
     *
     * @param fallback text used without a label, usually the preset name
     * @return the label
     */
    public String label(String fallback) {
        return label == null || label.isBlank() ? fallback : label.strip();
    }

    /**
     * @return the orders this preset shows
     */
    public OrderFilter filter() {
        var orderName = name == null || name.isBlank() ? null : name.strip();
        var listed = materials == null
                ? List.<String>of()
                : materials.stream()
                        .filter(Objects::nonNull)
                        .map(material -> material.strip().toLowerCase(Locale.ROOT))
                        .filter(material -> !material.isEmpty())
                        .toList();
        var lowestPrice = Math.max(0.0, minPrice);
        var highestPrice = maxPrice <= 0.0 ? Double.MAX_VALUE : Math.max(lowestPrice, maxPrice);
        var lowestSize = Math.max(0, minSize);
        var highestSize = maxSize <= 0 ? Integer.MAX_VALUE : Math.max(lowestSize, maxSize);
        return new OrderFilter(
                orderName, listed, anyMaterial, exactMatch, lowestPrice, highestPrice, lowestSize, highestSize);
    }
}
