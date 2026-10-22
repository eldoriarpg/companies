/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.order;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.orders.ItemMatcher;
import de.eldoria.companies.orders.PaymentType;
import de.eldoria.companies.orders.PriceLines;
import de.eldoria.companies.util.Inventories;
import de.eldoria.companies.util.Texts;
import de.eldoria.eldoutilities.localization.MessageComposer;
import de.eldoria.eldoutilities.messages.Replacement;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class FullOrder extends SimpleOrder implements de.eldoria.companies.components.order.IFullOrder {
    private final List<OrderContent> contents;

    public FullOrder(
            int id,
            UUID owner,
            String name,
            LocalDateTime created,
            int company,
            LocalDateTime claimed,
            OrderState state,
            double fee,
            int extensions,
            int deliveryBonus,
            List<OrderContent> contents) {
        super(id, owner, name, created, company, claimed, state, fee, extensions, deliveryBonus);
        this.contents = contents;
    }

    public String userShortInfo(Economy economy) {
        return MessageComposer.create()
                .text("<hover:show_text:'%s", userContent(economy))
                .newLine()
                .text("<name>")
                .localeCode("words.status")
                .text(": <value>")
                .localeCode(state().translationKey())
                .newLine()
                .text("<name>")
                .localeCode("words.price")
                .text(": <value>%s'>", economy.format(price()))
                .text(" <name>%s | <value>%s", id(), Texts.escape(name()))
                .text("</hover>")
                .space()
                .text("<click:run_command:/order info %s><add>[", id())
                .localeCode("words.info")
                .text("]</click>")
                .build();
    }

    private String userContent(Economy economy) {
        List<String> contents = new ArrayList<>();
        for (var content : this.contents) {
            contents.add(content.asComponent(economy));
        }
        return String.join("\n", contents);
    }

    /**
     * One line for the company: name and id, with the contents, the price and the company's tax in the hover.
     *
     * @param economy    economy formatting the amounts
     * @param taxPercent tax of the viewing company, where 5 means 5 %
     * @param acceptable whether to add a button which accepts the order
     * @return MiniMessage line
     */
    public String companyShortInfo(Economy economy, double taxPercent, boolean acceptable) {
        var composer = MessageComposer.create()
                .text("<hover:show_text:'%s", companySimpleContent(economy, state()))
                .newLine()
                .text("<name>")
                .localeCode("words.price")
                .text(": <value>%s", economy.format(price()));
        PriceLines.tax(composer, economy, price(), taxPercent)
                .text("'>")
                .text("<name>%s | <value>%s", id(), Texts.escape(name()))
                .text("</hover>")
                .space()
                .text("<click:run_command:/company order info %s><add>[", id())
                .localeCode("words.info")
                .text("]</click>");
        if (acceptable) {
            composer.space()
                    .text("<click:run_command:/company order accept %s><add>[", id())
                    .localeCode("words.accept")
                    .text("]</click>");
        }
        return composer.build();
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

    /**
     * The order as its owner sees it: progress per material, the price with fee and total, and when it is due or
     * refunded.
     * <p>
     * The company working on the order is never shown. An unclaimed order is refunded {@code maxUnclaimedHours} after
     * its last update, a claimed one is due {@code deliveryHours} after it was claimed.
     *
     * @param economy       economy formatting the price
     * @param configuration configuration providing the time limits
     * @return composer holding the view and its replacements
     */
    public MessageComposer userDetailInfo(Economy economy, Configuration configuration) {
        var composer = MessageComposer.create()
                .text("<name>%s | <value>%s", id(), Texts.escape(name()))
                .newLine()
                .text("<name>")
                .localeCode("words.status")
                .text(": <value>")
                .localeCode(state().translationKey())
                .newLine()
                .text(userProgressContent(economy))
                .newLine()
                .text("<name>")
                .localeCode("words.price")
                .text(": <value>%s", economy.format(price()));
        PriceLines.fee(composer, economy, price(), fee()).newLine();
        switch (state()) {
            case UNCLAIMED -> {
                composer.text("<name>")
                        .localeCode(
                                "order.info.refundIn",
                                Replacement.create(
                                        "duration",
                                        timeLeft(configuration.orderSetting().maxUnclaimedHours())))
                        .newLine()
                        .text("<click:run_command:/order cancel %s><remove>[", id())
                        .localeCode("words.cancel")
                        .text("]</click>");
                ownerActions(composer, configuration.orderSetting().maxExtensions());
            }
            case CLAIMED ->
                composer.text("<name>")
                        .localeCode(
                                "order.info.dueIn",
                                Replacement.create("duration", timeLeft(deliveryHours(configuration))));
            case DELIVERED ->
                composer.text("<click:run_command:/order receive %s><add>[", id())
                        .localeCode("words.receive")
                        .text("]</click>");
            case RECEIVED -> {}
        }
        return composer;
    }

    /**
     * Adds the buttons an owner has for an order nobody accepted: extend while extensions are left, and raise the
     * price.
     *
     * @param composer      composer to add the buttons to
     * @param maxExtensions extensions an order may have
     */
    private void ownerActions(MessageComposer composer, int maxExtensions) {
        var left = extensionsLeft(maxExtensions);
        if (left > 0) {
            composer.space()
                    .text("<click:run_command:/order extend %s><modify>[", id())
                    .localeCode("order.info.extend", Replacement.create("left", left))
                    .text("]</click>");
        }
        composer.space()
                .text("<click:suggest_command:/order raise %s ><modify>[", id())
                .localeCode("order.info.raise")
                .text("]</click>");
    }

    private String userProgressContent(Economy economy) {
        List<String> contents = new ArrayList<>();
        for (var content : this.contents) {
            contents.add(content.asProgressComponent(economy) + " " + Texts.progressBar(content.percent()));
        }
        return String.join("\n", contents);
    }

    private String timeLeft(int hours) {
        var left = Duration.between(LocalDateTime.now(), claimed().plusHours(hours));
        return Texts.prettyDuration(left.isNegative() ? Duration.ZERO : left);
    }

    /**
     * The order as a company member sees it: contents with deliver buttons, the price with the company's tax, and the
     * actions the member may take.
     *
     * @param member        viewing member
     * @param configuration configuration providing the delivery time
     * @param economy       economy formatting the amounts
     * @param taxPercent    tax of the member's company, where 5 means 5 %
     * @param matcher       item matcher, which tells whether damaged items are accepted and how worn they are
     * @return MiniMessage view
     */
    public String companyDetailInfo(
            CompanyMember member,
            Configuration configuration,
            Economy economy,
            double taxPercent,
            ItemMatcher matcher) {
        var composer = MessageComposer.create()
                .text("<name>%s | <value>%s", id(), Texts.escape(name()))
                .newLine()
                .text("<name>")
                .localeCode("words.status")
                .text(": <value>")
                .localeCode(state().translationKey())
                .newLine()
                .text(companyActionContent(economy, state(), matcher))
                .newLine()
                .text("<name>")
                .localeCode("words.price")
                .text(": <value>%s", economy.format(price()));
        PriceLines.tax(composer, economy, price(), taxPercent).newLine();

        switch (state()) {
            case UNCLAIMED:
                if (member.hasPermission(CompanyPermission.MANAGE_ORDERS)) {
                    composer.text("<click:run_command:/company order accept %s><add>[", id())
                            .localeCode("words.accept")
                            .text("]</click>");
                }
                break;
            case CLAIMED:
                composer.text("<name>")
                        .localeCode("words.leftTime")
                        .space()
                        .text("<value>%s", runningOutTime(configuration))
                        .newLine();
                if (!isDone()) {
                    composer.text("<click:run_command:/company order deliver %s all><add>[", id())
                            .localeCode("company.order.info.deliverAll")
                            .text("]</click>")
                            .space();
                }
                if (member.hasPermission(CompanyPermission.MANAGE_ORDERS)) {
                    composer.text("<click:run_command:/company order abort %s><remove>[", id())
                            .localeCode("words.abort")
                            .text("]</click>");
                }
                break;
            case DELIVERED:
            case RECEIVED:
        }
        return composer.build();
    }

    private String companyActionContent(Economy economy, OrderState state, ItemMatcher matcher) {
        List<String> contents = new ArrayList<>();
        for (var content : this.contents) {
            if (state == OrderState.UNCLAIMED) {
                contents.add(content.asComponent(economy));
                continue;
            }
            var composer = MessageComposer.create()
                    .text(content.asProgressComponent(economy))
                    .space();
            if (content.missing() > 0) {
                deliverButtons(composer, content);
            } else {
                composer.text("<value>").localeCode("words.done");
            }
            if (matcher.allowDamaged()) damage(composer, content, matcher);
            contents.add(composer.build());
        }
        return String.join("\n", contents);
    }

    /**
     * Adds the buttons delivering to a line. Lines are numbered from one in commands.
     *
     * @param composer composer to add the buttons to
     * @param content  line to deliver to
     */
    private void deliverButtons(MessageComposer composer, OrderContent content) {
        var baseCommand = String.format("/company order deliver %s %s", id(), content.line() + 1);
        composer.text("<add>")
                .text("<click:run_command:%s 1>[1]</click>", baseCommand)
                .text("<click:run_command:%s 8>[8]</click>", baseCommand)
                .text("<click:run_command:%s 32>[32]</click>", baseCommand)
                .text("<click:run_command:%s 64>[64]</click>", baseCommand)
                .text("<click:run_command:%s max>[", baseCommand)
                .localeCode("words.max")
                .text("]</click>")
                .text("<click:suggest_command:%s >[", baseCommand)
                .localeCode("words.add")
                .text("]</click>");
    }

    /**
     * Adds how many of the delivered items of a line are damaged and the wear of the most worn one in percent.
     *
     * @param composer composer to add the damage to
     * @param content  line with its delivered stacks
     * @param matcher  matcher measuring the damage
     */
    private static void damage(MessageComposer composer, OrderContent content, ItemMatcher matcher) {
        var damaged = 0;
        var worst = 0.0;
        for (var delivery : content.deliveries()) {
            var damage = matcher.damage(delivery.stack());
            if (damage <= 0) continue;
            damaged += delivery.amount();
            worst = Math.max(worst, damage);
        }
        if (damaged == 0) return;
        composer.space()
                .text("<inactive>")
                .localeCode("company.order.info.damaged")
                .text(": <value>%s <inactive>(%s %%)", damaged, Math.round(worst * 100));
    }

    @Override
    public List<OrderContent> contents() {
        return contents;
    }

    @Override
    public double price() {
        return contents.stream().mapToDouble(OrderContent::price).sum();
    }

    @Override
    public int amount() {
        return contents.stream().mapToInt(OrderContent::amount).sum();
    }

    @Override
    public int materialsAmount() {
        return contents.size();
    }

    @Override
    public int delivered() {
        return contents.stream().mapToInt(OrderContent::delivered).sum();
    }

    /**
     * The delivered share of the ordered items.
     *
     * @return a value from 0 to 1, or 0 when nothing is ordered
     */
    @Override
    public double progress() {
        var amount = amount();
        if (amount == 0) return 0;
        return delivered() / (double) amount;
    }

    @Override
    public Optional<OrderContent> line(int line) {
        return contents.stream().filter(content -> content.line() == line).findFirst();
    }

    @Override
    @Deprecated
    public Optional<OrderContent> content(Material material) {
        return contents.stream()
                .filter(content -> !content.exact() && content.material() == material)
                .findFirst();
    }

    @Override
    public boolean isDone() {
        return delivered() >= amount();
    }

    /**
     * The delivered items of every line, as they were delivered, split into stacks of at most their max stack size.
     *
     * @return delivered stacks
     */
    public List<ItemStack> deliveredStacks() {
        List<ItemStack> stacks = new ArrayList<>();
        for (var content : contents) {
            for (var delivery : content.deliveries()) {
                stacks.addAll(Inventories.split(delivery.stack(), delivery.amount()));
            }
        }
        return stacks;
    }

    public Map<UUID, Double> payments(PaymentType paymentType) {
        Map<UUID, Double> payments = new HashMap<>();
        switch (paymentType) {
            case STACK -> {
                for (var content : contents) {
                    for (var entry : content.payments().entrySet()) {
                        payments.compute(
                                entry.getKey(), (id, v) -> v == null ? entry.getValue() : v + entry.getValue());
                    }
                }
            }
            case TOTAL -> {
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
            }
            default -> throw new IllegalStateException("Unexpected value: " + paymentType);
        }
        return payments;
    }
}
