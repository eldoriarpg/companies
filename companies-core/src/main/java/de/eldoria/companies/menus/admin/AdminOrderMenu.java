/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.admin;

import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.data.wrapper.history.HistoryEntry;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.data.wrapper.order.OrderContent;
import de.eldoria.companies.menus.HistoryTexts;
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
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Everything about one order for admins: both sides, times, money, every line with what each worker delivered, the
 * history entries of the order, and the admin actions its state allows.
 * <p>
 * Unclaiming, cancelling and marking delivered ask for a confirmation. Extending takes the hours from a suggested
 * command in chat and from an input in the dialog.
 */
@SuppressWarnings("UnstableApiUsage")
public final class AdminOrderMenu implements Menu<AdminOrderMenu.Model> {
    /**
     * Key of the hours input of the dialog.
     */
    static final String HOURS = "hours";

    /**
     * Hours an extension adds unless the admin enters others.
     */
    public static final int DEFAULT_HOURS = 24;

    private final MessageSender messages;
    private final Economy economy;
    private final AdminOrderActions actions;

    /**
     * @param messages renders the dialog texts
     * @param economy  economy formatting the money
     * @param actions  what the buttons do
     */
    public AdminOrderMenu(MessageSender messages, Economy economy, AdminOrderActions actions) {
        this.messages = messages;
        this.economy = economy;
        this.actions = actions;
    }

