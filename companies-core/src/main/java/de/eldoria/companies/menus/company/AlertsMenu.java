/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.company;

import de.eldoria.companies.data.wrapper.alert.OrderAlert;
import de.eldoria.companies.menus.Keys;
import de.eldoria.companies.menus.Menu;
import de.eldoria.companies.menus.MenuActions;
import de.eldoria.companies.menus.MenuComponents;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.orders.query.OrderFilter;
import de.eldoria.eldoutilities.messages.MessageSender;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;

/**
 * The order alerts of a company: per alert its name, what it matches, whether it is on and how long ago it last
 * notified, with buttons to show its orders and, for members who manage orders, to switch it and to remove it.
 * <p>
 * In chat each alert is one line with its buttons; as a dialog the list is the body and every action is a button. The
 * handlers show the menu again with the new state.
 */
public final class AlertsMenu implements Menu<AlertsMenu.Model> {
    private static final String SEPARATOR = " <inactive>·</inactive> ";

    private final MessageSender messages;
    private final Economy economy;
    private final Handlers handlers;

    /**
     * @param messages renders the dialog texts
     * @param economy  formats prices
     * @param handlers what the buttons do
     */
    public AlertsMenu(MessageSender messages, Economy economy, Handlers handlers) {
        this.messages = messages;
        this.economy = economy;
        this.handlers = handlers;
    }

    @Override
    public MenuText chat(Player player, Model model, MenuActions actions) {
        var text = header(model);
        for (var i = 0; i < model.alerts().size(); i++) {
            text.newLine().append(row(model, i)).append(buttons(model, i, actions));
        }
        return text.newLine()
                .text(
                        "<act_search><show>[<i18n:words.search>]</act_search>",
                        actions.chatAction("act_search", handlers::search));
    }

    @Override
    public Optional<Dialog> dialog(Player player, Model model, MenuActions actions) {
        var components = new MenuComponents(messages, player);
        var body = header(model);
        for (var i = 0; i < model.alerts().size(); i++) body.newLine().append(row(model, i));
        List<ActionButton> buttons = new ArrayList<>();
        for (var alert : model.alerts()) {
            buttons.add(actions.button(
                    label(components, "<show><i18n:words.show>", alert),
                    null,
                    (clicker, response) -> handlers.show(clicker, alert)));
            if (!model.mayManage()) continue;
            buttons.add(actions.button(
                    label(components, alert.enabled() ? "<remove><i18n:words.off>" : "<add><i18n:words.on>", alert),
                    null,
                    (clicker, response) -> handlers.toggle(clicker, alert)));
            buttons.add(actions.buttonOnce(
                    label(components, "<remove><i18n:words.remove>", alert),
                    null,
                    (clicker, response) -> handlers.remove(clicker, alert)));
        }
        buttons.add(actions.button(
                components.locale("words.search"), null, (clicker, response) -> handlers.search(clicker)));
        var base = DialogBase.builder(components.locale("company.alerts.title"))
                .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                .body(List.of(DialogBody.plainMessage(components.of(body))))
                .build();
        var type = DialogType.multiAction(buttons)
                .columns(model.mayManage() ? 3 : 2)
                .build();
        return Optional.of(Dialog.create(factory -> factory.empty().base(base).type(type)));
    }

    private static MenuText header(Model model) {
        var text = MenuText.create()
                .text("<heading><i18n:company.alerts.title> ")
                .locale(
                        "company.alerts.count",
                        Values.number("count", model.alerts().size()),
                        Values.number("limit", model.limit()));
        model.notice().ifPresent(notice -> text.newLine().append(notice));
        if (model.alerts().isEmpty()) text.newLine().text("  <inactive><i18n:company.alerts.none>");
        return text;
    }

    private MenuText row(Model model, int index) {
        var alert = model.alerts().get(index);
        var name = Keys.of("alert", index, "name");
        return MenuText.create()
                .text("  <value><" + name + "> ", Values.text(name, alert.name()))
                .text(
                        alert.enabled()
                                ? "<active>[<i18n:words.on>]</active>"
                                : "<inactive>[<i18n:words.off>]</inactive>")
                .newLine()
                .text("    <neutral>")
                .append(describe(alert.filter(), economy::format, Keys.of("alert", index)))
                .text(SEPARATOR)
                .append(lastNotified(alert, model.now(), Keys.of("alert", index, "notified")));
    }

    private MenuText buttons(Model model, int index, MenuActions actions) {
        var alert = model.alerts().get(index);
        var text = MenuText.create()
                .text(" ")
                .append(button(
                        actions,
                        Keys.of("act_show", index),
                        "<show>[<i18n:words.show>]",
                        false,
                        player -> handlers.show(player, alert)));
        if (!model.mayManage()) return text;
        var toggle = alert.enabled() ? "<remove>[<i18n:words.off>]" : "<add>[<i18n:words.on>]";
        return text.space()
                .append(button(
                        actions, Keys.of("act_toggle", index), toggle, false, player -> handlers.toggle(player, alert)))
                .space()
                .append(button(
                        actions,
                        Keys.of("act_remove", index),
                        "<remove>[<i18n:words.remove>]",
                        true,
                        player -> handlers.remove(player, alert)));
    }

