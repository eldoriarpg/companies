package de.eldoria.companies.commands.company.order.search;

import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.eldoutilities.localization.ILocalizer;
import de.eldoria.eldoutilities.localization.MessageComposer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class SearchQuery {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.get();
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

    public Component asComponent(ILocalizer localizer) {
        var queryCmd = "/company order search query";
        var composer = MessageComposer.create().text("Current search Settings").newLine()
                .text("Name: ").text(name().isBlank() ? "_____" : name())
                .text("<click:suggest_command:%s name >[", queryCmd).localeCode("change").text("]</click>")
                .text("<click:run_command:%s name>[", queryCmd).localeCode("clear").text("]</click>")
                .newLine()
                .text("Materials: ")
                .text("<click:suggest_command:%s material_add>[", queryCmd).localeCode("add").text("]</click>")
                .text("<click:run_command:%s material_remove>[", queryCmd).localeCode("clear").text("]</click>")
                .newLine();
        for (var material : materials) {
            composer.text(material).text(" <click:run_command:%s material_remove %s>[", queryCmd, material).localeCode("remove").text("]</click>").newLine();
        }
        if (materials.size() > 1) {
            composer.text("Material Search ")
                    .text("<click:run_command:%s material_search any>[", queryCmd).localeCode("any").text("]</click> ")
                    .text("<click:run_command:%s material_search any>[", queryCmd).localeCode("all").text("]</click> ");
        }
        if (!materials.isEmpty()) {
            composer.localeCode("Material Match").space()
                    .text("<click:run_command:%s material_match part>[", queryCmd).localeCode("part").text("]</click>")
                    .text("<click:run_command:%s material_match exact>[", queryCmd).localeCode("exact").text("]</click>")
                    .newLine();
        }
        composer.localeCode("Min Price").space().text(String.format("%.2f", minPrice)).space()
                .text("<click:suggest_command:%s min_price >[",queryCmd).localeCode("change").text("]</click>").newLine()
                .localeCode("Max Price").space().text(maxPrice == Double.MAX_VALUE ? "MAX" : String.format("%.2f", maxPrice)).space()
                .text("<click:suggest_command:%s max_price >[",queryCmd).localeCode("change").text("]</click>").newLine()
                .localeCode("Min Size").space().text(minOrderSize).space()
                .text("<click:suggest_command:%s min_size >[",queryCmd).localeCode("change").text("]</click>").newLine()
                .localeCode("Max Size").space().text(maxOrderSize == Integer.MAX_VALUE ? "MAX" : maxOrderSize).space()
                .text("<click:suggest_command:%s max_size >[",queryCmd).localeCode("change").text("]</click>").newLine()
                .localeCode("Order by ");
        for (var sort : SortingType.values()) {
            composer.text("<click:run_command:%s sorting %s>", queryCmd, sort).text(sort == sortingType ? "<green>" : "<gray>")
                    .text("[").localeCode(sort.name()).text("]</click>");
        }
        composer.text("<click:run_command:%s order asc>%s[", queryCmd, asc ? "<green>" : "<gray>").localeCode("asc").text("]</click>").space()
                .text("<click:run_command:%s order desc>%s[", queryCmd, !asc ? "<green>" : "<gray>").localeCode("desc").text("]</click>")
                .newLine().text("<reset>")
                .text("<click:run_command:%s execute>[",queryCmd).localeCode("Search").text("]</click>")
                .text("<click:run_command:%s clear>[",queryCmd).localeCode("Clear").text("]</click>");
        return MINI_MESSAGE.parse(localizer.localize(composer.build()));
    }
}
