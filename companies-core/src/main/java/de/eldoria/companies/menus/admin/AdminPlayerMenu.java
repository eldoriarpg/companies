/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.admin;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.data.wrapper.inbox.InboxEntry;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.menus.HistoryTexts;
import de.eldoria.companies.menus.Keys;
import de.eldoria.companies.menus.Menu;
import de.eldoria.companies.menus.MenuActions;
import de.eldoria.companies.menus.MenuComponents;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.services.notifications.NotificationRenderer;
import de.eldoria.eldoutilities.messages.MessageSender;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * What an admin needs to know about a player: company and role with the permissions, the player's orders by state
 * with the unfinished ones listed, the items waiting by kind, and the unread count with the latest notifications.
 * Every listed order opens the admin view of the order.
 */
@SuppressWarnings("UnstableApiUsage")
public final class AdminPlayerMenu implements Menu<AdminPlayerMenu.Model> {
    private final MessageSender messages;
    private final NotificationRenderer renderer;
    private final AdminOrderActions actions;

    /**
     * @param messages renders the texts
     * @param renderer renders the notifications
     * @param actions  opens an order
     */
    public AdminPlayerMenu(MessageSender messages, NotificationRenderer renderer, AdminOrderActions actions) {
        this.messages = messages;
        this.renderer = renderer;
        this.actions = actions;
    }

    @Override
    public MenuText chat(Player player, Model model, MenuActions buttons) {
        var text = title(model).newLine().append(details(model));
        var open = model.openOrders();
        for (var index = 0; index < open.size(); index++) {
            var order = open.get(index);
            var key = Keys.of("act_order", index);
            text.newLine()
                    .text("  ")
                    .append(orderLine(Keys.of("order", index), order))
                    .text(
                            " <" + key + "><show>[<i18n:words.info>]</" + key + ">",
                            buttons.chatAction(key, viewer -> actions.info(viewer, order.id())));
        }
        return text.newLine().append(notifications(player, model));
    }

    @Override
    public Optional<Dialog> dialog(Player player, Model model, MenuActions buttons) {
        var components = new MenuComponents(messages, player);
        List<ActionButton> list = new ArrayList<>();
        for (var order : model.openOrders()) {
            list.add(buttons.button(
                    components.of(orderLine("order", order)),
                    null,
                    (viewer, response) -> actions.info(viewer, order.id())));
        }
        var base = DialogBase.builder(components.of(title(model)))
                .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                .body(List.of(
                        DialogBody.plainMessage(components.of(details(model))),
                        DialogBody.plainMessage(components.of(notifications(player, model)))))
                .build();
        if (list.isEmpty()) {
            return Optional.of(
                    Dialog.create(factory -> factory.empty().base(base).type(DialogType.notice())));
        }
        var type = DialogType.multiAction(list).columns(1).build();
        return Optional.of(Dialog.create(factory -> factory.empty().base(base).type(type)));
    }

    private static MenuText title(Model model) {
        return MenuText.create()
                .text("<heading>")
                .locale("companyadmin.player.title", Values.text("player_name", model.name()));
    }

    /**
     * Company, role, permissions, order counts and waiting items.
     *
     * @param model the player
     * @return the details
     */
    MenuText details(Model model) {
        var text = MenuText.create()
                .text(
                        "<name>UUID: <value><player_uuid>",
                        Values.text("player_uuid", model.id().toString()))
                .newLine();
        model.membership()
                .ifPresentOrElse(
                        membership -> text.append(membership(membership)),
                        () -> text.text(
                                "<name><i18n:companyadmin.order.company>: <inactive><i18n:companyadmin.player.noCompany>"));
        text.newLine().text("<heading><i18n:words.orders>");
        var counts = model.countsByState();
        for (var state : OrderState.values()) {
            var key = Keys.of("orders", state.name().toLowerCase(Locale.ROOT));
            text.newLine()
                    .text(
                            "  <name><i18n:" + state.translationKey() + ">: <value><" + key + ">",
                            Values.number(key, counts.getOrDefault(state, 0)));
        }
        return text.newLine()
                .text("<heading><i18n:companyadmin.player.itemsWaiting>")
                .newLine()
                .text(
                        "  <name><i18n:companyadmin.player.workerItems>: <value><worker_items>",
                        Values.number("worker_items", model.workerItems()))
                .newLine()
                .text(
                        "  <name><i18n:companyadmin.player.ownerItems>: <value><owner_items>",
                        Values.number("owner_items", model.ownerItems()));
    }

