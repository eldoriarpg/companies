/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.company;

import de.eldoria.companies.data.wrapper.offer.ContestEntry;
import de.eldoria.companies.data.wrapper.offer.EventOffer;
import de.eldoria.companies.data.wrapper.offer.OfferState;
import de.eldoria.companies.menus.Keys;
import de.eldoria.companies.menus.Menu;
import de.eldoria.companies.menus.MenuActions;
import de.eldoria.companies.menus.MenuComponents;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.util.Texts;
import de.eldoria.eldoutilities.messages.MessageSender;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;

/**
 * A contest of an event offer: its time, what it still wants and pays per item, its prizes, the part of the player's
 * company and the ranking of the companies that delivered.
 * <p>
 * Below it the buttons the contest and the company allow: delivering every matching item and leaving while the
 * company takes part, joining while it does not and the contest started, and going back to the contests. In chat they
 * are a line of buttons; as a dialog the contest is the body. The handlers show the contest again with what came of
 * the button on top. Leaving takes one click; the company keeps its place and may join again.
 */
public final class ContestMenu implements Menu<ContestMenu.Model> {
    private static final String PREFIX = "company.contest.";

    private final MessageSender messages;
    private final DoubleFunction<String> money;
    private final Handlers handlers;

    /**
     * @param messages renders the rows and the dialog texts
     * @param money    formats prices and prizes
     * @param handlers what the buttons do
     */
    public ContestMenu(MessageSender messages, DoubleFunction<String> money, Handlers handlers) {
        this.messages = messages;
        this.money = money;
        this.handlers = handlers;
    }

    @Override
    public MenuText chat(Player player, Model model, MenuActions actions) {
        var components = new MenuComponents(messages, player);
        var text = MenuText.create();
        var rows = rows(model);
        for (var index = 0; index < rows.size(); index++) {
            var key = Keys.of("row", index);
            if (index > 0) text.newLine();
            text.text("<" + key + ">", Values.component(key, components.of(rows.get(index))));
        }
        text.newLine();
        var buttons = buttons(model);
        for (var index = 0; index < buttons.size(); index++) {
            var button = buttons.get(index);
            var key = Keys.of("act", index);
            if (index > 0) text.space();
            text.text(
                    "<" + key + ">" + button.style() + "[<i18n:" + button.label() + ">]</" + key + ">",
                    button.once()
                            ? actions.chatActionOnce(key, button.handler())
                            : actions.chatAction(key, button.handler()));
        }
        return text;
    }

    @Override
    public Optional<Dialog> dialog(Player player, Model model, MenuActions actions) {
        var components = new MenuComponents(messages, player);
        List<DialogBody> body = new ArrayList<>();
        var rows = rows(model);
        for (var row : rows.subList(1, rows.size())) body.add(DialogBody.plainMessage(components.of(row)));
        List<ActionButton> buttons = new ArrayList<>();
        for (var button : buttons(model)) {
            var label = components.of(MenuText.create().text(button.style() + "<i18n:" + button.label() + ">"));
            buttons.add(
                    button.once()
                            ? actions.buttonOnce(label, null, (clicker, response) -> button.handler()
                                    .accept(clicker))
                            : actions.button(label, null, (clicker, response) -> button.handler()
                                    .accept(clicker)));
        }
        var base = DialogBase.builder(components.of(rows.getFirst()))
                .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                .body(body)
                .build();
        var type = DialogType.multiAction(buttons).columns(2).build();
        return Optional.of(Dialog.create(factory -> factory.empty().base(base).type(type)));
    }

