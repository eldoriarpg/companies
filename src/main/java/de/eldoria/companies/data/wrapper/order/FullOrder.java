package de.eldoria.companies.data.wrapper.order;

import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.orders.OrderState;
import de.eldoria.companies.orders.PaymentType;
import de.eldoria.companies.permissions.CompanyPermission;
import de.eldoria.eldoutilities.localization.ILocalizer;
import de.eldoria.eldoutilities.localization.MessageComposer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
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
    private static final MiniMessage MINI_MESSAGE = MiniMessage.get();
    private final List<OrderContent> contents;

    public FullOrder(int id, UUID owner, String name, LocalDateTime created, int company, LocalDateTime claimed,
                     OrderState state, List<OrderContent> contents) {
        super(id, owner, name, created, company, claimed, state);
        this.contents = contents;
    }

    public String userShortInfo(Economy economy) {
        var composer = MessageComposer.create().text(id() + " | " + name()).newLine()
                .text("<hover:show_text:" + userContent(economy) + "\nPrice:" + economy.format(price()) + ">"
                      + "<click:run_command:/order info " + id() + ">[info]</click>"
                      + "</hover>");

        return composer.build();
    }

    public String companyShortInfo(Economy economy) {
        var composer = MessageComposer.create().text("<hover:show_text:%s>%s | %s</hover>",
                        companySimpleContent(economy, state()) + "\nPrice:" + economy.format(price()), id(), name())
                .text("<click:run_command:/company order info %s>[info]</click>", id());
        return composer.build();
    }

    public String userDetailInfo(Economy economy) {
        var composer = MessageComposer.create().text("%s | %s", id(), name()).newLine()
                .text("State: %s", state().name().toLowerCase()).newLine()
                .text(userContent(economy)).newLine()
                .text("Price: %s", price()).newLine();
        switch (state()) {
            case UNCLAIMED:
                composer.text("<click:run_command:/order cancel %s>[cancel]", id());
                break;
            case DELIVERED:
                composer.text("<click:run_command:/order receive %s>[cancel]", id());
                break;
            case RECEIVED:
            case CLAIMED:
                break;
        }
        return composer.build();
    }

    public String companyDetailInfo(CompanyMember member, Configuration configuration, ILocalizer localizer, Economy economy) {
        var composer = MessageComposer.create().text(id() + " | " + name()).newLine()
                .text("State: " + state().name().toLowerCase()).newLine()
                .text(companyActionContent(economy, state())).newLine()
                .text("Price: " + price()).newLine();

        switch (state()) {
            case UNCLAIMED:
                if (member.hasPermission(CompanyPermission.MANAGE_ORDERS)) {
                    composer.text("<click:run_command:/company order accept " + id() + ">[accept]</click>");
                }
                break;
            case CLAIMED:
                composer.localeCode("Left time ").text(runningOutTime(configuration)).newLine();
                if (member.hasPermission(CompanyPermission.MANAGE_ORDERS)) {
                    composer.text("<click:run_command:/company order abort " + id() + ">[abort]</click>");
                }
                break;
            case DELIVERED:
            case RECEIVED:
        }
        return composer.build();
    }

    private String userContent(Economy economy) {
        List<String> contents = new ArrayList<>();
        for (var content : this.contents) {
            contents.add(content.asComponent(economy));
        }
        return String.join("\n", contents);
    }

    private String companyActionContent(Economy economy, OrderState state) {
        List<String> contents = new ArrayList<>();
        for (var content : this.contents) {
            if (state == OrderState.UNCLAIMED) {
                contents.add(content.asComponent(economy));
            } else {
                var composer = MessageComposer.create().text(content.asProgressComponent(economy)).space();
                if (content.missing() != 0) {
                    var baseCommand = "/company order deliver " + id() + " " + content.stack().getType() + " ";
                    composer.text("<click:run_command:" + baseCommand + " max>[max]</click>")
                            .text("<click:run_command:" + baseCommand + " 1>[1]</click>")
                            .text("<click:run_command:" + baseCommand + " 64>[64]</click>");
                } else {
                    composer.text("Done");
                }
                contents.add(composer.build());
            }
        }
        return String.join("\n", contents);
    }

    private String companySimpleContent(Economy economy, OrderState state) {
        List<String> contents = new ArrayList<>();
        for (var content : this.contents) {
            if (state == OrderState.UNCLAIMED) {
                contents.add(content.asComponent(economy));
            } else {
                contents.add(content.asProgressComponent(economy));
            }
        }
        return String.join("\n", contents);
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
