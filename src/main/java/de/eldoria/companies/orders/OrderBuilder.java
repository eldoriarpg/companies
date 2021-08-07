package de.eldoria.companies.orders;

import de.eldoria.companies.configuration.elements.OrderSetting;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.data.wrapper.order.OrderContent;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.eldoutilities.localization.ILocalizer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class OrderBuilder {
    private final SimpleOrder order;
    private final List<OrderContent> elements = new ArrayList<>();

    public OrderBuilder(UUID owner, String name) {
        order = new SimpleOrder(owner, name);
    }

    public void addContent(ItemStack stack, int amount, float price) {
        elements.add(new OrderContent(stack, amount, price));
    }

    public FullOrder build() {
        return order.toFullOrder(elements);
    }

    public UUID owner() {
        return order.owner();
    }

    public String name() {
        return order.name();
    }

    public List<OrderContent> elements() {
        return elements;
    }

    public float price() {
        return (float) elements.stream().mapToDouble(OrderContent::price).sum();
    }

    public float amount() {
        return (float) elements.stream().mapToDouble(OrderContent::amount).sum();
    }

    public int materialsAmount() {
        return elements.size();
    }

    public boolean hasMaterial(Material material) {
        return elements.stream().anyMatch(e -> e.stack().getType() == material);
    }

    public Component asComponent(OrderSetting setting, ILocalizer localizer, Economy economy) {
        var items = Component.text()
                .append(Component.text(order.name())).append(Component.newline())
                .append(Component.text("Items"));
        if (setting.maxItems() != amount() && setting.maxMaterials() != setting.maxMaterials()) {
            items.append(Component.space()).append(Component.text("[add]").clickEvent(ClickEvent.suggestCommand("/order create add ")));
        }

        for (var element : elements) {
            items.append(Component.newline()).append(element.asComponent(localizer, economy));
        }

        items.append(Component.newline());

        items.append(Component.text("Materials: " + materialsAmount() + "/" + setting.maxMaterials()))
                .append(Component.text("Items: " + amount() + "/" + setting.maxItems()))
                .append(Component.text(economy.format(price())))
                .append(Component.newline())
                .append(Component.text("[done]").clickEvent(ClickEvent.runCommand("/order create done")))
                .append(Component.space())
                .append(Component.text("[cancel]").clickEvent(ClickEvent.runCommand("/order create cancel")));
        return items.build();
    }
}