    /**
     * The rows of the contest, its title first.
     */
    private List<MenuText> rows(Model model) {
        var contest = model.contest();
        List<MenuText> rows = new ArrayList<>();
        rows.add(MenuText.create()
                .text("<heading>")
                .locale(
                        PREFIX + "info.title",
                        Values.number("id", contest.id()),
                        Values.text("offer_name", contest.name()))
                .space()
                .append(time(contest, model.now())));
        model.notice().ifPresent(rows::add);
        rows.add(MenuText.create()
                .locale(
                        PREFIX + "info.wanted",
                        Values.number("missing", contest.missing()),
                        Values.number("amount", contest.amount()),
                        Values.text("item", contest.line().label()),
                        Values.text("unit", money.apply(contest.unitPrice()))));
        if (contest.prizes().isEmpty()) {
            rows.add(MenuText.create().locale(PREFIX + "info.noPrizes"));
        } else {
            var prizes = new ArrayList<String>();
            for (var index = 0; index < contest.prizes().size(); index++) {
                prizes.add((index + 1) + ". " + money.apply(contest.prizes().get(index)));
            }
            rows.add(
                    MenuText.create().locale(PREFIX + "info.prizes", Values.text("prizes", String.join(", ", prizes))));
        }
        var own = model.own();
        if (own.isPresent()) {
            rows.add(MenuText.create()
                    .locale(
                            own.get().active() ? PREFIX + "info.own" : PREFIX + "info.ownLeft",
                            Values.number("items", own.get().delivered()),
                            Values.text("paid", money.apply(own.get().paid())),
                            Values.text(
                                    "place",
                                    model.place().isPresent()
                                            ? String.valueOf(model.place().getAsInt())
                                            : "-")));
        } else {
            rows.add(MenuText.create().locale(PREFIX + "info.notJoined"));
        }
        if (model.ranking().isEmpty()) {
            rows.add(MenuText.create().locale(PREFIX + "info.noDeliveries"));
            return rows;
        }
        rows.add(MenuText.create().locale(PREFIX + "info.ranking"));
        for (var index = 0; index < model.ranking().size(); index++) {
            var entry = model.ranking().get(index);
            rows.add(MenuText.create()
                    .locale(
                            PREFIX + "info.place",
                            Values.number("place", index + 1),
                            Values.text("company_name", String.valueOf(entry.name())),
                            Values.number("items", entry.delivered())));
        }
        return rows;
    }

    /**
     * The buttons the contest and the company allow, in their order.
     */
    private List<Button> buttons(Model model) {
        var contest = model.contest();
        List<Button> buttons = new ArrayList<>();
        var joined = model.own().filter(ContestEntry::active).isPresent();
        if (!contest.state().closed()) {
            if (joined) {
                if (contest.state() == OfferState.ACTIVE) {
                    buttons.add(new Button(
                            PREFIX + "deliverAll", "<add>", false, player -> handlers.deliverAll(player, contest)));
                }
                if (model.mayManage()) {
                    buttons.add(
                            new Button(PREFIX + "leave", "<remove>", true, player -> handlers.leave(player, contest)));
                }
            } else if (contest.started() != null && model.member() && model.mayManage()) {
                buttons.add(new Button(PREFIX + "join", "<add>", false, player -> handlers.join(player, contest)));
            }
        }
        buttons.add(new Button(PREFIX + "back", "<show>", false, handlers::list));
        return buttons;
    }

    /**
     * How long a contest still runs, or that it waits, is paused or closed.
     *
     * @param contest the contest
     * @param now     the current time in UTC
     * @return the text
     */
    static MenuText time(EventOffer contest, LocalDateTime now) {
        return switch (contest.state()) {
            case SCHEDULED ->
                MenuText.create()
                        .locale(
                                PREFIX + "startsIn",
                                Values.text("time", pretty(Duration.between(now, contest.starts()))));
            case PAUSED -> MenuText.create().locale(PREFIX + "paused");
            case ACTIVE ->
                MenuText.create()
                        .locale(
                                PREFIX + "timeLeft",
                                Values.text("time", pretty(Duration.between(now, contest.ends()))));
            default -> MenuText.create().locale(PREFIX + "closed");
        };
    }

    private static String pretty(Duration duration) {
        return Texts.prettyDuration(duration.isNegative() ? Duration.ZERO : duration);
    }

    /**
     * A button of the contest.
     *
     * @param label   locale key of its text
     * @param style   style tag in front of its text
     * @param once    whether it works a single time
     * @param handler what it does
     */
    private record Button(String label, String style, boolean once, Consumer<Player> handler) {}

    /**
     * The contest shown.
     *
     * @param contest   the contest
     * @param ranking   the companies that delivered and still exist, best first, as many as are shown
     * @param own       the entry of the player's company, empty when it never took part
     * @param place     the place of the player's company among the companies that delivered, empty without one
     * @param member    whether the player is part of a company
     * @param mayManage whether the player may let the company join and leave
     * @param now       the current time in UTC
     * @param notice    line shown under the title, such as the result of the last button
     */
    public record Model(
            EventOffer contest,
            List<ContestEntry> ranking,
            Optional<ContestEntry> own,
            OptionalInt place,
            boolean member,
            boolean mayManage,
            LocalDateTime now,
            Optional<MenuText> notice) {}

    /**
     * What the buttons of the contest do.
     */
    public interface Handlers {
        /**
         * @param player  player who clicked
         * @param contest the contest to deliver every matching item into
         */
        void deliverAll(Player player, EventOffer contest);

        /**
         * @param player  player who clicked
         * @param contest the contest the company joins
         */
        void join(Player player, EventOffer contest);

        /**
         * @param player  player who clicked
         * @param contest the contest the company leaves
         */
        void leave(Player player, EventOffer contest);

        /**
         * @param player player who wants to see the contests
         */
        void list(Player player);
    }
}
