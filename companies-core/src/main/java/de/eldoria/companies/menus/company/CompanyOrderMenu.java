/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.company;

import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.data.wrapper.offer.OfferMark;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.menus.Keys;
import de.eldoria.companies.menus.Menu;
import de.eldoria.companies.menus.MenuActions;
import de.eldoria.companies.menus.MenuComponents;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.menus.order.OfferTexts;
import de.eldoria.companies.menus.order.OrderLineView;
import de.eldoria.companies.menus.order.OrderTexts;
import de.eldoria.companies.orders.ItemMatcher;
import de.eldoria.companies.orders.PriceLines;
import de.eldoria.eldoutilities.messages.MessageSender;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * One order as a company member sees it: the lines with their progress, deliver buttons and the damage of delivered
 * items, the price with the company's tax, the time left and the actions the member may take. A chunk of an event
 * offer names its offer with the offer's progress and end.
 * <p>
 * The owner of the order is never shown; the model does not even hold it. In chat every line has buttons delivering
 * 1, 8, 32, 64 or the missing items, and {@code [Add]} suggests the typed deliver command for another amount. As
 * dialog every line shows its item and has one button delivering what it misses.
 */
@SuppressWarnings("UnstableApiUsage")
public final class CompanyOrderMenu implements Menu<CompanyOrderMenu.Model> {
    private static final int[] AMOUNTS = {1, 8, 32, 64};

    private final CompanyOrderActions actions;
    private final MessageSender messages;
    private final Economy economy;

    /**
     * @param actions  what the buttons do
     * @param messages renders the dialog texts
     * @param economy  economy formatting the amounts
     */
    public CompanyOrderMenu(CompanyOrderActions actions, MessageSender messages, Economy economy) {
        this.actions = actions;
        this.messages = messages;
        this.economy = economy;
    }

    @Override
    public MenuText chat(Player player, Model model, MenuActions buttons) {
        var text = OrderTexts.title("", model.id(), model.name()).newLine();
        var offer = model.offer();
        if (offer != null) text.append(OfferTexts.line(offer)).newLine();
        text.append(OrderTexts.status(model.state())).newLine();
        for (var line : model.lines()) {
            text.append(lineText(model, line));
            if (model.deliverable(line)) {
                text.space().append(deliverButtons(model.id(), line, buttons));
            }
            text.newLine();
        }
        text.append(priceText(model)).newLine();
        var id = model.id();
        switch (model.state()) {
            case UNCLAIMED -> {
                if (model.canManage()) {
                    text.text(
                            "<act_accept><add>[<i18n:words.accept>]</act_accept>",
                            buttons.chatActionOnce("act_accept", p -> actions.accept(p, id)));
                }
            }
            case CLAIMED -> {
                text.append(timeLeft(model)).newLine();
                if (!model.done()) {
                    text.text(
                                    "<act_deliver_all><add>[<i18n:company.order.info.deliverAll>]</act_deliver_all>",
                                    buttons.chatAction("act_deliver_all", p -> actions.deliverAll(p, id)))
                            .space();
                }
                if (model.canManage()) {
                    text.text(
                            "<act_abort><remove>[<i18n:words.abort>]</act_abort>",
                            buttons.chatAction("act_abort", p -> actions.abort(p, id)));
                }
            }
            case DELIVERED, RECEIVED -> {}
        }
        return text;
    }

    @Override
    public Optional<Dialog> dialog(Player player, Model model, MenuActions buttons) {
        var components = new MenuComponents(messages, player);
        List<DialogBody> body = new ArrayList<>();
        var offer = model.offer();
        if (offer != null) body.add(DialogBody.plainMessage(components.of(OfferTexts.line(offer))));
        body.add(DialogBody.plainMessage(components.of(OrderTexts.status(model.state()))));
        for (var line : model.lines()) {
            body.add(DialogBody.item(line.template())
                    .description(DialogBody.plainMessage(components.of(lineText(model, line))))
                    .build());
        }
        var summary = priceText(model);
        if (model.state() == OrderState.CLAIMED) {
            summary.newLine().append(timeLeft(model));
        }
        body.add(DialogBody.plainMessage(components.of(summary)));

        var id = model.id();
        List<ActionButton> list = new ArrayList<>();
        for (var line : model.lines()) {
            if (!model.deliverable(line)) continue;
            var number = line.number();
            list.add(buttons.button(
                    components.locale("company.order.info.deliverLine", Values.component("line_item", line.itemName())),
                    components.of(OrderTexts.progress("", line, economy)),
                    (p, response) -> actions.deliver(p, id, number, Integer.MAX_VALUE)));
        }
        switch (model.state()) {
            case UNCLAIMED -> {
                if (model.canManage()) {
                    list.add(buttons.buttonOnce(
                            components.locale("words.accept"), null, (p, response) -> actions.accept(p, id)));
                }
            }
            case CLAIMED -> {
                if (!model.done()) {
                    list.add(buttons.button(
                            components.locale("company.order.info.deliverAll"),
                            null,
                            (p, response) -> actions.deliverAll(p, id)));
                }
                if (model.canManage()) {
                    list.add(buttons.button(
                            components.locale("words.abort"), null, (p, response) -> actions.abort(p, id)));
                }
            }
            case DELIVERED, RECEIVED -> {}
        }
        list.add(buttons.button(components.locale("words.back"), null, (p, response) -> actions.list(p)));

        var base = DialogBase.builder(
                        components.locale("company.order.info.title", Values.text("order_name", model.name())))
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(body)
                .build();
        var type = DialogType.multiAction(list).columns(1).build();
        return Optional.of(Dialog.create(factory -> factory.empty().base(base).type(type)));
    }

