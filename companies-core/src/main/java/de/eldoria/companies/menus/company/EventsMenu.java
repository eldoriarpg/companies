/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.company;

import de.eldoria.companies.data.wrapper.event.ServerEvent;
import de.eldoria.companies.data.wrapper.offer.EventOffer;
import de.eldoria.companies.data.wrapper.offer.OfferKind;
import de.eldoria.companies.menus.Keys;
import de.eldoria.companies.menus.Menu;
import de.eldoria.companies.menus.MenuActions;
import de.eldoria.companies.menus.MenuComponents;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.menus.order.EventTexts;
import de.eldoria.companies.util.Texts;
import de.eldoria.eldoutilities.messages.MessageSender;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.DoubleFunction;

/**
 * The server events and event offers a player sees: each server event with its effect, the orders it applies to, its
 * time and, for a reward multiplier with a budget, what is left of it as a bar, with its offers under it; then the
 * offers on their own. Each offer shows its kind, name, what it asks for, its progress, its time and, for a contest,
 * the place of the player's company.
 * <p>
 * In chat every offer has an open button on its line; as a dialog the list is the body and every offer is a button.
 * Opening a goal shows its chunks in the order search, opening a contest shows the contest.
 */
public final class EventsMenu implements Menu<EventsMenu.Model> {
    private static final String PREFIX = "company.events.";

    private final MessageSender messages;
    private final DoubleFunction<String> money;
    private final Handlers handlers;

    /**
     * @param messages renders the rows and the dialog texts
     * @param money    formats the budget left of an event
     * @param handlers what the buttons do
     */
    public EventsMenu(MessageSender messages, DoubleFunction<String> money, Handlers handlers) {
        this.messages = messages;
        this.money = money;
        this.handlers = handlers;
    }

    @Override
    public MenuText chat(Player player, Model model, MenuActions actions) {
        var components = new MenuComponents(messages, player);
        var text = MenuText.create().text("<heading>").locale(PREFIX + "title");
        var row = 0;
        for (var shown : rows(model, components)) {
            var key = Keys.of("row", row++);
            var line = shown.line();
            var offer = shown.offer();
            if (offer != null) {
                var open = Keys.of("act_open", offer.id());
                line = MenuText.create()
                        .append(line)
                        .text(
                                " <" + open + "><show>[<i18n:" + PREFIX + "open>]</" + open + ">",
                                actions.chatAction(open, clicker -> handlers.open(clicker, offer)));
            }
            text.newLine().text("<" + key + ">", Values.component(key, components.of(line)));
        }
        return text;
    }

    @Override
    public Optional<Dialog> dialog(Player player, Model model, MenuActions actions) {
        var components = new MenuComponents(messages, player);
        List<DialogBody> body = new ArrayList<>();
        List<ActionButton> buttons = new ArrayList<>();
        for (var shown : rows(model, components)) {
            body.add(DialogBody.plainMessage(components.of(shown.line())));
            var offer = shown.offer();
            if (offer == null) continue;
            buttons.add(actions.button(
                    components.of(MenuText.create()
                            .text(
                                    "<show><i18n:" + PREFIX + "open> <value>#<id> <offer_name>",
                                    Values.number("id", offer.id()),
                                    Values.text("offer_name", offer.name()))),
                    null,
                    (clicker, response) -> handlers.open(clicker, offer)));
        }
        var base = DialogBase.builder(components.locale(PREFIX + "title"))
                .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                .body(body)
                .build();
        var type = buttons.isEmpty()
                ? DialogType.notice()
                : DialogType.multiAction(buttons).columns(2).build();
        return Optional.of(Dialog.create(factory -> factory.empty().base(base).type(type)));
    }

    /**
     * The rows of the list in their order: each event with its offers indented under it, then the offers of no event
     * shown.
     */
    private List<Row> rows(Model model, MenuComponents components) {
        List<Row> rows = new ArrayList<>();
        var shownEvents = model.events().stream().map(ServerEvent::id).toList();
        for (var event : model.events()) {
            rows.add(new Row(eventLine(event, model.now(), components), null));
            for (var offer : grouped(model.offers(), event.id())) {
                rows.add(new Row(
                        MenuText.create()
                                .text("  ")
                                .append(line(offer, model.places().get(offer.id()), model.now())),
                        offer));
            }
        }
        for (var offer : model.offers()) {
            if (offer.event() != null && shownEvents.contains(offer.event())) continue;
            rows.add(new Row(line(offer, model.places().get(offer.id()), model.now()), offer));
        }
        return rows;
    }

