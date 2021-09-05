package de.eldoria.companies.orders;

import de.eldoria.companies.configuration.elements.OrderSettings;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.data.wrapper.order.MaterialPrice;
import de.eldoria.companies.data.wrapper.order.OrderContent;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.eldoutilities.localization.MessageComposer;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static de.eldoria.companies.util.Colors.ADD;
import static de.eldoria.companies.util.Colors.MODIFY;
import static de.eldoria.companies.util.Colors.NAME;
import static de.eldoria.companies.util.Colors.REMOVE;
import static de.eldoria.companies.util.Colors.VALUE;

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

    public int amount(@Nullable Material material) {
        return elements.stream().filter(m -> m.material() != material).mapToInt(OrderContent::amount).sum();
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
                .text("<%s>%s <click:suggest_command:/order create name ><%s>[", NAME, name(), MODIFY).localeCode("change").text("]</click>").newLine()
                .localeCode("Items").text(": ");

        if (setting.maxItems() != amount() && elements.size() != setting.maxMaterials()) {
            composer.space().text("<click:suggest_command:%s add ><%s>[", cmd, ADD).localeCode("add").text("]</click>");
        }

        for (var element : elements) {
            var materialPrice = orderData.getMaterialPrice(element.materialString()).orElse(new MaterialPrice(element.materialString()));
            composer.newLine()
                    .text("<hover:show_text:%s>%s</hover>", materialPrice.asComponent(economy), element.asComponent(economy))
                    .text("<click:run_command:%s remove %s><%s>[", cmd, element.materialString(), REMOVE)
                    .localeCode("remove")
                    .text("] <click:suggest_command:%s price %s ><%s><%s>[", cmd, element.materialString(), MODIFY).localeCode("price")
                    .text("]</click> <click:suggest_command:%s amount %s ><%s>[", cmd, element.materialString(), MODIFY).localeCode("amount").text("]</click>");
        }
        composer.newLine()
                .text("<%s>", NAME).localeCode("Materials").text(": <%s>%s/%s",VALUE, materialsAmount(), setting.maxMaterials()).newLine()
                .text("<%s>", NAME).localeCode("Items").text(": <%s>%s/%s",VALUE, amount(), setting.maxItems()).newLine()
                .text("<%s>", NAME).localeCode("Price").text(": <%s>%s", VALUE,economy.format(price())).newLine()
                .text("<click:run_command:%s done><%s>[", cmd, ADD).localeCode("done").text("]</click>").space()
                .text("<click:run_command:%s cancel><%s>[", cmd, REMOVE).localeCode("cancel").text("]</click>");
        return composer.build();
    }

    public void removeContent(Material parse) {
        elements.removeIf(o -> o.material() == parse);
    }

    public void changeContentAmount(Material material, int amount) {
        elements().stream().filter(m -> m.material() == material).findAny().ifPresent(m -> m.amount(amount));
    }

    public void changeContentPrice(Material material, double price) {
        elements().stream().filter(m -> m.material() == material).findAny().ifPresent(m -> m.price(price));
    }
}
