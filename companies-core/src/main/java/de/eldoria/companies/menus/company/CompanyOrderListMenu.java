/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.company;

import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.menus.Keys;
import de.eldoria.companies.menus.Menu;
import de.eldoria.companies.menus.MenuActions;
import de.eldoria.companies.menus.MenuComponents;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.menus.order.OrderSummary;
import de.eldoria.companies.menus.order.OrderTexts;
import de.eldoria.companies.orders.PriceLines;
import de.eldoria.eldoutilities.messages.MessageSender;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The orders a company works on. Every order shows its progress, price and the company's tax when hovered and has an
 * info button; the search is one click away.
 */
@SuppressWarnings("UnstableApiUsage")
public final class CompanyOrderListMenu implements Menu<CompanyOrderListMenu.Model> {
    private final CompanyOrderActions actions;
    private final MessageSender messages;
    private final Economy economy;

    /**
     * @param actions  what the buttons do
     * @param messages renders the hover texts and the dialog texts
     * @param economy  economy formatting the prices
     */
    public CompanyOrderListMenu(CompanyOrderActions actions, MessageSender messages, Economy economy) {
        this.actions = actions;
        this.messages = messages;
        this.economy = economy;
    }

    @Override
    public MenuText chat(Player player, Model model, MenuActions buttons) {
        var components = new MenuComponents(messages, player);
        var text = MenuText.create()
                .text(
                        "<heading><i18n:" + actions.listTitle()
                                + ">: <act_search><show>[<i18n:words.search>]</act_search>",
                        buttons.chatAction("act_search", actions::search));
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
                            Values.component(details, components.of(details(order, model.taxPercent()))))
                    .append(OrderTexts.title(prefix, id, order.name()))
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
        List<ActionButton> list = new ArrayList<>();
        for (var order : model.orders()) {
            var id = order.id();
            list.add(buttons.button(
                    components.of(OrderTexts.title("", id, order.name())),
                    components.of(details(order, model.taxPercent())),
                    (p, response) -> actions.info(p, id)));
        }
        list.add(buttons.button(components.locale("words.search"), null, (p, response) -> actions.search(p)));
        var base = DialogBase.builder(components.locale(actions.listTitle()))
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .build();
        var type = DialogType.multiAction(list).columns(1).build();
        return Optional.of(Dialog.create(factory -> factory.empty().base(base).type(type)));
    }

    /**
     * The progress, price and tax of an order, shown when it is hovered.
     */
    private MenuText details(OrderSummary order, double taxPercent) {
        var text = MenuText.create();
        for (var line : order.lines()) {
            text.append(OrderTexts.progress("", line, economy)).newLine();
        }
        text.append(OrderTexts.price("", order.price(), economy));
        return PriceLines.tax(text, economy, order.price(), taxPercent);
    }

    /**
     * The orders of a company as the list shows them.
     *
     * @param orders     orders the company works on
     * @param taxPercent tax of the company, where 5 means 5 %
     */
    public record Model(List<OrderSummary> orders, double taxPercent) {
        /**
         * @param orders     orders the company works on
         * @param taxPercent tax of the company
         * @return the model
         */
        public static Model of(List<FullOrder> orders, double taxPercent) {
            return new Model(orders.stream().map(OrderSummary::of).toList(), taxPercent);
        }
    }
}