    /**
     * Reads the hours of an extension.
     *
     * @param entered hours as entered
     * @return the hours, or empty unless they are a whole number above zero
     */
    public static OptionalInt hours(@Nullable String entered) {
        if (entered == null) return OptionalInt.empty();
        try {
            var hours = Integer.parseInt(entered.trim());
            return hours > 0 ? OptionalInt.of(hours) : OptionalInt.empty();
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }

    @Override
    public MenuText chat(Player player, Model model, MenuActions buttons) {
        var text = MenuText.create();
        model.notice().ifPresent(notice -> text.append(notice).newLine());
        text.append(title(model)).newLine().append(details(model));
        var id = model.order().id();
        var row = MenuText.create();
        if (model.order().state() == OrderState.CLAIMED) {
            row.text(
                            "<act_unclaim><remove>[<i18n:companyadmin.order.unclaim>]</act_unclaim> ",
                            buttons.chatAction("act_unclaim", viewer -> actions.unclaim(viewer, id)))
                    .text(
                            "<act_deliver><add>[<i18n:companyadmin.order.deliver>]</act_deliver> ",
                            buttons.chatAction("act_deliver", viewer -> actions.deliver(viewer, id)));
        }
        if (isOpen(model)) {
            row.text(
                            "<act_refund><remove>[<i18n:companyadmin.order.cancelRefund>]</act_refund> ",
                            buttons.chatAction("act_refund", viewer -> actions.cancel(viewer, id, true)))
                    .text(
                            "<act_keep><remove>[<i18n:companyadmin.order.cancelKeep>]</act_keep> ",
                            buttons.chatAction("act_keep", viewer -> actions.cancel(viewer, id, false)))
                    .text(
                            "<act_extend><modify>[<i18n:companyadmin.order.extend>]</act_extend>",
                            Values.click(
                                    "act_extend",
                                    ClickEvent.suggestCommand(
                                            "/companyadmin order extend " + id + " " + DEFAULT_HOURS)));
        }
        if (isOpen(model)) text.newLine().append(row);
        return text;
    }

    @Override
    public Optional<Dialog> dialog(Player player, Model model, MenuActions buttons) {
        var components = new MenuComponents(messages, player);
        List<DialogBody> body = new ArrayList<>();
        model.notice().ifPresent(notice -> body.add(DialogBody.plainMessage(components.of(notice))));
        body.add(DialogBody.plainMessage(components.of(details(model))));
        var id = model.order().id();
        List<ActionButton> list = new ArrayList<>();
        List<DialogInput> inputs = new ArrayList<>();
        if (model.order().state() == OrderState.CLAIMED) {
            list.add(buttons.button(
                    label(components, "<remove>", "companyadmin.order.unclaim"),
                    null,
                    (viewer, response) -> actions.unclaim(viewer, id)));
            list.add(buttons.button(
                    label(components, "<add>", "companyadmin.order.deliver"),
                    null,
                    (viewer, response) -> actions.deliver(viewer, id)));
        }
        if (isOpen(model)) {
            list.add(buttons.button(
                    label(components, "<remove>", "companyadmin.order.cancelRefund"),
                    null,
                    (viewer, response) -> actions.cancel(viewer, id, true)));
            list.add(buttons.button(
                    label(components, "<remove>", "companyadmin.order.cancelKeep"),
                    null,
                    (viewer, response) -> actions.cancel(viewer, id, false)));
            inputs.add(DialogInput.text(HOURS, components.locale("companyadmin.order.hours"))
                    .initial(String.valueOf(DEFAULT_HOURS))
                    .maxLength(6)
                    .build());
            list.add(buttons.button(
                    label(components, "<modify>", "companyadmin.order.extend"),
                    null,
                    (viewer, response) -> actions.extend(viewer, id, response.getText(HOURS))));
        }
        var base = DialogBase.builder(components.of(title(model)))
                .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                .body(body)
                .inputs(inputs)
                .build();
        if (list.isEmpty()) {
            return Optional.of(
                    Dialog.create(factory -> factory.empty().base(base).type(DialogType.notice())));
        }
        var type = DialogType.multiAction(list).columns(2).build();
        return Optional.of(Dialog.create(factory -> factory.empty().base(base).type(type)));
    }

    private static boolean isOpen(Model model) {
        var state = model.order().state();
        return state == OrderState.UNCLAIMED || state == OrderState.CLAIMED;
    }

    private static Component label(MenuComponents components, String style, String key) {
        return components.of(MenuText.create().text(style + "<i18n:" + key + ">"));
    }

    private static MenuText title(Model model) {
        var order = model.order();
        return MenuText.create()
                .locale(
                        "companyadmin.order.title",
                        Values.number("order_id", order.id()),
                        Values.text("order_name", order.name()));
    }

    /**
     * Every detail of the order, as shown in chat and in the dialog body.
     *
     * @param model the order with names and history
     * @return the details
     */
    MenuText details(Model model) {
        var order = model.order();
        var text = MenuText.create()
                .append(field("companyadmin.order.owner", "owner_name", model.ownerName()))
                .newLine()
                .append(field(
                        "companyadmin.order.company",
                        "company_name",
                        model.companyName().orElse("-")))
                .newLine()
                .text("<name><i18n:companyadmin.order.state>: <value><i18n:"
                        + order.state().translationKey() + ">")
                .newLine()
                .append(field("companyadmin.order.placed", "placed_at", HistoryTexts.date(order.created())));
        if (order.state() == OrderState.CLAIMED) {
            text.newLine()
                    .append(field("companyadmin.order.claimed", "claimed_at", HistoryTexts.date(order.claimed())));
        }
        model.deadline().ifPresent(deadline -> text.newLine().append(deadline(deadline, order.deliveryBonus())));
        text.newLine()
                .text(
                        "<name><i18n:companyadmin.order.extensions>: <value><order_extensions>",
                        Values.number("order_extensions", order.extensions()))
                .newLine()
                .text(
                        "<name><i18n:words.price>: <value><order_price> <name><i18n:words.fee>: <value><order_fee>",
                        Values.money("order_price", economy, order.price()),
                        Values.money("order_fee", economy, order.fee()));
        var contents = order.contents();
        for (var index = 0; index < contents.size(); index++) {
            text.newLine().append(line(index, contents.get(index), model.names()));
        }
        text.newLine().text("<heading><i18n:companyadmin.order.history>");
        if (model.history().isEmpty()) {
            text.newLine().text("<inactive><i18n:companyadmin.order.noHistory>");
        }
        var history = model.history();
        for (var index = 0; index < history.size(); index++) {
            text.newLine().append(historyLine(index, history.get(index)));
        }
        return text;
    }

    private static MenuText field(String label, String key, String value) {
        return MenuText.create().text("<name><i18n:" + label + ">: <value><" + key + ">", Values.text(key, value));
    }

    private static MenuText deadline(Deadline deadline, int bonus) {
        var label = deadline.removal() ? "companyadmin.order.removal" : "companyadmin.order.deadline";
        var text = field(label, "deadline_at", HistoryTexts.date(deadline.at()));
        if (deadline.removal()) return text;
        return text.text(
                " <inactive>(<i18n:companyadmin.order.bonus>: <delivery_bonus>)",
                Values.number("delivery_bonus", bonus));
    }

    private MenuText line(int index, OrderContent content, Map<UUID, String> names) {
        var item = Keys.of("line", index, "item");
        var delivered = Keys.of("line", index, "delivered");
        var amount = Keys.of("line", index, "amount");
        var price = Keys.of("line", index, "price");
        var text = MenuText.create()
                .text(
                        "<yellow><" + item + "> <blue><" + delivered + ">/<" + amount + "> <gold><" + price + ">",
                        Values.item(item, content.stack()),
                        Values.number(delivered, content.delivered()),
                        Values.number(amount, content.amount()),
                        Values.money(price, economy, content.price()));
        var workers = List.copyOf(content.workerAmount().entrySet());
        for (var worker = 0; worker < workers.size(); worker++) {
            var entry = workers.get(worker);
            var name = Keys.of("line", index, "worker", worker, "name");
            var items = Keys.of("line", index, "worker", worker, "items");
            text.newLine()
                    .text(
                            "  <name><" + name + ">: <value><" + items + ">",
                            Values.text(
                                    name,
                                    names.getOrDefault(
                                            entry.getKey(), entry.getKey().toString())),
                            Values.number(items, entry.getValue()));
        }
        return text;
    }

    private MenuText historyLine(int index, HistoryEntry entry) {
        var finished = Keys.of("history", index, "finished");
        var side = entry.ownerSide().isPresent() ? "companyadmin.order.ownerSide" : "companyadmin.order.companySide";
        return MenuText.create()
                .text("<name><i18n:" + side + ">: ")
                .append(HistoryTexts.outcome(entry))
                .text(" <inactive><" + finished + ">", Values.text(finished, HistoryTexts.date(entry.finished())));
    }

    /**
     * When an order runs out: the delivery deadline of a claimed order, or when an open order is removed.
     *
     * @param at      the time
     * @param removal true for the removal of an open order
     */
    public record Deadline(LocalDateTime at, boolean removal) {}

    /**
     * An order with the names of both sides and of its workers.
     *
     * @param order       the order with its deliveries
     * @param ownerName   name of the owner
     * @param companyName name of the company working on it, empty while nobody claimed it
     * @param deadline    when the order runs out, empty once it is delivered
     * @param names       names of the workers, by id
     * @param history     history entries of the order
     * @param notice      line shown on top, such as an error
     */
    public record Model(
            FullOrder order,
            String ownerName,
            Optional<String> companyName,
            Optional<Deadline> deadline,
            Map<UUID, String> names,
            List<HistoryEntry> history,
            Optional<MenuText> notice) {
        public Model {
            names = Map.copyOf(names);
            history = List.copyOf(history);
        }

        /**
         * @param notice line shown on top
         * @return this model with the line
         */
        public Model withNotice(MenuText notice) {
            return new Model(order, ownerName, companyName, deadline, names, history, Optional.of(notice));
        }
    }
}
