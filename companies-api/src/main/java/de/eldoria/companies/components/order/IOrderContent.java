package de.eldoria.companies.components.order;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public interface IOrderContent {
    ItemStack stack();

    int amount();

    void amount(int amount);

    List<? extends IContentPart> parts();

    double price();

    default String prettyType() {
        return stack().getType().name().replace("_", " ");
    }

    int delivered();

    double percent();

    String materialString();

    Material material();

    int missing();
}
