package de.eldoria.companies.data.wrapper.order;

import de.eldoria.eldoutilities.localization.ILocalizer;
import net.kyori.adventure.text.Component;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

    public Component asProgressComponent(ILocalizer localizer, Economy economy) {
        return Component.text()
                .append(Component.text(stack.getType().name().replace("_", " ")))
                .append(Component.text(delivered() + "/" + amount + ""))
                .append(Component.text(" " + economy.format(price))).build();
    }

    public int delivered() {
        return parts.stream().mapToInt(ContentPart::amount).sum();
    }

    public float percent() {
        return delivered() / (float) amount;
    }

    public String materialString() {
        return stack.getType().name().toLowerCase();
    }

    public Material material() {
        return stack.getType();
    }

    public int missing() {
        return amount - delivered();
    }

    public Map<UUID, Float> payments() {
        Map<UUID, Float> payments = new HashMap<>();
        for (var part : parts) {
            payments.put(part.worker, (part.amount / (float) amount) * price);
        }
        return payments;
    }

    public Map<UUID, Integer> workerAmount() {
        Map<UUID, Integer> workerAmount = new HashMap<>();
        for (var part : parts) {
            workerAmount.put(part.worker, part.amount);
        }
        return workerAmount;
    }
}
