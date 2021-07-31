package de.eldoria.companies.data.wrapper.order;

import org.bukkit.inventory.ItemStack;

import java.util.List;

public class OrderContent {
    ItemStack stack;
    int amount;
    List<ContentPart> parts;
    float price;

    public OrderContent(ItemStack stack, int amount) {
        this.stack = stack;
        this.amount = amount;
        this.parts = parts;
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
}
