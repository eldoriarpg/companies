package de.eldoria.companies.data.wrapper.order;

import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.orders.OrderState;
import de.eldoria.companies.orders.PaymentType;
import de.eldoria.companies.permissions.CompanyPermission;
import de.eldoria.eldoutilities.localization.ILocalizer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class FullOrder extends SimpleOrder {
    private final List<OrderContent> contents;

    public FullOrder(int id, UUID owner, String name, LocalDateTime created, int company, LocalDateTime claimed,
                     OrderState state, List<OrderContent> contents) {
        super(id, owner, name, created, company, claimed, state);
        this.contents = contents;
    }

    public Component userShortInfo(ILocalizer localizer, Economy economy) {
        return Component.text().append(Component.text(id()).append(Component.text(" | "))
                .append(Component.text(name())
                        .hoverEvent(HoverEvent.showText(() ->
                                userContent(localizer, economy).append(Component.newline())
                                        .append(Component.text("Price " + price())))))

                .append(Component.text("[info]")
                        .clickEvent(ClickEvent.runCommand("/order info " + id())))).asComponent();
    }

    public Component companyShortInfo(ILocalizer localizer, Economy economy) {
        return Component.text().append(Component.text(id()).append(Component.text(" | ")).append(Component.text(name())
                        .append(Component.text("[info]")
                                .clickEvent(ClickEvent.runCommand("/company order info " + id())))).asComponent()
                .hoverEvent(HoverEvent.showText(() ->
                        companySimpleContent(localizer, economy, state()).append(Component.newline())
                                .append(Component.text("Price " + price()))))).build();
    }

    public Component userDetailInfo(ILocalizer localizer, Economy economy) {
        var build = Component.text().append(Component.text(id())).append(Component.text(" | ")).append(Component.text(name())
                        .append(Component.newline())
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

    public Component companyDetailInfo(CompanyMember member, ILocalizer localizer, Economy economy) {
        var build = Component.text().append(Component.text(id())).append(Component.text(" | ")).append(Component.text(name())
                        .append(Component.newline())
                        .append(Component.text("State: " + state().name().toLowerCase()))
                        .append(Component.newline())
                        .append(companyActionContent(localizer, economy, state()))
                        .append(Component.newline())
                        .append(Component.text("Price " + price())))
                .append(Component.newline());
        switch (state()) {
            case UNCLAIMED:
                if (member.hasPermission(CompanyPermission.MANAGE_ORDERS)) {
                    build.append(Component.text("[accept]")
                            .clickEvent(ClickEvent.runCommand("/company order accept " + id())));
                }
                break;
            case CLAIMED:
                if (member.hasPermission(CompanyPermission.MANAGE_ORDERS)) {
                    build.append(Component.text("[abort]")
                            .clickEvent(ClickEvent.runCommand("/company order abort " + id())));
                }
                break;
            case DELIVERED:
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

    private Component companyActionContent(ILocalizer localizer, Economy economy, OrderState state) {
        List<Component> contents = new ArrayList<>();
        for (var content : this.contents) {
            if (state == OrderState.UNCLAIMED) {
                contents.add(content.asComponent(localizer, economy));
            } else {
                var component = content.asProgressComponent(localizer, economy).append(Component.space());
                if (content.missing() != 0) {
                    component = component.append(Component.text("[max]")
                                    .clickEvent(ClickEvent.runCommand("/company order deliver " + id() + " " + content.stack().getType() + " max")))
                            .append(Component.text("[1]")
                                    .clickEvent(ClickEvent.runCommand("/company order deliver " + id() + " " + content.stack().getType() + " 1")))
                            .append(Component.text("[64]")
                                    .clickEvent(ClickEvent.runCommand("/company order deliver " + id() + " " + content.stack().getType() + " 64")));
                } else {
                    component = component.append(Component.text("Done"));
                }
                contents.add(component);
            }
        }
        return Component.join(Component.newline(), contents);
    }

    private Component companySimpleContent(ILocalizer localizer, Economy economy, OrderState state) {
        List<Component> contents = new ArrayList<>();
        for (var content : this.contents) {
            if (state == OrderState.UNCLAIMED) {
                contents.add(content.asComponent(localizer, economy));
            } else {
                contents.add(content.asProgressComponent(localizer, economy));
            }
        }
        return Component.join(Component.newline(), contents);
    }

    public List<OrderContent> contents() {
        return contents;
    }

    public double price() {
        return contents.stream().mapToDouble(OrderContent::price).sum();
    }

    public int amount() {
        return contents.stream().mapToInt(OrderContent::amount).sum();
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

    public int delivered() {
        return contents.stream().mapToInt(OrderContent::delivered).sum();
    }

    public double progress() {
        return amount() / (double) delivered();
    }

    public Optional<OrderContent> content(Material material) {
        return contents.stream().filter(content -> content.material() == material).findFirst();
    }

    public boolean isDone() {
        return amount() == delivered();
    }

    public Map<UUID, Double> payments(PaymentType paymentType) {
        Map<UUID, Double> payments = new HashMap<>();
        switch (paymentType) {
            case STACK:
                for (var content : contents) {
                    for (var entry : content.payments().entrySet()) {
                        payments.compute(entry.getKey(), (id, v) -> v == null ? entry.getValue() : v + entry.getValue());
                    }
                }
                break;
            case TOTAL:
                Map<UUID, Integer> workers = new HashMap<>();
                for (var content : contents) {
                    for (var entry : content.workerAmount().entrySet()) {
                        workers.compute(entry.getKey(), (id, v) -> v == null ? entry.getValue() : v + entry.getValue());
                    }
                }
                var amount = amount();
                var price = price();
                for (var entry : workers.entrySet()) {
                    payments.put(entry.getKey(), (entry.getValue() / (double) amount) * price);
                }
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + paymentType);
        }
        return payments;
    }
}
