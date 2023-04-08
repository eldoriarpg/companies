/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company.order.search;

import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.util.Colors;
import de.eldoria.eldoutilities.localization.MessageComposer;
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
        if (materials.isEmpty()) return ".*";
        if (anyMaterial) {
            return materials.stream()
                            .map(mat -> "(" + regexMat(mat) + ")")
                            .collect(Collectors.joining("|"));
        }
        return materials.stream()
                        .map(mat -> "(.*" + regexMat(mat) + ")")
                        .collect(Collectors.joining(""));
    }

    private String regexMat(String mat) {
        if (exactMatch) {
            return "\\b" + mat + "\\b";
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

    public String asComponent() {
        var queryCmd = "/company order search query";
        var composer = MessageComposer.create()
                                      .text("<heading>")
                                      .localeCode("company.order.search.query.searchQuery.searchSetting")
                                      .newLine()
                                      .text("<name>")
                                      .localeCode("words.name")
                                      .text(": %s", name().isBlank() ? "_____" : name())
                                      .text("<click:suggest_command:%s name ><gold>[", queryCmd)
                                      .localeCode("words.change")
                                      .text("]</click>")
                                      .text("<click:run_command:%s name><red>[", queryCmd)
                                      .localeCode("words.clear")
                                      .text("]</click>")
                                      .newLine()
                                      .text("<name>")
                                      .localeCode("words.materials")
                                      .text(": <click:suggest_command:%s material_add ><green>[", queryCmd)
                                      .localeCode("words.add")
                                      .text("]</click>")
                                      .text("<click:run_command:%s material_remove><red>[", queryCmd)
                                      .localeCode("words.clear")
                                      .text("]</click>")
                                      .newLine();
        for (var material : materials) {
            composer.space(2)
                    .text("<value>%s", material)
                    .text(" <click:run_command:%s material_remove %s><remove>[", queryCmd, material)
                    .localeCode("words.remove")
                    .text("]</click>")
                    .newLine();
        }
        if (materials.size() > 1) {
            composer.text("<name>")
                    .localeCode("company.order.search.query.searchQuery.materialSearch")
                    .space()
                    .text("<click:run_command:%s material_search any><%s>[", queryCmd, Colors.active(anyMaterial))
                    .localeCode("words.any")
                    .text("]<reset></click> ")
                    .text("<click:run_command:%s material_search all><%s>[", queryCmd, Colors.active(!anyMaterial))
                    .localeCode("words.all")
                    .text("]<reset></click> ")
                    .newLine();
        }
        if (!materials.isEmpty()) {
            composer.text("<name>")
                    .localeCode("company.order.search.query.searchQuery.materialMatch")
                    .space()
                    .text("<click:run_command:%s material_match part><%s>[", queryCmd, Colors.active(!exactMatch))
                    .localeCode("words.part")
                    .text("]</click>")
                    .text("<click:run_command:%s material_match exact><%s>[", queryCmd, Colors.active(exactMatch))
                    .localeCode("words.exact")
                    .text("]</click>")
                    .newLine();
        }
        composer.text("<name>")
                .localeCode("company.order.search.query.searchQuery.minPrice")
                .space()
                .text("<value>%.2f", minPrice)
                .space()
                .text("<click:suggest_command:%s min_price ><modify>[", queryCmd)
                .localeCode("words.change")
                .text("]</click>")
                .newLine()
                .text("<name>")
                .localeCode("company.order.search.query.searchQuery.maxPrice")
                .space()
                .text("<value>")
                .text(maxPrice == Double.MAX_VALUE ? "MAX" : String.format("%.2f", maxPrice))
                .space()
                .text("<click:suggest_command:%s max_price ><modify>[", queryCmd)
                .localeCode("words.change")
                .text("]</click>")
                .newLine()
                .text("<name>")
                .localeCode("company.order.search.query.searchQuery.minSize")
                .space()
                .text("<value>%s", minOrderSize)
                .space()
                .text("<click:suggest_command:%s min_size ><modify>[", queryCmd)
                .localeCode("words.change")
                .text("]</click>")
                .newLine()
                .text("<name>")
                .localeCode("company.order.search.query.searchQuery.maxSize")
                .space()
                .text("<value>")
                .text(maxOrderSize == Integer.MAX_VALUE ? "MAX" : maxOrderSize)
                .space()
                .text("<click:suggest_command:%s max_size ><modify>[", queryCmd)
                .localeCode("words.change")
                .text("]</click>")
                .newLine()
                .text("<name>")
                .localeCode("company.order.search.query.searchQuery.orderBy")
                .text(":")
                .newLine();
        for (var sort : SortingType.values()) {
            composer.space(2)
                    .text("<click:run_command:%s sorting %s><%s>[", queryCmd, sort.name(), Colors.active(sort == sortingType))
                    .localeCode(sort.translationKey())
                    .text("]</click>");
        }
        composer.newLine()
                .space(2)
                .text("<click:run_command:%s order asc><%s>[", queryCmd, Colors.active(asc))
                .localeCode("words.ascending")
                .text("]</click>")
                .space()
                .text("<click:run_command:%s order desc><%s>[", queryCmd, Colors.active(!asc))
                .localeCode("$words.descending$")
                .text("]</click>")
                .newLine()
                .text("<click:run_command:%s execute><show>[", queryCmd)
                .localeCode("words.search")
                .text("]</click>")
                .text("<click:run_command:%s clear><remove>[", queryCmd)
                .localeCode("words.clear")
                .text("]</click>");
        return composer.build();
    }

    public String name() {
        return name == null ? "" : name;
    }
}
