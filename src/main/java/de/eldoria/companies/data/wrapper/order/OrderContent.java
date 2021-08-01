package de.eldoria.companies.data.wrapper.order;

import de.eldoria.eldoutilities.localization.ILocalizer;
import net.kyori.adventure.text.Component;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class OrderContent {
    private ItemStack stack;
    private int amount;
    private List<ContentPart> parts = new ArrayList<>();
    private float price;

    public OrderContent(ItemStack stack, int amount, float price) {
        this.stack = stack;
        this.amount = amount;
        this.price = price;
    }

    public void parts(List<ContentPart> parts) {
        this.parts = parts;
    }

    public ItemStack stack() {
        return stack;
    }

    public int amount() {
        return amount;
    }

    public List<ContentPart> parts() {
        return parts;
    }

    public float price() {
        return price;
    }

    public Component asComponent(ILocalizer localizer, Economy economy) {
        return Component.text()
                .append(Component.text(stack.getType().name().replace("_", " ")))
                .append(Component.text("[x" + amount + "]"))
                .append(Component.text(" " + economy.format(price))).build();
    }
}
