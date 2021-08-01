package de.eldoria.companies.data.wrapper.order;

import de.eldoria.companies.orders.OrderState;
import de.eldoria.eldoutilities.localization.ILocalizer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.inventory.ItemStack;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class FullOrder extends SimpleOrder {
    private final List<OrderContent> contents;

    public FullOrder(long id, UUID owner, String name, LocalDateTime created, int company, LocalDateTime claimed, OrderState state, List<OrderContent> contents) {
        super(id, owner, name, created, company, claimed, state);
        this.contents = contents;
    }

    public Component userShortInfo(ILocalizer localizer, Economy economy) {
        return Component.text().append(Component.text(id()).append(Component.text(" | ")).append(Component.text(name())
                        .append(Component.text("[info]")
                                .clickEvent(ClickEvent.runCommand("/order info " + id())))).asComponent()
                .hoverEvent(HoverEvent.showText(() ->
                        userContent(localizer, economy).append(Component.newline())
                                .append(Component.text("Price " + price()))))).build();
    }

    public Component userDetailInfo(ILocalizer localizer, Economy economy) {
        var build = Component.text().append(Component.text(id())).append(Component.text(" | ")).append(Component.text(name())
                        .append(Component.text("State: " + state().name().toLowerCase()))
                        .append(Component.newline())
                        .append(userContent(localizer, economy))
                        .append(Component.newline())
                        .append(Component.text("Price " + price())))
                .append(Component.newline());
        switch (state()) {
            case UNCLAIMED:
                build.append(Component.text("[cancel]")
                        .clickEvent(ClickEvent.runCommand("/order cancel " + id())));
                break;
            case CLAIMED:
                break;
            case DELIVERED:
                build.append(Component.text("[receive]")
                        .clickEvent(ClickEvent.runCommand("/order receive " + id())));
                break;
            case RECEIVED:
                break;
        }
        return build.build();
    }

    private Component userContent(ILocalizer localizer, Economy economy) {
        List<Component> contents = new ArrayList<>();
        for (var content : this.contents) {
            contents.add(content.asComponent(localizer, economy));
        }
        return Component.join(Component.newline(), contents);
    }

    public List<OrderContent> contents() {
        return contents;
    }

    public float price() {
        return (float) contents.stream().mapToDouble(OrderContent::price).sum();
    }

    public float amount() {
        return (float) contents.stream().mapToDouble(OrderContent::amount).sum();
    }

    public int materialsAmount() {
        return contents.size();
    }

    public List<ItemStack> createStacks() {
        List<ItemStack> stacks = new ArrayList<>();
        for (var content : contents) {
            var baseStack = content.stack();
            var amount = content.amount();
            while (amount != 0) {
                var size = Math.min(baseStack.getType().getMaxStackSize(), amount);
                amount -= size;
                var currStack = baseStack.clone();
                currStack.setAmount(size);
                stacks.add(currStack);
            }
        }
        return stacks;
    }
}
