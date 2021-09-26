package de.eldoria.companies.commands.company.order.search;

import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.util.Colors;
import de.eldoria.eldoutilities.localization.MessageComposer;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class SearchQuery {
    private String name = null;
    private List<String> materials = new ArrayList<>();
    private boolean anyMaterial = true;
    private boolean exactMatch = false;
    private double minPrice = 0.0;
    private double maxPrice = Double.MAX_VALUE;
    private int minOrderSize = 0;
    private int maxOrderSize = Integer.MAX_VALUE;
    private SortingType sortingType = SortingType.AGE;
    private boolean asc = true;

    public void sort(List<FullOrder> orders) {
        sortingType().sort(orders, isAsc());
    }

    public String name() {
        return name == null ? "" : name;
    }

    public void name(@Nullable String name) {
        if (name != null) {
            this.name = name.substring(0, Math.min(name.length(), 32));
            return;
        }
        this.name = name;
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
        this.minPrice = Math.max(minPrice, 0);
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

    public SortingType sortingType() {
        return sortingType;
    }

    public void sortingType(SortingType sortingType) {
        this.sortingType = sortingType;
    }

    public boolean isAsc() {
        return asc;
    }

    public void asc(boolean asc) {
        this.asc = asc;
    }

    public String materialRegex() {
        if (materials.isEmpty()) return ".*";
        if (anyMaterial) {
            return materials.stream().map(mat -> "(" + regexMat(mat) + ")").collect(Collectors.joining("|"));
        }
        return materials.stream().map(mat -> "(.*" + regexMat(mat) + ")").collect(Collectors.joining(""));
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
        var composer = MessageComposer.create().text("<%s>", Colors.HEADING).localeCode("company.order.search.query.searchQuery.searchSetting").newLine()
                .text("<%s>", Colors.NAME).localeCode("words.name").text(": %s", name().isBlank() ? "_____" : name())
                .text("<click:suggest_command:%s name ><gold>[", queryCmd).localeCode("words.change").text("]</click>")
                .text("<click:run_command:%s name><red>[", queryCmd).localeCode("words.clear").text("]</click>")
                .newLine()
                .text("<%s>", Colors.NAME).localeCode("words.materials")
                .text(": <click:suggest_command:%s material_add ><green>[", queryCmd).localeCode("words.add").text("]</click>")
                .text("<click:run_command:%s material_remove><red>[", queryCmd).localeCode("words.clear").text("]</click>")
                .newLine();
        for (var material : materials) {
            composer.space(2).text("<%s>%s", Colors.VALUE, material).text(" <click:run_command:%s material_remove %s><%s>[", queryCmd, material, Colors.REMOVE)
                    .localeCode("words.remove").text("]</click>").newLine();
        }
        if (materials.size() > 1) {
            composer.text("<%s>", Colors.NAME).localeCode("company.order.search.query.searchQuery.materialSearch").space()
                    .text("<click:run_command:%s material_search any><%s>[", queryCmd, Colors.active(anyMaterial))
                    .localeCode("words.any").text("]<reset></click> ")
                    .text("<click:run_command:%s material_search all><%s>[", queryCmd, Colors.active(!anyMaterial))
                    .localeCode("words.all").text("]<reset></click> ")
                    .newLine();
        }
        if (!materials.isEmpty()) {
            composer.text("<%s>", Colors.NAME).localeCode("company.order.search.query.searchQuery.materialMatch").space()
                    .text("<click:run_command:%s material_match part><%s>[", queryCmd, Colors.active(!exactMatch)).localeCode("words.part").text("]</click>")
                    .text("<click:run_command:%s material_match exact><%s>[", queryCmd, Colors.active(exactMatch)).localeCode("words.exact").text("]</click>")
                    .newLine();
        }
        composer.text("<%s>", Colors.NAME).localeCode("company.order.search.query.searchQuery.minPrice").space().text("<%s>%.2f", Colors.VALUE, minPrice).space()
                .text("<click:suggest_command:%s min_price ><%s>[", queryCmd, Colors.MODIFY).localeCode("words.change").text("]</click>").newLine()
                .text("<%s>", Colors.NAME).localeCode("company.order.search.query.searchQuery.maxPrice").space().text("<%s>", Colors.VALUE).text(maxPrice == Double.MAX_VALUE ? "MAX" : String.format("%.2f", maxPrice)).space()
                .text("<click:suggest_command:%s max_price ><%s>[", queryCmd, Colors.MODIFY).localeCode("words.change").text("]</click>").newLine()
                .text("<%s>", Colors.NAME).localeCode("company.order.search.query.searchQuery.minSize").space().text("<%s>%s", Colors.VALUE, minOrderSize).space()
                .text("<click:suggest_command:%s min_size ><%s>[", queryCmd, Colors.MODIFY).localeCode("words.change").text("]</click>").newLine()
                .text("<%s>", Colors.NAME).localeCode("company.order.search.query.searchQuery.maxSize").space().text("<%s>", Colors.VALUE).text(maxOrderSize == Integer.MAX_VALUE ? "MAX" : maxOrderSize).space()
                .text("<click:suggest_command:%s max_size ><%s>[", queryCmd, Colors.MODIFY).localeCode("words.change").text("]</click>").newLine()
                .text("<%s>", Colors.NAME).localeCode("company.order.search.query.searchQuery.orderBy").text(":").newLine();
        for (var sort : SortingType.values()) {
            composer.space(2).text("<click:run_command:%s sorting %s><%s>[", queryCmd, sort.name(), Colors.active(sort == sortingType))
                    .localeCode(sort.translationKey()).text("]</click>");
        }
        composer.newLine().space(2).text("<click:run_command:%s order asc><%s>[", queryCmd, Colors.active(asc)).localeCode("words.ascending").text("]</click>").space()
                .text("<click:run_command:%s order desc><%s>[", queryCmd, Colors.active(!asc)).localeCode("words.descending").text("]</click>")
                .newLine()
                .text("<click:run_command:%s execute><%s>[", queryCmd, Colors.SHOW).localeCode("words.search").text("]</click>")
                .text("<click:run_command:%s clear><red>[", queryCmd, Colors.REMOVE).localeCode("words.clear").text("]</click>");
        return composer.build();
    }
}
