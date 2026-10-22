/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.company;

import de.eldoria.companies.components.ranking.TopOrder;
import de.eldoria.companies.data.wrapper.company.CompanyStats;
import de.eldoria.companies.menus.Keys;
import de.eldoria.companies.menus.Menu;
import de.eldoria.companies.menus.MenuActions;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.util.Colors;
import de.eldoria.eldoutilities.messages.MessageSender;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The company ranking, one page at a time, by a chosen ordering.
 * <p>
 * Every company shows its statistics when hovered and opens its profile when clicked. The orderings are buttons, the
 * one in use highlighted.
 */
public final class TopMenu implements Menu<TopView> {
    private static final DateTimeFormatter FOUNDED = DateTimeFormatter.ofPattern("dd.MM.yy");

    private final MessageSender messages;
    private final Economy economy;
    private final Navigation navigation;

    /**
     * @param messages   renders the hover and dialog texts
     * @param economy    economy formatting the earned money
     * @param navigation shows other pages and the profiles
     */
    public TopMenu(MessageSender messages, Economy economy, Navigation navigation) {
        this.messages = messages;
        this.economy = economy;
        this.navigation = navigation;
    }

    @Override
    public MenuText chat(Player player, TopView model, MenuActions actions) {
        var texts = new SearchTexts(messages, player);
        var text = MenuText.create()
                .text("<heading><i18n:company.top.ranking>")
                .newLine()
                .text("<name><i18n:words.ordering>:");
        for (var ordering : TopOrder.values()) {
            var key = Keys.of("act_ordering", ordering.name().toLowerCase(Locale.ROOT));
            text.space()
                    .text(
                            "<" + key + ">" + orderingLabel(model, ordering) + "</" + key + ">",
                            actions.chatAction(key, viewer -> navigation.show(viewer, model.page(), ordering)));
        }
        text.newLine();
        var ranks = model.ranks();
        for (var i = 0; i < ranks.size(); i++) {
            var rank = ranks.get(i);
            var positionKey = Keys.of("rank", i, "position");
            var statsKey = Keys.of("rank", i, "stats");
            var nameKey = Keys.of("rank", i, "name");
            var openKey = Keys.of("act_rank", i);
            text.text(
                            "<name><" + positionKey + "> | <" + openKey + "><hover:show_text:'<" + statsKey
                                    + ">'><gold><" + nameKey + "></hover></" + openKey + ">",
                            Values.number(positionKey, rank.rank()),
                            Values.component(statsKey, texts.of(stats(rank))),
                            Values.text(nameKey, rank.name()),
                            actions.chatAction(openKey, viewer -> navigation.profile(viewer, rank.id())))
                    .newLine();
        }
        return text.append(model.paging()
                .chat(
                        actions,
                        viewer -> navigation.show(viewer, model.page() - 1, model.ordering()),
                        viewer -> navigation.show(viewer, model.page() + 1, model.ordering())));
    }

    @Override
    public Optional<Dialog> dialog(Player player, TopView model, MenuActions actions) {
        var texts = new SearchTexts(messages, player);
        List<ActionButton> buttons = new ArrayList<>();
        for (var ordering : TopOrder.values()) {
            buttons.add(actions.button(
                    texts.of(MenuText.create().text(orderingLabel(model, ordering))),
                    null,
                    (clicker, response) -> navigation.show(clicker, model.page(), ordering)));
        }
        for (var rank : model.ranks()) {
            var label = MenuText.create()
                    .text(
                            "<name><rank_position> | <gold><rank_name>",
                            Values.number("rank_position", rank.rank()),
                            Values.text("rank_name", rank.name()));
            buttons.add(actions.button(
                    texts.of(label),
                    texts.of(stats(rank)),
                    (clicker, response) -> navigation.profile(clicker, rank.id())));
        }
        var paging = model.paging();
        paging.addButtons(
                buttons,
                actions,
                viewer -> navigation.show(viewer, model.page() - 1, model.ordering()),
                viewer -> navigation.show(viewer, model.page() + 1, model.ordering()));
        var body = MenuText.create()
                .text("<name><i18n:words.ordering>: <value><i18n:"
                        + model.ordering().translationKey() + ">")
                .newLine()
                .text("<name><i18n:words.page>: ")
                .append(paging.label());
        var base = DialogBase.builder(texts.locale("company.top.ranking"))
                .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                .body(List.of(DialogBody.plainMessage(texts.of(body))))
                .build();
        return Optional.of(Dialog.create(factory -> factory.empty()
                .base(base)
                .type(DialogType.multiAction(buttons).columns(2).build())));
    }

    /**
     * The statistics of a company, shown when it is hovered.
     *
     * @param stats statistics of the company
     * @return the text
     */
    MenuText stats(CompanyStats stats) {
        return MenuText.create()
                .text("<heading><company_name>", Values.text("company_name", stats.name()))
                .newLine()
                .text(
                        "<name><i18n:words.founded>: <value><founded>",
                        Values.text("founded", FOUNDED.format(stats.founded())))
                .newLine()
                .text(
                        "<name><i18n:words.member>: <value><member_count>",
                        Values.number("member_count", stats.memberCount()))
                .newLine()
                .text(
                        "<name><i18n:words.orders>: <value><order_count>",
                        Values.number("order_count", stats.orderCount()))
                .newLine()
                .text(
                        "<name><i18n:level.earnedMoney>: <value><earned_money>",
                        Values.money("earned_money", economy, stats.price()))
                .newLine()
                .text(
                        "<name><i18n:level.deliveredItems>: <value><delivered_items>",
                        Values.number("delivered_items", stats.deliveredItems()));
    }

    private static String orderingLabel(TopView model, TopOrder ordering) {
        var style = Colors.active(model.ordering() == ordering);
        return "<" + style + ">[<i18n:" + ordering.translationKey() + ">]</" + style + ">";
    }

    /**
     * Shows other pages of the ranking and the profiles of the ranked companies.
     */
    public interface Navigation {
        /**
         * Shows a page of the ranking.
         *
         * @param viewer   player who clicked
         * @param page     number of the page, from one
         * @param ordering what the companies are ranked by
         */
        void show(Player viewer, int page, TopOrder ordering);

        /**
         * Shows the profile of a company.
         *
         * @param viewer  player who clicked
         * @param company id of the company
         */
        void profile(Player viewer, int company);
    }
}
