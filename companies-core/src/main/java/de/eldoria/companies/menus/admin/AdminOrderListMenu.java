/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.admin;

import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.menus.HistoryTexts;
import de.eldoria.companies.menus.Keys;
import de.eldoria.companies.menus.Menu;
import de.eldoria.companies.menus.MenuActions;
import de.eldoria.companies.menus.MenuComponents;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.menus.company.Paging;
import de.eldoria.eldoutilities.messages.MessageSender;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * One page of the orders an admin searched for, of one player or one company, the newest first. Every order shows
 * id, name, state, owner and company, and opens the order when clicked.
 */
@SuppressWarnings("UnstableApiUsage")
public final class AdminOrderListMenu implements Menu<AdminOrderListMenu.Model> {
    private final MessageSender messages;
    private final AdminOrderActions actions;
    private final BiConsumer<Player, Integer> pages;

    /**
     * @param messages renders the dialog texts
     * @param actions  opens an order
     * @param pages    shows another page of the same search
     */
    public AdminOrderListMenu(MessageSender messages, AdminOrderActions actions, BiConsumer<Player, Integer> pages) {
        this.messages = messages;
        this.actions = actions;
        this.pages = pages;
    }

    @Override
    public MenuText chat(Player player, Model model, MenuActions buttons) {
        var text = title(model);
        if (model.rows().isEmpty()) {
            text.newLine().text("<inactive><i18n:companyadmin.order.empty>");
        }
        var rows = model.rows();
        for (var index = 0; index < rows.size(); index++) {
            var row = rows.get(index);
            var open = Keys.of("act_order", index);
            text.newLine()
                    .append(summary(Keys.of("order", index), row))
                    .text(
                            " <" + open + "><show>[<i18n:words.info>]</" + open + ">",
                            buttons.chatAction(
                                    open,
                                    viewer -> actions.info(viewer, row.order().id())));
        }
        return text.newLine().append(model.paging().chat(buttons, previous(model), next(model)));
    }

    @Override
    public Optional<Dialog> dialog(Player player, Model model, MenuActions buttons) {
        var components = new MenuComponents(messages, player);
        List<ActionButton> list = new ArrayList<>();
        for (var row : model.rows()) {
            list.add(buttons.button(
                    components.of(summary("order", row)),
                    null,
                    (viewer, response) -> actions.info(viewer, row.order().id())));
        }
        var paging = model.paging();
        paging.addButtons(list, buttons, previous(model), next(model));
        var body = MenuText.create();
        if (model.rows().isEmpty())
            body.text("<inactive><i18n:companyadmin.order.empty>").newLine();
        body.text("<heading><i18n:words.page> ").append(paging.label());
        var base = DialogBase.builder(components.of(title(model)))
                .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                .body(List.of(DialogBody.plainMessage(components.of(body))))
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
                .locale("companyadmin.order.listTitle", Values.text("search_target", model.target()));
    }

    /**
     * One order of the list.
     *
     * @param prefix key prefix, unique within the list
     * @param row    the order
     * @return the line
     */
    static MenuText summary(String prefix, Row row) {
        var order = row.order();
        var id = Keys.of(prefix, "id");
        var name = Keys.of(prefix, "name");
        var owner = Keys.of(prefix, "owner");
        var company = Keys.of(prefix, "company");
        var placed = Keys.of(prefix, "placed");
        var solo = order.isSolo() ? " <inactive>(<i18n:companyadmin.order.solo>)" : "";
        return MenuText.create()
                .text(
                        "<inactive>#<" + id + "> <value><" + name + "> <name><i18n:"
                                + order.state().translationKey() + "> <yellow><" + owner + "> <aqua><" + company
                                + ">" + solo + " <inactive><" + placed + ">",
                        Values.number(id, order.id()),
                        Values.text(name, order.name()),
                        Values.text(owner, row.ownerName()),
                        Values.text(company, row.companyName().orElse("-")),
                        Values.text(placed, HistoryTexts.date(order.created())));
    }

    private Consumer<Player> previous(Model model) {
        return viewer -> pages.accept(viewer, model.page() - 1);
    }

    private Consumer<Player> next(Model model) {
        return viewer -> pages.accept(viewer, model.page() + 1);
    }

    /**
     * An order with the names of both sides.
     *
     * @param order       the order
     * @param ownerName   name of the owner
     * @param companyName name of the company working on it, or of the player working on it alone; empty while nobody
     *                    claimed it
     */
    public record Row(SimpleOrder order, String ownerName, Optional<String> companyName) {}

    /**
     * One page of a search.
     *
     * @param target  the player or company searched for
     * @param page    number of the page, from one
     * @param rows    orders of the page
     * @param hasNext whether another page may follow
     */
    public record Model(String target, int page, List<Row> rows, boolean hasNext) {
        public Model {
            rows = List.copyOf(rows);
        }

        /**
         * A page as the repository returned it. A full page may be followed by another one.
         *
         * @param target   the player or company searched for
         * @param page     number of the page, from one
         * @param rows     orders of the page
         * @param pageSize orders on a full page
         * @return the model
         */
        public static Model of(String target, int page, List<Row> rows, int pageSize) {
            return new Model(target, page, rows, rows.size() >= pageSize);
        }

        Paging paging() {
            return Paging.open(page, hasNext);
        }
    }
}