    /**
     * @param offers the offers shown
     * @param event  id of a server event
     * @return the offers of the event, in the order shown
     */
    public static List<EventOffer> grouped(List<EventOffer> offers, int event) {
        return offers.stream()
                .filter(offer -> Integer.valueOf(event).equals(offer.event()))
                .toList();
    }

    @Override
    public boolean holdsChat() {
        return false;
    }

    private MenuText eventLine(ServerEvent event, LocalDateTime now, MenuComponents components) {
        var text = MenuText.create()
                .text("<neutral>[<i18n:" + PREFIX + "serverEvent>]</neutral> ")
                .locale(
                        PREFIX + "eventEntry",
                        Values.text("event_name", event.name()),
                        Values.component("effect", components.of(EventTexts.effect(event))),
                        Values.component("scope", components.of(EventTexts.scope(event))))
                .space();
        if (event.activeAt(now)) {
            text.locale(PREFIX + "timeLeft", Values.text("time", pretty(Duration.between(now, event.ends()))));
        } else if (!event.starts().isAfter(now)) {
            text.locale(PREFIX + "starting");
        } else {
            text.locale(PREFIX + "startsIn", Values.text("time", pretty(Duration.between(now, event.starts()))));
        }
        var budget = event.budget();
        var left = event.remaining();
        if (event.kind().funded() && budget != null && budget > 0 && left.isPresent()) {
            text.space()
                    .text(Texts.progressBar(left.getAsDouble() / budget))
                    .space()
                    .locale(PREFIX + "budgetLeft", Values.text("budget_left", money.apply(left.getAsDouble())));
        }
        return text;
    }

    private static MenuText line(EventOffer offer, OptionalInt place, LocalDateTime now) {
        var kind = offer.kind() == OfferKind.CONTEST ? "offers.kind.contest" : "offers.kind.goal";
        var text = MenuText.create()
                .text("<neutral>[<i18n:" + kind + ">]</neutral> ")
                .locale(
                        PREFIX + "entry",
                        Values.number("id", offer.id()),
                        Values.text("offer_name", offer.name()),
                        Values.text("item", offer.line().label()),
                        Values.number("delivered", offer.delivered()),
                        Values.number("amount", offer.amount()))
                .space()
                .text(Texts.progressBar(offer.amount() <= 0 ? 1 : (double) offer.delivered() / offer.amount()))
                .space();
        switch (offer.state()) {
            case SCHEDULED ->
                text.locale(PREFIX + "startsIn", Values.text("time", pretty(Duration.between(now, offer.starts()))));
            case PAUSED -> text.locale(PREFIX + "paused");
            default ->
                text.locale(PREFIX + "timeLeft", Values.text("time", pretty(Duration.between(now, offer.ends()))));
        }
        if (place != null && place.isPresent()) {
            text.space().locale(PREFIX + "place", Values.number("place", place.getAsInt()));
        }
        return text;
    }

    private static String pretty(Duration duration) {
        return Texts.prettyDuration(duration.isNegative() ? Duration.ZERO : duration);
    }

    /**
     * A row of the list.
     *
     * @param line  the text of the row
     * @param offer the offer the row shows, null for a server event
     */
    private record Row(MenuText line, @Nullable EventOffer offer) {}

    /**
     * What the list shows.
     *
     * @param events the server events shown, the soonest start first
     * @param offers the offers shown, the soonest end first
     * @param places the place of the player's company in each contest shown, when it has one
     * @param now    the current time in UTC
     */
    public record Model(
            List<ServerEvent> events, List<EventOffer> offers, Map<Integer, OptionalInt> places, LocalDateTime now) {}

    /**
     * What the buttons of the list do.
     */
    public interface Handlers {
        /**
         * @param player player who clicked
         * @param offer  the offer to open
         */
        void open(Player player, EventOffer offer);
    }
}
