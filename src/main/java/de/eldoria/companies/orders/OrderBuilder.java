package de.eldoria.companies.orders;

import de.eldoria.companies.configuration.elements.OrderSettings;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.data.wrapper.order.OrderContent;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.eldoutilities.localization.MessageComposer;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class OrderBuilder {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.get();
    private final SimpleOrder order;
    private final List<OrderContent> elements = new ArrayList<>();

    public OrderBuilder(UUID owner, String name) {
        order = new SimpleOrder(owner, name);
    }

    public void addContent(ItemStack stack, int amount, double price) {
        var first = elements.stream().filter(orderContent -> orderContent.material() == stack.getType()).findFirst();
        first.ifPresentOrElse(o -> {
            o.amount(o.amount() + amount);
            o.price(o.price() + price);
        }, () -> elements.add(new OrderContent(stack, amount, price)));
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

    public void name(String name) {
        order.name(name);
    }

    public List<OrderContent> elements() {
        return elements;
    }

    public double price() {
        return elements.stream().mapToDouble(OrderContent::price).sum();
    }

    public int amount() {
        return elements.stream().mapToInt(OrderContent::amount).sum();
    }

    public int materialsAmount() {
        return elements.size();
    }

    public boolean hasMaterial(Material material) {
        return elements.stream().anyMatch(e -> e.stack().getType() == material);
    }

    public String asComponent(OrderSettings setting, Economy economy, AOrderData orderData) {
        var cmd = "/order create";
        var composer = MessageComposer.create()
                .text("%s <click:suggest_command:/order create name >[", name()).localeCode("change").text("]</click>").newLine()
                .localeCode("Items").text(": ");

        if (setting.maxItems() != amount() && elements.size() != setting.maxMaterials()) {
            composer.space().text("<click:suggest_command:%s add >[", cmd).localeCode("add").text("]</click>");
        }

        for (var element : elements) {
            composer.newLine()
                    .text("<hover:show_text:%s>%s</hover>", orderData.getMaterialPrice(element.materialString()), element.asComponent(economy))
                    .text("<click:run_command:%s remove %s><red>[", cmd, element.materialString())
                    .localeCode("remove")
                    .text("]<reset>");
        }
        composer.newLine().localeCode("Materials").text(": %s/%s", materialsAmount(), setting.maxMaterials()).newLine()
                .localeCode("Items").text(": %s/%s", amount(), setting.maxItems()).newLine()
                .localeCode("Price").text(": %s", economy.format(price())).newLine()
                .text("<click:run_command:%s done>[", cmd).localeCode("done").text("]</click>").space()
                .text("<click:run_command:%s cancel>[", cmd).localeCode("cancel").text("]</click>");
        return composer.build();
    }

    public void removeContent(Material parse) {
        elements.removeIf(o -> o.material() == parse);
    }
}
