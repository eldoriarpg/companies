/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.company;

import de.eldoria.companies.components.ranking.TimeRange;
import de.eldoria.companies.components.ranking.TopOrder;
import de.eldoria.companies.data.wrapper.company.CompanyRank;
import de.eldoria.companies.menus.Keys;
import de.eldoria.companies.menus.Menu;
import de.eldoria.companies.menus.MenuActions;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.util.Colors;
import de.eldoria.companies.util.ProfileColors;
import de.eldoria.companies.util.Times;
import de.eldoria.eldoutilities.messages.MessageSender;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The company ranking, one page at a time, by a chosen ordering and time range.
 * <p>
 * Every company shows its tag in its colour, its statistics in the range when hovered and opens its profile when
 * clicked. The orderings and ranges are buttons, the ones in use highlighted. Below the page the viewer's own company
 * is shown with its place, or a dash when it is not ranked in the range.
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
                            actions.chatAction(key, viewer -> navigation.show(viewer, 1, ordering, model.range())));
        }
        text.newLine().text("<name><i18n:company.top.range>:");
        for (var range : TimeRange.values()) {
            var key = Keys.of("act_range", range.id());
            text.space()
                    .text(
                            "<" + key + ">" + rangeLabel(model, range) + "</" + key + ">",
                            actions.chatAction(key, viewer -> navigation.show(viewer, 1, model.ordering(), range)));
        }
        text.newLine();
        var ranks = model.ranks();
        for (var i = 0; i < ranks.size(); i++) {
            var rank = ranks.get(i);
            var positionKey = Keys.of("rank", i, "position");
            var statsKey = Keys.of("rank", i, "stats");
            var tagKey = Keys.of("rank", i, "tag");
            var nameKey = Keys.of("rank", i, "name");
            var openKey = Keys.of("act_rank", i);
            text.text(
                            "<name><" + positionKey + "> | <" + openKey + "><hover:show_text:'<" + statsKey + ">'><"
                                    + tagKey + "><gold><" + nameKey + "></hover></" + openKey + ">",
                            Values.number(positionKey, rank.rank()),
                            Values.component(statsKey, texts.of(stats(rank))),
                            tag(tagKey, rank.tag(), rank.color()),
                            Values.text(nameKey, rank.name()),
                            actions.chatAction(openKey, viewer -> navigation.profile(viewer, rank.id())))
                    .newLine();
        }
        text.append(model.paging().chat(actions, previous(model), next(model)));
        model.own().ifPresent(own -> text.newLine().append(ownLine(own)));
        return text;
    }

    @Override
    public Optional<Dialog> dialog(Player player, TopView model, MenuActions actions) {
        var texts = new SearchTexts(messages, player);
        List<ActionButton> buttons = new ArrayList<>();
        for (var ordering : TopOrder.values()) {
            buttons.add(actions.button(
                    texts.of(MenuText.create().text(orderingLabel(model, ordering))),
                    null,
                    (clicker, response) -> navigation.show(clicker, 1, ordering, model.range())));
        }
        for (var range : TimeRange.values()) {
            buttons.add(actions.button(
                    texts.of(MenuText.create().text(rangeLabel(model, range))),
                    null,
                    (clicker, response) -> navigation.show(clicker, 1, model.ordering(), range)));
        }
        for (var rank : model.ranks()) {
            var label = MenuText.create()
                    .text(
                            "<name><rank_position> | <rank_tag><gold><rank_name>",
                            Values.number("rank_position", rank.rank()),
                            tag("rank_tag", rank.tag(), rank.color()),
                            Values.text("rank_name", rank.name()));
            buttons.add(actions.button(
                    texts.of(label),
                    texts.of(stats(rank)),
                    (clicker, response) -> navigation.profile(clicker, rank.id())));
        }
        var paging = model.paging();
        paging.addButtons(buttons, actions, previous(model), next(model));
        var body = MenuText.create()
                .text("<name><i18n:words.ordering>: <value><i18n:"
                        + model.ordering().translationKey() + ">")
                .newLine()
                .text("<name><i18n:company.top.range>: <value><i18n:"
                        + model.range().translationKey() + ">")
                .newLine()
                .text("<name><i18n:words.page>: ")
                .append(paging.label());
        model.own().ifPresent(own -> body.newLine().append(ownLine(own)));
        var base = DialogBase.builder(texts.locale("company.top.ranking"))
                .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                .body(List.of(DialogBody.plainMessage(texts.of(body))))
                .build();
        return Optional.of(Dialog.create(factory -> factory.empty()
                .base(base)
                .type(DialogType.multiAction(buttons).columns(2).build())));
    }

    /**
     * The statistics of a ranked company in the ranked time range, shown when it is hovered.
     *
     * @param rank the ranked company
     * @return the text
     */
    MenuText stats(CompanyRank rank) {
        return MenuText.create()
                .text("<heading><company_name>", Values.text("company_name", rank.name()))
                .newLine()
                .text(
                        "<name><i18n:words.founded>: <value><founded>",
                        Values.text("founded", FOUNDED.format(Times.local(rank.founded()))))
                .newLine()
                .text("<name><i18n:words.level>: <value><company_level>", Values.number("company_level", rank.level()))
                .newLine()
                .text(
                        "<name><i18n:words.member>: <value><member_count>",
                        Values.number("member_count", rank.memberCount()))
                .newLine()
                .text(
                        "<name><i18n:words.orders>: <value><order_count>",
                        Values.number("order_count", rank.orderCount()))
                .newLine()
                .text(
                        "<name><i18n:level.earnedMoney>: <value><earned_money>",
                        Values.money("earned_money", economy, rank.price()))
                .newLine()
                .text(
                        "<name><i18n:level.deliveredItems>: <value><delivered_items>",
                        Values.number("delivered_items", rank.deliveredItems()));
    }

    /**
     * The viewer's company with its place, or with a dash when it is not ranked.
     *
     * @param own the viewer's company
     * @return the line
     */
    static MenuText ownLine(TopView.OwnCompany own) {
        var place = own.rank().isPresent()
                ? MenuText.create()
                        .locale(
                                "company.top.ownRank",
                                Values.number("own_rank", own.rank().getAsInt()))
                : MenuText.create().locale("company.top.notRanked");
        return MenuText.create()
                .text("<name><i18n:company.top.yourCompany>: ")
                .append(place)
                .space()
                .text(
                        "<own_tag><gold><own_name>",
                        tag("own_tag", own.tag(), own.color()),
                        Values.text("own_name", own.name()));
    }

    /**
     * The tag of a company in its colour, followed by a space, inserted unparsed. Nothing without a tag.
     *
     * @param key   placeholder key
     * @param tag   tag of the company
     * @param color stored colour of the company
     * @return placeholder
     */
    private static TagResolver.Single tag(String key, Optional<String> tag, Optional<String> color) {
        if (tag.isEmpty()) return Values.component(key, Component.empty());
        TextColor textColor = color.flatMap(ProfileColors::resolve).orElse(null);
        return Values.component(key, Component.text("[" + tag.get() + "] ", textColor));
    }

    private Consumer<Player> previous(TopView model) {
        return viewer -> navigation.show(viewer, model.page() - 1, model.ordering(), model.range());
    }

    private Consumer<Player> next(TopView model) {
        return viewer -> navigation.show(viewer, model.page() + 1, model.ordering(), model.range());
    }

    private static String orderingLabel(TopView model, TopOrder ordering) {
        var style = Colors.active(model.ordering() == ordering);
        return "<" + style + ">[<i18n:" + ordering.translationKey() + ">]</" + style + ">";
    }

    private static String rangeLabel(TopView model, TimeRange range) {
        var style = Colors.active(model.range() == range);
        return "<" + style + ">[<i18n:" + range.translationKey() + ">]</" + style + ">";
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
         * @param range    time range of the ranked values
         */
        void show(Player viewer, int page, TopOrder ordering, TimeRange range);

        /**
         * Shows the profile of a company.
         *
         * @param viewer  player who clicked
         * @param company id of the company
         */
        void profile(Player viewer, int company);
    }
}
