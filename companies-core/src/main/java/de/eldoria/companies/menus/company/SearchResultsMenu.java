/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.company;

import de.eldoria.companies.commands.company.order.search.OrderSearch;
import de.eldoria.companies.commands.company.order.search.SearchResults;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.menus.Keys;
import de.eldoria.companies.menus.Menu;
import de.eldoria.companies.menus.MenuActions;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Page;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.orders.Payout;
import de.eldoria.companies.util.Texts;
import de.eldoria.eldoutilities.messages.MessageSender;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A page of the orders a company order search found.
 * <p>
 * Each order shows its contents and price when hovered, with buttons for its info and, for members who may manage the
 * company's orders, for accepting it. Paging runs the search again. As a dialog every order is a button showing its
 * info, next to its {@code [Accept]} button.
 */
public final class SearchResultsMenu implements Menu<SearchResultsMenu.Model> {
    private final OrderSearch search;
    private final MessageSender messages;
    private final Economy economy;

    /**
     * @param search   the order search the buttons act on
     * @param messages renders the dialog texts and the order details
     * @param economy  economy formatting the prices
     */
    public SearchResultsMenu(OrderSearch search, MessageSender messages, Economy economy) {
        this.search = search;
        this.messages = messages;
        this.economy = economy;
    }

    @Override
    public MenuText chat(Player player, Model model, MenuActions actions) {
        var texts = new SearchTexts(messages, player);
        var results = model.results();
        var page = model.page();
        var text = MenuText.create()
                .text(
                        "<heading><i18n:words.results>: <value><result_count> ",
                        Values.number("result_count", results.orders().size()))
                .text(
                        "<act_change><modify>[<i18n:words.change>]</act_change>",
                        actions.chatAction("act_change", search::showForm))
                .newLine();
        var orders = page.items();
        for (var i = 0; i < orders.size(); i++) {
            text.append(orderLine(i, orders.get(i), texts, results, actions)).newLine();
        }
        if (results.limitReached()) {
            text.text("<inactive><i18n:" + search.mode().limitReachedKey() + ">")
                    .newLine();
        }
        if (model.workAlone()) {
            text.text(
                            "<act_work_alone><show>[<i18n:solo.search.workAlone>]</act_work_alone>",
                            actions.chatAction("act_work_alone", search::searchSolo))
                    .newLine();
        }
        return text.append(pageLine(page, actions));
    }