    /**
     * What a filter matches, such as {@code Materials: iron_ingot (exact) · Price $10.00–$500.00 · Items ≤ 256}.
     *
     * @param filter the filter
     * @param money  formats prices
     * @param prefix prefix of the placeholder keys, unique within the text the description is added to
     * @return the description
     */
    public static MenuText describe(OrderFilter filter, DoubleFunction<String> money, String prefix) {
        List<MenuText> parts = new ArrayList<>();
        var name = filter.name();
        if (name != null) {
            var key = Keys.of(prefix, "filter_name");
            parts.add(MenuText.create().text("<i18n:words.name> \"<" + key + ">\"", Values.text(key, name)));
        }
        if (!filter.materials().isEmpty()) parts.add(materials(filter, Keys.of(prefix, "filter_materials")));
        range(filter.minPrice(), filter.maxPrice(), Double.MAX_VALUE, money)
                .ifPresent(range -> parts.add(part("words.price", Keys.of(prefix, "filter_price"), range)));
        range(filter.minSize(), filter.maxSize(), Integer.MAX_VALUE, size -> String.valueOf((long) size))
                .ifPresent(range -> parts.add(part("words.items", Keys.of(prefix, "filter_size"), range)));
        if (parts.isEmpty()) return MenuText.create().text("<i18n:company.alerts.anyOrder>");
        var text = MenuText.create().append(parts.getFirst());
        parts.stream().skip(1).forEach(part -> text.text(SEPARATOR + "<neutral>")
                .append(part));
        return text;
    }

    private static MenuText materials(OrderFilter filter, String key) {
        var text = MenuText.create()
                .text(
                        "<i18n:words.materials>: <" + key + "> (",
                        Values.text(key, String.join(", ", filter.materials())));
        if (filter.materials().size() > 1) {
            text.text(filter.anyMaterial() ? "<i18n:words.any>, " : "<i18n:words.all>, ");
        }
        return text.text(filter.exactMatch() ? "<i18n:words.exact>)" : "<i18n:words.part>)");
    }

    private static MenuText part(String label, String key, String range) {
        return MenuText.create().text("<i18n:" + label + "> <" + key + ">", Values.text(key, range));
    }

    /**
     * A range such as {@code 10–500}, {@code ≥ 10} or {@code ≤ 500}.
     *
     * @param min       lower bound
     * @param max       upper bound
     * @param unbounded upper bound meaning no limit
     * @param format    formats a bound
     * @return the range, empty when it limits nothing
     */
    static Optional<String> range(double min, double max, double unbounded, DoubleFunction<String> format) {
        var hasMin = min > 0;
        var hasMax = max < unbounded;
        if (hasMin && hasMax) return Optional.of(format.apply(min) + "–" + format.apply(max));
        if (hasMin) return Optional.of("≥ " + format.apply(min));
        if (hasMax) return Optional.of("≤ " + format.apply(max));
        return Optional.empty();
    }

    private static MenuText lastNotified(OrderAlert alert, LocalDateTime now, String key) {
        return alert.lastNotified()
                .map(last -> MenuText.create()
                        .text(
                                "<i18n:company.alerts.sinceNotice> <" + key + ">",
                                Values.text(key, TimeSpans.between(last, now))))
                .orElseGet(() -> MenuText.create().text("<i18n:company.alerts.neverNotified>"));
    }

    private static MenuText button(
            MenuActions actions, String key, String label, boolean once, Consumer<Player> handler) {
        var action = once ? actions.chatActionOnce(key, handler) : actions.chatAction(key, handler);
        return MenuText.create().text("<" + key + ">" + label + "</" + key + ">", action);
    }

    private static Component label(MenuComponents components, String action, OrderAlert alert) {
        return components.of(
                MenuText.create().text(action + " <value><subject>", Values.text("subject", alert.name())));
    }

    /**
     * The alerts shown.
     *
     * @param alerts    alerts of the company by name
     * @param limit     alerts the company may have
     * @param mayManage whether the viewer may switch and remove alerts
     * @param now       current time, for the time since the last notice
     * @param notice    line shown on top, such as the result of the last action
     */
    public record Model(
            List<OrderAlert> alerts, int limit, boolean mayManage, LocalDateTime now, Optional<MenuText> notice) {}

    /**
     * What the buttons of the menu do.
     */
    public interface Handlers {
        /**
         * @param player player who clicked
         * @param alert  alert whose orders are shown
         */
        void show(Player player, OrderAlert alert);

        /**
         * @param player player who clicked
         * @param alert  alert to turn on or off
         */
        void toggle(Player player, OrderAlert alert);

        /**
         * @param player player who clicked
         * @param alert  alert to remove
         */
        void remove(Player player, OrderAlert alert);

        /**
         * @param player player who wants to search for orders, to save the search as an alert
         */
        void search(Player player);
    }
}
