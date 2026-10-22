/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company.order.search;

import de.eldoria.companies.data.wrapper.order.FullOrder;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class SearchQuery {
    private String name;
    private List<String> materials = new ArrayList<>();
    private boolean anyMaterial = true;
    private boolean exactMatch;
    private double minPrice;
    private double maxPrice = Double.MAX_VALUE;
    private int minOrderSize;
    private int maxOrderSize = Integer.MAX_VALUE;
    private SortingType sortingType = SortingType.AGE;
    private boolean asc = true;
    private boolean fillable;

    public void sort(List<FullOrder> orders) {
        sortingType().sort(orders, isAsc());
    }

    public SortingType sortingType() {
        return sortingType;
    }

    public boolean isAsc() {
        return asc;
    }

    public void name(@Nullable String name) {
        if (name != null) {
            this.name = name.substring(0, Math.min(name.length(), 32));
            return;
        }
        this.name = null;
    }

    public List<String> materials() {
        return materials;
    }

    public void materials(List<String> materials) {
        this.materials = materials;
    }

    public double minPrice() {
        return minPrice;
    }

    public void minPrice(double minPrice) {
        this.minPrice = Math.max(minPrice, 0.0);
        maxPrice = Math.max(this.minPrice, maxPrice);
    }

    public double maxPrice() {
        return maxPrice;
    }

    public void maxPrice(double maxPrice) {
        this.maxPrice = Math.max(maxPrice, 0.0);
        minPrice = Math.min(minPrice, this.maxPrice);
    }

    public int minOrderSize() {
        return minOrderSize;
    }

    public void minOrderSize(int minOrderSize) {
        this.minOrderSize = Math.max(minOrderSize, 0);
        maxOrderSize = Math.max(this.minOrderSize, maxOrderSize);
    }

    public int maxOrderSize() {
        return maxOrderSize;
    }

    public void maxOrderSize(int maxOrderSize) {
        this.maxOrderSize = Math.max(maxOrderSize, 0);
        minOrderSize = Math.min(minOrderSize, this.maxOrderSize);
    }

    public void sortingType(SortingType sortingType) {
        this.sortingType = sortingType;
    }

    public void asc(boolean asc) {
        this.asc = asc;
    }

    public String materialRegex() {
        return materialRegex("\\b");
    }

    /**
     * Builds a regex matching the space separated materials of an order.
     * <p>
     * When all materials are required, every material is checked with a lookahead, so the order of the materials does not matter.
     *
     * @param wordBoundary word boundary of the regex dialect, which is {@code \b} for most dialects and {@code \y} for PostgreSQL
     * @return regex for the materials of this query
     */
    public String materialRegex(String wordBoundary) {
        if (materials.isEmpty()) return ".*";
        if (anyMaterial) {
            return materials.stream()
                    .map(mat -> "(" + regexMat(mat, wordBoundary) + ")")
                    .collect(Collectors.joining("|"));
        }
        return materials.stream()
                .map(mat -> "(?=.*" + regexMat(mat, wordBoundary) + ")")
                .collect(Collectors.joining(""));
    }

    private String regexMat(String mat, String wordBoundary) {
        if (exactMatch) {
            return wordBoundary + mat + wordBoundary;
        }
        return mat;
    }

    public void anyMaterial(boolean anyMaterial) {
        this.anyMaterial = anyMaterial;
    }

    public void exactMatch(boolean exactMatch) {
        this.exactMatch = exactMatch;
    }

    public boolean isAnyMaterial() {
        return anyMaterial;
    }

    public boolean isExactMatch() {
        return exactMatch;
    }

    public String name() {
        return name == null ? "" : name;
    }

    /**
     * Whether only orders the searching player can deliver items to are shown.
     *
     * @return true when the search filters by the player's items
     */
    public boolean fillable() {
        return fillable;
    }

    public void fillable(boolean fillable) {
        this.fillable = fillable;
    }
}