    private static MenuText membership(Membership membership) {
        var role = membership.owner() ? "placeholder.role.owner" : "placeholder.role.member";
        var text = MenuText.create()
                .text(
                        "<name><i18n:companyadmin.order.company>: <value><company_name> <inactive>(<i18n:" + role
                                + ">)",
                        Values.text("company_name", membership.company()))
                .newLine()
                .text("<name><i18n:words.permissions>: ");
        if (membership.permissions().isEmpty()) return text.text("<inactive>-");
        var permissions = membership.permissions();
        for (var index = 0; index < permissions.size(); index++) {
            if (index > 0) text.text("<inactive>, ");
            text.text("<value><i18n:" + permissions.get(index).translationKey() + ">");
        }
        return text;
    }

    private MenuText notifications(Player player, Model model) {
        var components = new MenuComponents(messages, player);
        var text = MenuText.create()
                .text(
                        "<heading><i18n:companyadmin.player.notifications> <inactive>(<i18n:companyadmin.player.unread>)",
                        Values.number("unread_count", model.unread()));
        var latest = model.latest();
        for (var index = 0; index < latest.size(); index++) {
            var entry = latest.get(index);
            var created = Keys.of("note", index, "created");
            var body = Keys.of("note", index, "body");
            var style = entry.read() ? "<inactive>" : "<active>";
            text.newLine()
                    .text(
                            "  " + style + "<" + created + "> <" + body + ">",
                            Values.text(created, HistoryTexts.date(entry.created())),
                            Values.component(body, components.of(renderer.body(entry.type(), entry.args()))));
        }
        return text;
    }

    private static MenuText orderLine(String prefix, SimpleOrder order) {
        var id = Keys.of(prefix, "id");
        var name = Keys.of(prefix, "name");
        return MenuText.create()
                .text(
                        "<inactive>#<" + id + "> <value><" + name + "> <name><i18n:"
                                + order.state().translationKey() + ">",
                        Values.number(id, order.id()),
                        Values.text(name, order.name()));
    }

    /**
     * A player's company membership.
     *
     * @param company     name of the company
     * @param owner       whether the player owns it
     * @param permissions permissions the player holds
     */
    public record Membership(String company, boolean owner, List<CompanyPermission> permissions) {
        public Membership {
            permissions = List.copyOf(permissions);
        }
    }

    /**
     * Everything shown about a player.
     *
     * @param name        name of the player
     * @param id          id of the player
     * @param membership  the player's company, empty without one
     * @param orders      the player's own orders
     * @param workerItems items returned to the player as a worker
     * @param ownerItems  items of received orders waiting for the player
     * @param unread      number of unread notifications
     * @param latest      the latest notifications
     */
    public record Model(
            String name,
            UUID id,
            Optional<Membership> membership,
            List<SimpleOrder> orders,
            int workerItems,
            int ownerItems,
            int unread,
            List<InboxEntry> latest) {
        public Model {
            orders = List.copyOf(orders);
            latest = List.copyOf(latest);
        }

        /**
         * @return number of the player's orders in every state
         */
        public Map<OrderState, Integer> countsByState() {
            Map<OrderState, Integer> counts = new EnumMap<>(OrderState.class);
            for (var order : orders) counts.merge(order.state(), 1, Integer::sum);
            return counts;
        }

        /**
         * @return the orders that were not received yet
         */
        public List<SimpleOrder> openOrders() {
            return orders.stream()
                    .filter(order -> order.state() != OrderState.RECEIVED)
                    .toList();
        }
    }
}
