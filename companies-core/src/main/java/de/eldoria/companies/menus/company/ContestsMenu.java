/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.company;

import de.eldoria.companies.data.wrapper.offer.EventOffer;
import de.eldoria.companies.menus.Keys;
import de.eldoria.companies.menus.Menu;
import de.eldoria.companies.menus.MenuActions;
import de.eldoria.companies.menus.MenuComponents;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.eldoutilities.messages.MessageSender;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.DoubleFunction;

/**
 * The contests that have not closed: per contest its name, what it asks for, how much is delivered, its price per item
 * and its time, with a button to show it and, while the player's company does not take part and the contest started,
 * to join it.
 * <p>
 * In chat each contest is one line with its buttons; as a dialog the list is the body and every action is a button.
 */
public final class ContestsMenu implements Menu<ContestsMenu.Model> {
    private static final String PREFIX = "company.contest.";

    private final MessageSender messages;
    private final DoubleFunction<String> money;
    private final Handlers handlers;

    /**
     * @param messages renders the rows and the dialog texts
     * @param money    formats prices
     * @param handlers what the buttons do
     */
    public ContestsMenu(MessageSender messages, DoubleFunction<String> money, Handlers handlers) {
        this.messages = messages;
        this.money = money;
        this.handlers = handlers;
    }

    @Override
    public MenuText chat(Player player, Model model, MenuActions actions) {
        var components = new MenuComponents(messages, player);
        var text = MenuText.create().text("<heading>").locale(PREFIX + "title");
        for (var index = 0; index < model.contests().size(); index++) {
            var contest = model.contests().get(index);
            var row = Keys.of("row", index);
            var info = Keys.of("act_info", index);
            text.newLine()
                    .text("<" + row + ">", Values.component(row, components.of(row(contest, model.now()))))
                    .text(
                            " <" + info + "><show>[<i18n:words.info>]</" + info + ">",
                            actions.chatAction(info, clicker -> handlers.info(clicker, contest)));
            if (joinable(model, contest)) {
                var join = Keys.of("act_join", index);
                text.text(
                        " <" + join + "><add>[<i18n:" + PREFIX + "join>]</" + join + ">",
                        actions.chatAction(join, clicker -> handlers.join(clicker, contest)));
            }
        }
        return text;
    }

    @Override
    public Optional<Dialog> dialog(Player player, Model model, MenuActions actions) {
        var components = new MenuComponents(messages, player);
        List<DialogBody> body = new ArrayList<>();
        List<ActionButton> buttons = new ArrayList<>();
        for (var contest : model.contests()) {
            body.add(DialogBody.plainMessage(components.of(row(contest, model.now()))));
            buttons.add(actions.button(
                    label(components, "<show><i18n:words.info>", contest),
                    null,
                    (clicker, response) -> handlers.info(clicker, contest)));
            if (joinable(model, contest)) {
                buttons.add(actions.button(
                        label(components, "<add><i18n:" + PREFIX + "join>", contest),
                        null,
                        (clicker, response) -> handlers.join(clicker, contest)));
            }
        }
        var base = DialogBase.builder(components.locale(PREFIX + "title"))
                .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                .body(body)
                .build();
        var type = DialogType.multiAction(buttons).columns(1).build();
        return Optional.of(Dialog.create(factory -> factory.empty().base(base).type(type)));
    }

    private static boolean joinable(Model model, EventOffer contest) {
        return model.mayManage() && !model.joined().contains(contest.id()) && contest.started() != null;
    }

    private MenuText row(EventOffer contest, LocalDateTime now) {
        return MenuText.create()
                .locale(
                        PREFIX + "entry",
                        Values.number("id", contest.id()),
                        Values.text("offer_name", contest.name()),
                        Values.text("item", contest.line().label()),
                        Values.number("delivered", contest.delivered()),
                        Values.number("amount", contest.amount()),
                        Values.text("unit", money.apply(contest.unitPrice())))
                .space()
                .append(ContestMenu.time(contest, now));
    }

    private static Component label(MenuComponents components, String action, EventOffer contest) {
        return components.of(
                MenuText.create().text(action + " <value><offer_name>", Values.text("offer_name", contest.name())));
    }

    /**
     * The contests shown.
     *
     * @param contests the contests that have not closed
     * @param joined   the ids of those the player's company takes part in
     * @param mayManage whether the player may let the company join, which needs a company and {@code manage_orders}
     * @param now       the current time in UTC
     */
    public record Model(List<EventOffer> contests, Set<Integer> joined, boolean mayManage, LocalDateTime now) {}

    /**
     * What the buttons of the list do.
     */
    public interface Handlers {
        /**
         * @param player  player who clicked
         * @param contest the contest to show
         */
        void info(Player player, EventOffer contest);

        /**
         * @param player  player who clicked
         * @param contest the contest the company joins
         */
        void join(Player player, EventOffer contest);
    }
}
