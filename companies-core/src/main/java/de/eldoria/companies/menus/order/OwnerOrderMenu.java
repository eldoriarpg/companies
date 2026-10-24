/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.order;

import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.menus.Menu;
import de.eldoria.companies.menus.MenuActions;
import de.eldoria.companies.menus.MenuComponents;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.orders.PriceLines;
import de.eldoria.eldoutilities.messages.MessageSender;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * One order as its owner sees it: progress per line, the price with fee and total, when it is refunded or due, and the
 * owner's actions.
 * <p>
 * The company working on the order is never shown; the model does not even hold it. In chat the actions are click
 * callbacks, except raising the price, which suggests the typed {@code /order raise} command. As dialog, every line
 * shows its item, and the price increase is entered in the dialog. A copy of a repeating order says so and links to
 * the repeating order.
 */
@SuppressWarnings("UnstableApiUsage")
public final class OwnerOrderMenu implements Menu<OwnerOrderMenu.Model> {
    /**
     * Key of the input holding the price increase.
     */
    static final String RAISE = "raise_amount";

    private final OwnerOrderActions actions;
    private final MessageSender messages;
    private final Economy economy;

    /**
     * @param actions  what the buttons do
     * @param messages renders the dialog texts
     * @param economy  economy formatting the amounts
     */
    public OwnerOrderMenu(OwnerOrderActions actions, MessageSender messages, Economy economy) {
        this.actions = actions;
        this.messages = messages;
        this.economy = economy;
    }

    @Override
    public MenuText chat(Player player, Model model, MenuActions buttons) {
        var text = OrderTexts.title("", model.id(), model.name())
                .newLine()
                .append(OrderTexts.status(model.state()))
                .newLine();
        for (var line : model.lines()) {
            text.append(lineText(line)).newLine();
        }
        text.append(priceText(model)).newLine();
        var id = model.id();
        model.series().ifPresent(series -> text.text(
                        "<neutral>⟳ <i18n:order.info.copy> <act_series><modify>[<i18n:order.info.series>]</act_series>",
                        buttons.chatAction("act_series", p -> actions.series(p, series)))
                .newLine());
        switch (model.state()) {
            case UNCLAIMED -> {
                text.append(OrderTexts.timeLeft("order.info.refundIn", model.timeLeft()))
                        .newLine()
                        .text(
                                "<act_cancel><remove>[<i18n:words.cancel>]</act_cancel>",
                                buttons.chatActionOnce("act_cancel", p -> actions.cancel(p, id)));
                if (model.extensionsLeft() > 0) {
                    text.space()
                            .text(
                                    "<act_extend><modify>[",
                                    buttons.chatActionOnce("act_extend", p -> actions.extend(p, id)))
                            .locale("order.info.extend", Values.number("left", model.extensionsLeft()))
                            .text("]</act_extend>");
                }
                text.space()
                        .text("<click:suggest_command:/order raise " + id
                                + " ><modify>[<i18n:order.info.raise>]</click>");
            }
            case CLAIMED -> text.append(OrderTexts.timeLeft("order.info.dueIn", model.timeLeft()));
            case DELIVERED ->
                text.text(
                        "<act_receive><add>[<i18n:words.receive>]</act_receive>",
                        buttons.chatActionOnce("act_receive", p -> actions.receive(p, id)));
            case RECEIVED -> {}
        }
        return text;
    }

    @Override
    public Optional<Dialog> dialog(Player player, Model model, MenuActions buttons) {
        var components = new MenuComponents(messages, player);
        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(components.of(OrderTexts.status(model.state()))));
        for (var line : model.lines()) {
            body.add(DialogBody.item(line.template())
                    .description(DialogBody.plainMessage(components.of(lineText(line))))
                    .build());
        }
        var summary = priceText(model);
        switch (model.state()) {
            case UNCLAIMED -> summary.newLine().append(OrderTexts.timeLeft("order.info.refundIn", model.timeLeft()));
            case CLAIMED -> summary.newLine().append(OrderTexts.timeLeft("order.info.dueIn", model.timeLeft()));
            case DELIVERED, RECEIVED -> {}
        }
        if (model.series().isPresent()) summary.newLine().text("<neutral>⟳ <i18n:order.info.copy>");
        body.add(DialogBody.plainMessage(components.of(summary)));