    private MenuText lineText(Model model, OrderLineView line) {
        if (model.state() == OrderState.UNCLAIMED) return OrderTexts.requested("", line, economy);
        var text = OrderTexts.progress("", line, economy);
        if (line.missing() == 0) {
            text.space().text("<value><i18n:words.done>");
        }
        var damage = OrderTexts.damage(line);
        if (!damage.build().isEmpty()) {
            text.space().append(damage);
        }
        return text;
    }

    private MenuText deliverButtons(int id, OrderLineView line, MenuActions buttons) {
        var number = line.number();
        var text = MenuText.create().text("<add>");
        for (var amount : AMOUNTS) {
            var key = Keys.of("act_line", number, amount);
            text.text(
                    "<" + key + ">[" + amount + "]</" + key + ">",
                    buttons.chatAction(key, p -> actions.deliver(p, id, number, amount)));
        }
        var max = Keys.of("act_line", number, "max");
        return text.text(
                        "<" + max + ">[<i18n:words.max>]</" + max + ">",
                        buttons.chatAction(max, p -> actions.deliver(p, id, number, Integer.MAX_VALUE)))
                .text("<click:suggest_command:" + actions.deliverCommand() + " " + id + " " + number
                        + " >[<i18n:words.add>]</click>");
    }

    private MenuText priceText(Model model) {
        return PriceLines.tax(OrderTexts.price("", model.price(), economy), economy, model.price(), model.taxPercent());
    }

    private static MenuText timeLeft(Model model) {
        return MenuText.create()
                .text(
                        "<name><i18n:words.leftTime> <value><time_left>",
                        Values.text("time_left", OrderTexts.pretty(model.timeLeft())));
    }

    /**
     * What a member sees of an order. It holds no owner.
     *
     * @param id         id of the order
     * @param name       name of the order
     * @param state      state of the order
     * @param lines      lines with their progress and damage
     * @param price      price of the order
     * @param taxPercent tax of the member's company, where 5 means 5 %
     * @param timeLeft   until a claimed order is due; zero otherwise
     * @param canManage  whether the member may accept and abort orders
     * @param offer      the event offer the order is a chunk of, null when it belongs to none
     */
    public record Model(
            int id,
            String name,
            OrderState state,
            List<OrderLineView> lines,
            double price,
            double taxPercent,
            Duration timeLeft,
            boolean canManage,
            @Nullable OfferMark offer) {
        /**
         * An order of no event offer.
         *
         * @param id         id of the order
         * @param name       name of the order
         * @param state      state of the order
         * @param lines      lines with their progress and damage
         * @param price      price of the order
         * @param taxPercent tax of the member's company, where 5 means 5 %
         * @param timeLeft   until a claimed order is due; zero otherwise
         * @param canManage  whether the member may accept and abort orders
         */
        public Model(
                int id,
                String name,
                OrderState state,
                List<OrderLineView> lines,
                double price,
                double taxPercent,
                Duration timeLeft,
                boolean canManage) {
            this(id, name, state, lines, price, taxPercent, timeLeft, canManage, null);
        }

        /**
         * @param offer the event offer the order is a chunk of, null when it belongs to none
         * @return this model marked with the offer
         */
        public Model withOffer(@Nullable OfferMark offer) {
            return new Model(id, name, state, lines, price, taxPercent, timeLeft, canManage, offer);
        }

        /**
         * @param order         order to show
         * @param deliveryHours hours the company has to deliver once it accepted the order
         * @param taxPercent    tax of the member's company
         * @param canManage     whether the member may accept and abort orders
         * @param matcher       measures the damage of delivered items, when damaged items are accepted
         * @param now           current time
         * @return the model
         */
        public static Model of(
                FullOrder order,
                int deliveryHours,
                double taxPercent,
                boolean canManage,
                ItemMatcher matcher,
                LocalDateTime now) {
            var timeLeft = order.state() == OrderState.CLAIMED
                    ? Duration.between(now, order.claimed().plusHours(deliveryHours))
                    : Duration.ZERO;
            return new Model(
                    order.id(),
                    order.name(),
                    order.state(),
                    OrderLineView.withDamage(order, matcher),
                    order.price(),
                    taxPercent,
                    timeLeft,
                    canManage);
        }

        /**
         * @return whether every line got all its items
         */
        public boolean done() {
            return lines.stream().allMatch(line -> line.missing() == 0);
        }

        /**
         * @param line line of this order
         * @return whether items can be delivered to the line now
         */
        public boolean deliverable(OrderLineView line) {
            return state == OrderState.CLAIMED && line.missing() > 0;
        }
    }
}
