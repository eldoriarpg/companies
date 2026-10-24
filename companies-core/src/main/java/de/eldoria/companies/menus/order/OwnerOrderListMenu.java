/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.order;

import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.menus.Keys;
import de.eldoria.companies.menus.Menu;
import de.eldoria.companies.menus.MenuActions;
import de.eldoria.companies.menus.MenuComponents;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.orders.OrderDraftService;
import de.eldoria.companies.orders.OrderLimits;
import de.eldoria.eldoutilities.messages.MessageSender;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The orders of a player which are not received yet, with the order limit and the items waiting to be collected.
 * <p>
 * Every order shows its contents, status and price when hovered and has an info button. Copies of repeating orders
 * are marked with ⟳. In chat a new order is
 * started by the suggested typed {@code /order create} command; as dialog its name is entered in the dialog.
 */
@SuppressWarnings("UnstableApiUsage")
public final class OwnerOrderListMenu implements Menu<OwnerOrderListMenu.Model> {
    /**
     * Key of the input holding the name of a new order.
     */
    static final String ORDER_NAME = "order_name";

    /**
     * Marks a copy of a repeating order.
     */
    static final String REPEATING = " <neutral>⟳";

    private final OwnerOrderActions actions;
    private final MessageSender messages;
    private final Economy economy;

    /**
     * @param actions  what the buttons do
     * @param messages renders the hover texts and the dialog texts
     * @param economy  economy formatting the prices
     */
    public OwnerOrderListMenu(OwnerOrderActions actions, MessageSender messages, Economy economy) {
        this.actions = actions;
        this.messages = messages;
        this.economy = economy;
    }

    @Override
    public MenuText chat(Player player, Model model, MenuActions buttons) {
        var components = new MenuComponents(messages, player);
        var text = MenuText.create().append(heading(model)).text(":").newLine();
        if (model.limitReached()) {
            text.text("<inactive><i18n:order.list.limitReached>");
        } else {
            text.text("<click:suggest_command:/order create ><add>[<i18n:order.list.newOrder>]</click>");
        }
        if (model.waiting() > 0) {
            text.newLine()
                    .append(waiting(model))
                    .space()
                    .text(
                            "<act_collect><add>[<i18n:words.collect>]</act_collect>",
                            buttons.chatActionOnce("act_collect", actions::collect));
        }
        var orders = model.orders();
        for (var i = 0; i < orders.size(); i++) {
            var order = orders.get(i);
            var prefix = Keys.of("order", i);
            var details = Keys.of(prefix, "details");
            var info = Keys.of("act_info", i);
            var id = order.id();
            text.newLine()
                    .text(
                            "<hover:show_text:'<" + details + ">'>",
                            Values.component(details, components.of(details(order))))
                    .append(OrderTexts.title(prefix, id, order.name()))
                    .text(model.copies().contains(id) ? REPEATING : "")
                    .text("</hover> ")
                    .text(
                            "<" + info + "><add>[<i18n:words.info>]</" + info + ">",
                            buttons.chatAction(info, p -> actions.info(p, id)));
        }
        return text;
    }

    @Override
    public Optional<Dialog> dialog(Player player, Model model, MenuActions buttons) {
        var components = new MenuComponents(messages, player);
        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(components.of(heading(model))));
        if (model.limitReached()) {
            body.add(DialogBody.plainMessage(components.locale("order.list.limitReached")));
        }
        if (model.waiting() > 0) {
            body.add(DialogBody.plainMessage(components.of(waiting(model))));
        }
        List<ActionButton> list = new ArrayList<>();
        for (var order : model.orders()) {
            var id = order.id();
            list.add(buttons.button(
                    components.of(OrderTexts.title("", id, order.name())
                            .text(model.copies().contains(id) ? REPEATING : "")),
                    components.of(details(order)),
                    (p, response) -> actions.info(p, id)));
        }
        if (model.waiting() > 0) {
            list.add(buttons.buttonOnce(components.locale("words.collect"), null, (p, response) -> actions.collect(p)));
        }
        List<DialogInput> inputs = new ArrayList<>();
        if (!model.limitReached()) {
            inputs.add(DialogInput.text(ORDER_NAME, components.locale("words.name"))
                    .maxLength(OrderDraftService.MAX_NAME_LENGTH - 1)
                    .build());
            list.add(buttons.button(
                    components.locale("order.list.newOrder"),
                    null,
                    (p, response) -> actions.create(p, response.getText(ORDER_NAME))));
        }
        var base = DialogBase.builder(components.locale("order.list.orders"))
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(body)
                .inputs(inputs)
                .build();
        var type = DialogType.multiAction(list).columns(1).build();
        return Optional.of(Dialog.create(factory -> factory.empty().base(base).type(type)));
    }

    private static MenuText heading(Model model) {
        return MenuText.create()
                .text(
                        "<i18n:order.list.orders> (<active_orders>/<order_limit>)",
                        Values.number("active_orders", model.active()),
                        Values.number("order_limit", model.limit()));
    }

    private static MenuText waiting(Model model) {
        return MenuText.create()
                .text("<neutral>")
                .locale("order.list.waiting", Values.number("amount", model.waiting()));
    }

    /**
     * The contents, status and price of an order, shown when it is hovered.
     */
    private MenuText details(OrderSummary order) {
        var text = MenuText.create();
        for (var line : order.lines()) {
            text.append(OrderTexts.progress("", line, economy)).newLine();
        }
        return text.append(OrderTexts.status(order.state()))
                .newLine()
                .append(OrderTexts.price("", order.price(), economy));
    }

    /**
     * The orders of a player as the list shows them.
     *
     * @param orders  orders not received yet
     * @param active  orders counting towards the limit
     * @param limit   orders the player may have
     * @param waiting items of received orders waiting to be collected
     * @param copies  ids of the orders that are copies of a repeating order
     */
    public record Model(List<OrderSummary> orders, int active, int limit, int waiting, Set<Integer> copies) {
        /**
         * @param orders  orders not received yet
         * @param active  orders counting towards the limit
         * @param limit   orders the player may have
         * @param waiting items of received orders waiting to be collected
         * @param copies  ids of the copies of repeating orders
         */
        public Model {
            orders = List.copyOf(orders);
            copies = Set.copyOf(copies);
        }

        /**
         * @param orders  orders not received yet
         * @param usage   use of the order limit
         * @param waiting items waiting to be collected
         * @return the model
         */
        public static Model of(List<FullOrder> orders, OrderLimits.Usage usage, int waiting) {
            var copies = orders.stream()
                    .filter(order -> order.seriesId().isPresent())
                    .map(FullOrder::id)
                    .collect(Collectors.toSet());
            return new Model(
                    orders.stream().map(OrderSummary::of).toList(), usage.active(), usage.limit(), waiting, copies);
        }

        /**
         * @return whether the player may not start another order
         */
        public boolean limitReached() {
            return new OrderLimits.Usage(active, limit).reached();
        }
    }
}
