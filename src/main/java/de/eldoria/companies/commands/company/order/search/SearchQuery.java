package de.eldoria.companies.commands.company.order.search;

import de.eldoria.companies.data.wrapper.order.FullOrder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
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
        this.minPrice = minPrice;
        validatePrice();
    }

    public double maxPrice() {
        return maxPrice;
    }

    public void maxPrice(double maxPrice) {
        this.maxPrice = maxPrice;
        validatePrice();
    }

    private void validatePrice() {
        minOrderSize = Math.min(minOrderSize, 0);
        maxOrderSize = Math.max(minOrderSize, maxOrderSize);
        minOrderSize = Math.min(minOrderSize, maxOrderSize);
    }

    public int minOrderSize() {
        return minOrderSize;
    }

    public void minOrderSize(int minOrderSize) {
        this.minOrderSize = minOrderSize;
        validateSize();
    }

    public int maxOrderSize() {
        return maxOrderSize;
    }

    public void maxOrderSize(int maxOrderSize) {
        this.maxOrderSize = maxOrderSize;
        validateSize();
    }

    private void validateSize() {
        minOrderSize = Math.min(minOrderSize, 0);
        maxOrderSize = Math.max(minOrderSize, maxOrderSize);
        minOrderSize = Math.min(minOrderSize, maxOrderSize);
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

    public Component asComponent() {
        var queryCmd = "/company order search query ";
        var builder = Component.text()
                .append(Component.text("Current search Settings")).append(Component.newline())
                .append(Component.text("Name: ")).append(Component.text(name().isBlank() ? "_____" : name()))
                .append(Component.text("[change]").clickEvent(ClickEvent.suggestCommand(queryCmd + "name ")))
                .append(Component.text("[clear]").clickEvent(ClickEvent.runCommand(queryCmd + "name")))
                .append(Component.newline())
                .append(Component.text("Materials:"))
                .append(Component.text("[add]").clickEvent(ClickEvent.suggestCommand(queryCmd + "material_add ")))
                .append(Component.text("[clear]").clickEvent(ClickEvent.suggestCommand(queryCmd + "material_remove")))
                .append(Component.newline());
        for (var material : materials) {
            builder.append(Component.text(material))
                    .append(Component.text("[Remove]").clickEvent(ClickEvent.runCommand(queryCmd + "material_remove " + material)))
                    .append(Component.newline());
        }
        if (materials.size() > 1) {
            builder.append(Component.text("Material Search "))
                    .append(Component.text("[ANY]", anyMaterial ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY)
                            .clickEvent(ClickEvent.runCommand(queryCmd + " material_search any")))
                    .append(Component.text("[ALL]", anyMaterial ? NamedTextColor.DARK_GRAY : NamedTextColor.GREEN)
                            .clickEvent(ClickEvent.runCommand(queryCmd + " material_search all")))
                    .append(Component.newline());
        }
        if (!materials.isEmpty()) {
            builder.append(Component.text("Material Match "))
                    .append(Component.text("[PART]", exactMatch ? NamedTextColor.DARK_GRAY : NamedTextColor.GREEN)
                            .clickEvent(ClickEvent.runCommand(queryCmd + " material_match part")))
                    .append(Component.text("[EXACT]", exactMatch ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY)
                            .clickEvent(ClickEvent.runCommand(queryCmd + " material_match exact")))
                    .append(Component.newline());
        }
        builder.append(Component.text("Min Price: ")).append(Component.text(minPrice))
                .append(Component.text("[change]").clickEvent(ClickEvent.suggestCommand(queryCmd + "min_price ")))
                .append(Component.newline())
                .append(Component.text("Max Price: ")).append(Component.text(String.valueOf(maxPrice == Double.MAX_VALUE ? "MAX" : maxPrice)))
                .append(Component.text("[change]").clickEvent(ClickEvent.suggestCommand(queryCmd + "max_price ")))
                .append(Component.newline())
                .append(Component.text("Min Size: ")).append(Component.text(minOrderSize))
                .append(Component.text("[change]").clickEvent(ClickEvent.suggestCommand(queryCmd + "min_size ")))
                .append(Component.newline())
                .append(Component.text("Max Size: ")).append(Component.text(String.valueOf(maxOrderSize == Integer.MAX_VALUE ? "MAX" : maxOrderSize)))
                .append(Component.text("[change]").clickEvent(ClickEvent.suggestCommand(queryCmd + "max_size ")))
                .append(Component.newline())
                .append(Component.text("Order by: "));
        for (var sort : SortingType.values()) {
            builder.append(Component.text("[" + sort.name() + "]", sort == sortingType ? NamedTextColor.GREEN : NamedTextColor.GRAY)
                    .clickEvent(ClickEvent.runCommand(queryCmd + "sorting " + sort)));
        }
        builder.append(Component.text("[asc]", asc ? NamedTextColor.GREEN : NamedTextColor.GRAY)
                        .clickEvent(ClickEvent.runCommand(queryCmd + "order asc")))
                .append(Component.text("[desc]", !asc ? NamedTextColor.GREEN : NamedTextColor.GRAY)
                        .clickEvent(ClickEvent.runCommand(queryCmd + "order desc")))
                .append(Component.newline());
        builder.append(Component.text("[Search]").clickEvent(ClickEvent.runCommand(queryCmd + "execute")))
                .append(Component.text("[Clear]").clickEvent(ClickEvent.runCommand(queryCmd + "clear")));
        return builder.build();
    }
}
