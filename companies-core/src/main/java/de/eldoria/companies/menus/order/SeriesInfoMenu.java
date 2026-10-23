/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.order;

import de.eldoria.companies.data.wrapper.series.OrderSeries;
import de.eldoria.companies.data.wrapper.series.SeriesLine;
import de.eldoria.companies.data.wrapper.series.SeriesState;
import de.eldoria.companies.menus.Keys;
import de.eldoria.companies.menus.Menu;
import de.eldoria.companies.menus.MenuActions;
import de.eldoria.companies.menus.MenuComponents;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.orders.OrderBuilder;
import de.eldoria.companies.orders.PriceLines;
import de.eldoria.eldoutilities.messages.MessageSender;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.DoubleUnaryOperator;

/**
 * One repeating order as its owner sees it: the lines of every copy, what each copy charges with the current fee, how
 * it repeats, its state, and the current copy. An active series can be stopped, a stopped one resumed.
 */
@SuppressWarnings("UnstableApiUsage")
public final class SeriesInfoMenu implements Menu<SeriesInfoMenu.Model> {
    private final SeriesActions actions;
    private final MessageSender messages;
    private final Economy economy;

    /**
     * @param actions  what the buttons do
     * @param messages renders the dialog texts
     * @param economy  economy formatting the amounts
     */
    public SeriesInfoMenu(SeriesActions actions, MessageSender messages, Economy economy) {
        this.actions = actions;
        this.messages = messages;
        this.economy = economy;
    }

    @Override
    public MenuText chat(Player player, Model model, MenuActions buttons) {
        var series = model.series();
        var id = series.id();
        var text = SeriesTexts.title("", series).newLine();
        var lines = series.lines();
        for (var i = 0; i < lines.size(); i++) {
            text.append(lineText(i, lines.get(i))).newLine();
        }
        text.append(priceText(model))
                .newLine()
                .append(SeriesTexts.details(series, model.now()))
                .newLine();
        series.currentOrder()
                .ifPresent(order -> text.text(
                        "<act_order><add>[<i18n:order.series.currentCopy>]</act_order> ",
                        buttons.chatAction("act_order", p -> actions.order(p, order))));
        if (series.state() == SeriesState.ACTIVE) {
            text.text(
                    "<act_stop><remove>[<i18n:order.series.stop>]</act_stop> ",
                    buttons.chatActionOnce("act_stop", p -> actions.stop(p, id)));
        }
        if (series.state() == SeriesState.STOPPED) {
            text.text(
                    "<act_resume><add>[<i18n:order.series.resume>]</act_resume> ",
                    buttons.chatActionOnce("act_resume", p -> actions.resume(p, id)));
        }
        return text.text(
                "<act_back><modify>[<i18n:words.back>]</act_back>", buttons.chatAction("act_back", actions::list));
    }

    @Override
    public Optional<Dialog> dialog(Player player, Model model, MenuActions buttons) {
        var components = new MenuComponents(messages, player);
        var series = model.series();
        var id = series.id();
        List<DialogBody> body = new ArrayList<>();
        var lines = series.lines();
        for (var i = 0; i < lines.size(); i++) {
            body.add(DialogBody.item(lines.get(i).template())
                    .description(DialogBody.plainMessage(components.of(lineText(i, lines.get(i)))))
                    .build());
        }
        body.add(DialogBody.plainMessage(components.of(priceText(model))));
        body.add(DialogBody.plainMessage(components.of(SeriesTexts.details(series, model.now()))));

        List<ActionButton> list = new ArrayList<>();
        series.currentOrder()
                .ifPresent(order -> list.add(buttons.button(
                        components.locale("order.series.currentCopy"),
                        null,
                        (p, response) -> actions.order(p, order))));
        if (series.state() == SeriesState.ACTIVE) {
            list.add(buttons.buttonOnce(
                    components.locale("order.series.stop"), null, (p, response) -> actions.stop(p, id)));
        }
        if (series.state() == SeriesState.STOPPED) {
            list.add(buttons.buttonOnce(
                    components.locale("order.series.resume"), null, (p, response) -> actions.resume(p, id)));
        }
        list.add(buttons.button(components.locale("words.back"), null, (p, response) -> actions.list(p)));

        var base = DialogBase.builder(components.of(SeriesTexts.title("", series)))
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(body)
                .build();
        var type = DialogType.multiAction(list).columns(1).build();
        return Optional.of(Dialog.create(factory -> factory.empty().base(base).type(type)));
    }

    private MenuText lineText(int index, SeriesLine line) {
        var item = Keys.of("line", index, "item");
        var amount = Keys.of("line", index, "amount");
        var total = Keys.of("line", index, "total");
        var draftLine = line.toDraftLine();
        return MenuText.create()
                .text(
                        "<value><" + item + "> <default>× <value><" + amount + "> <default>= <value><" + total + ">",
                        Values.component(item, draftLine.itemName()),
                        Values.number(amount, line.amount()),
                        Values.money(total, economy, draftLine.total()));
    }

    private MenuText priceText(Model model) {
        return PriceLines.fee(OrderTexts.price("", model.price(), economy), economy, model.price(), model.fee());
    }

    /**
     * What the owner sees of a repeating order.
     *
     * @param series the series
     * @param price  price of each copy
     * @param fee    fee of each copy under the current settings
     * @param now    current time
     */
    public record Model(OrderSeries series, double price, double fee, LocalDateTime now) {
        /**
         * @param series the series
         * @param fee    fee of a copy, by its price
         * @param now    current time
         * @return the model
         */
        public static Model of(OrderSeries series, DoubleUnaryOperator fee, LocalDateTime now) {
            var price = series.lines().stream()
                    .map(line -> BigDecimal.valueOf(OrderBuilder.total(line.unitPrice(), line.amount())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .doubleValue();
            return new Model(series, price, fee.applyAsDouble(price), now);
        }
    }
}