        List<DialogInput> inputs = new ArrayList<>();
        List<ActionButton> list = new ArrayList<>();
        var id = model.id();
        switch (model.state()) {
            case UNCLAIMED -> {
                inputs.add(DialogInput.text(RAISE, components.locale("order.info.raiseAmount"))
                        .build());
                list.add(buttons.button(
                        components.locale("order.info.raise"),
                        null,
                        (p, response) -> actions.raise(p, id, response.getText(RAISE))));
                if (model.extensionsLeft() > 0) {
                    list.add(buttons.buttonOnce(
                            components.locale("order.info.extend", Values.number("left", model.extensionsLeft())),
                            null,
                            (p, response) -> actions.extend(p, id)));
                }
                list.add(buttons.buttonOnce(
                        components.locale("words.cancel"), null, (p, response) -> actions.cancel(p, id)));
            }
            case DELIVERED ->
                list.add(buttons.buttonOnce(
                        components.locale("words.receive"), null, (p, response) -> actions.receive(p, id)));
            case CLAIMED, RECEIVED -> {}
        }
        model.series()
                .ifPresent(series -> list.add(buttons.button(
                        components.locale("order.info.series"), null, (p, response) -> actions.series(p, series))));
        list.add(buttons.button(components.locale("words.back"), null, (p, response) -> actions.list(p)));

        var base = DialogBase.builder(components.locale("order.info.title", Values.text("order_name", model.name())))
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(body)
                .inputs(inputs)
                .build();
        var type = DialogType.multiAction(list).columns(1).build();
        return Optional.of(Dialog.create(factory -> factory.empty().base(base).type(type)));
    }

    /**
     * Reads a price increase as entered in the dialog or typed.
     *
     * @param entered text entered, with a dot or a comma as decimal separator
     * @return the increase, or empty unless it is a finite number above zero
     */
    public static Optional<Double> raiseAmount(@Nullable String entered) {
        return DraftInput.unitPrice(entered).filter(amount -> amount > 0);
    }

    private MenuText lineText(OrderLineView line) {
        return OrderTexts.progress("", line, economy).space().append(OrderTexts.progressBar(line));
    }

    private MenuText priceText(Model model) {
        return PriceLines.fee(OrderTexts.price("", model.price(), economy), economy, model.price(), model.fee());
    }

    /**
     * What the owner sees of an order. It holds no company and no worker.
     *
     * @param id             id of the order
     * @param name           name of the order
     * @param state          state of the order
     * @param lines          lines with their progress
     * @param price          price of the order
     * @param fee            fee the owner paid
     * @param timeLeft       until an unclaimed order is refunded or a claimed one is due; zero otherwise
     * @param extensionsLeft how often the refund timer can still be restarted
     * @param series         the repeating order this order is a copy of, empty for a one-off order
     */
    public record Model(
            int id,
            String name,
            OrderState state,
            List<OrderLineView> lines,
            double price,
            double fee,
            Duration timeLeft,
            int extensionsLeft,
            OptionalInt series) {
        /**
         * @param order         order to show
         * @param configuration configuration providing the time limits
         * @return the model
         */
        public static Model of(FullOrder order, Configuration configuration) {
            var settings = configuration.orderSetting();
            return of(
                    order,
                    settings.maxUnclaimedHours(),
                    order.deliveryHours(configuration),
                    settings.maxExtensions(),
                    LocalDateTime.now());
        }

        /**
         * An unclaimed order is refunded {@code refundHours} after its last update, a claimed one is due
         * {@code deliveryHours} after it was claimed.
         *
         * @param order         order to show
         * @param refundHours   hours until an unclaimed order is refunded
         * @param deliveryHours hours the company has to deliver
         * @param maxExtensions extensions an order may have
         * @param now           current time
         * @return the model
         */
        public static Model of(
                FullOrder order, int refundHours, int deliveryHours, int maxExtensions, LocalDateTime now) {
            var timeLeft =
                    switch (order.state()) {
                        case UNCLAIMED -> Duration.between(now, order.claimed().plusHours(refundHours));
                        case CLAIMED -> Duration.between(now, order.claimed().plusHours(deliveryHours));
                        case DELIVERED, RECEIVED -> Duration.ZERO;
                    };
            return new Model(
                    order.id(),
                    order.name(),
                    order.state(),
                    OrderLineView.of(order),
                    order.price(),
                    order.fee(),
                    timeLeft,
                    order.extensionsLeft(maxExtensions),
                    order.seriesId());
        }
    }
}