    @Override
    public Optional<Dialog> dialog(Player player, Model model, MenuActions actions) {
        var texts = new SearchTexts(messages, player);
        var results = model.results();
        var page = model.page();
        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(texts.of(MenuText.create()
                .text(
                        "<heading><i18n:words.results>: <value><result_count>",
                        Values.number("result_count", results.orders().size()))
                .text(" <heading>(<i18n:words.page> <page_number>/<page_count>)", pageValues(page)))));
        if (results.limitReached()) {
            body.add(DialogBody.plainMessage(texts.of(
                    MenuText.create().text("<inactive><i18n:" + search.mode().limitReachedKey() + ">"))));
        }
        List<ActionButton> buttons = new ArrayList<>();
        for (var order : page.items()) {
            var id = order.id();
            var label = texts.of(MenuText.create()
                    .text(
                            "<name><order_id> | <value><order_name>",
                            Values.number("order_id", id),
                            Values.text("order_name", order.name())));
            buttons.add(actions.button(label, texts.of(details(order, results.taxPercent())), (clicker, response) -> {
                clicker.closeDialog();
                search.info(clicker, id);
            }));
            if (results.canAccept()) {
                buttons.add(actions.buttonOnce(texts.locale("words.accept"), null, (clicker, response) -> {
                    clicker.closeDialog();
                    search.accept(clicker, id);
                }));
            }
        }
        if (page.hasPrevious()) {
            buttons.add(actions.button(
                    Component.text(Texts.LEFT_ARROW),
                    null,
                    (clicker, response) -> search.showPage(clicker, page.number() - 1)));
        }
        if (page.hasNext()) {
            buttons.add(actions.button(
                    Component.text(Texts.RIGHT_ARROW),
                    null,
                    (clicker, response) -> search.showPage(clicker, page.number() + 1)));
        }
        buttons.add(
                actions.button(texts.locale("words.change"), null, (clicker, response) -> search.showForm(clicker)));
        if (model.workAlone()) {
            buttons.add(actions.button(texts.locale("solo.search.workAlone"), null, (clicker, response) -> {
                clicker.closeDialog();
                search.searchSolo(clicker);
            }));
        }
        var base = DialogBase.builder(texts.locale("company.order.search.dialog.results"))
                .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                .body(body)
                .build();
        var type = DialogType.multiAction(buttons)
                .columns(results.canAccept() ? 2 : 1)
                .build();
        return Optional.of(Dialog.create(factory -> factory.empty().base(base).type(type)));
    }

    private MenuText orderLine(
            int index, FullOrder order, SearchTexts texts, SearchResults results, MenuActions actions) {
        var id = order.id();
        var detailsKey = Keys.of("order", index, "details");
        var idKey = Keys.of("order", index, "id");
        var nameKey = Keys.of("order", index, "name");
        var infoKey = Keys.of("act_info", index);
        var text = MenuText.create()
                .text(
                        "<hover:show_text:'<" + detailsKey + ">'><name><" + idKey + "> | <value><" + nameKey
                                + "></hover> ",
                        Values.component(detailsKey, texts.of(details(order, results.taxPercent()))),
                        Values.number(idKey, id),
                        Values.text(nameKey, order.name()))
                .text(
                        "<" + infoKey + "><show>[<i18n:words.info>]</" + infoKey + ">",
                        actions.chatAction(infoKey, p -> search.info(p, id)));
        if (results.canAccept()) {
            var acceptKey = Keys.of("act_accept", index);
            text.space()
                    .text(
                            "<" + acceptKey + "><add>[<i18n:words.accept>]</" + acceptKey + ">",
                            actions.chatActionOnce(acceptKey, p -> search.accept(p, id)));
        }
        return text;
    }

    /**
     * The contents and the price of an order, with the tax and the net payout when the company pays tax.
     */
    private MenuText details(FullOrder order, double taxPercent) {
        var text = MenuText.create();
        var contents = order.contents();
        for (var i = 0; i < contents.size(); i++) {
            var content = contents.get(i);
            var item = Keys.of("line", i, "item");
            var amount = Keys.of("line", i, "amount");
            var price = Keys.of("line", i, "price");
            text.text(
                            "<yellow><" + item + "> <blue><" + amount + ">x <gold><" + price + ">",
                            Values.component(item, content.stack().effectiveName()),
                            Values.number(amount, content.amount()),
                            Values.money(price, economy, content.price()))
                    .newLine();
        }
        text.text(
                "<name><i18n:words.price>: <value><order_price>", Values.money("order_price", economy, order.price()));
        var payout = Payout.split(order.price(), taxPercent);
        if (payout.tax() > 0) {
            text.newLine()
                    .text(
                            "<name><i18n:words.tax>: <value><order_tax> (<tax_percent> %)",
                            Values.money("order_tax", economy, payout.tax()),
                            Values.text("tax_percent", Texts.percent(taxPercent)))
                    .newLine()
                    .text(
                            "<name><i18n:words.net>: <value><order_net>",
                            Values.money("order_net", economy, payout.net()));
        }
        return text;
    }

    private MenuText pageLine(Page<FullOrder> page, MenuActions actions) {
        var text = MenuText.create();
        if (page.hasPrevious()) {
            text.text(
                    "<act_previous> <active>" + Texts.LEFT_ARROW + " </act_previous>",
                    actions.chatAction("act_previous", p -> search.showPage(p, page.number() - 1)));
        } else {
            text.text(" <inactive>" + Texts.LEFT_ARROW + " ");
        }
        text.text("<heading><page_number>/<page_count>", pageValues(page));
        if (page.hasNext()) {
            text.text(
                    "<act_next> <active>" + Texts.RIGHT_ARROW + " </act_next>",
                    actions.chatAction("act_next", p -> search.showPage(p, page.number() + 1)));
        } else {
            text.text("<inactive> " + Texts.RIGHT_ARROW + " ");
        }
        return text;
    }

    private static TagResolver.Single[] pageValues(Page<?> page) {
        return new TagResolver.Single[] {
            Values.number("page_number", page.number() + 1), Values.number("page_count", page.count())
        };
    }

    /**
     * What a page of search results shows.
     *
     * @param results   everything the search found
     * @param page      the orders on this page
     * @param workAlone whether to offer looking for orders to work on alone
     */
    public record Model(SearchResults results, Page<FullOrder> page, boolean workAlone) {
        /**
         * A page without the offer to work alone.
         *
         * @param results everything the search found
         * @param page    the orders on this page
         */
        public Model(SearchResults results, Page<FullOrder> page) {
            this(results, page, false);
        }
    }
}
