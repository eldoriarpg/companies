/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.company;

import de.eldoria.companies.menus.MenuActions;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Page;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.util.Texts;
import io.papermc.paper.registry.data.dialog.ActionButton;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.OptionalInt;
import java.util.function.Consumer;

/**
 * The paging controls of a list menu: arrows to the previous and the next page around the page number.
 *
 * @param number      number of the shown page, from one
 * @param count       number of pages, when known
 * @param hasPrevious whether a page comes before this one
 * @param hasNext     whether a page follows this one
 */
public record Paging(int number, OptionalInt count, boolean hasPrevious, boolean hasNext) {
    /**
     * @param page a page of a list held in memory
     * @return the controls of the page
     */
    public static Paging of(Page<?> page) {
        return new Paging(page.number() + 1, OptionalInt.of(page.count()), page.hasPrevious(), page.hasNext());
    }

    /**
     * @param number  number of the shown page, from one
     * @param hasNext whether a page follows
     * @return the controls of a page of a list whose length is unknown
     */
    public static Paging open(int number, boolean hasNext) {
        return new Paging(number, OptionalInt.empty(), number > 1, hasNext);
    }

    /**
     * The chat line with the arrows, which are inactive at either end.
     *
     * @param actions  binds the arrows
     * @param previous shows the previous page
     * @param next     shows the next page
     * @return the line
     */
    public MenuText chat(MenuActions actions, Consumer<Player> previous, Consumer<Player> next) {
        var text = MenuText.create();
        if (hasPrevious) {
            text.text(
                    "<act_previous><active>" + Texts.LEFT_ARROW + "</act_previous>",
                    actions.chatAction("act_previous", previous));
        } else {
            text.text("<inactive>" + Texts.LEFT_ARROW);
        }
        text.text(" <i18n:words.page> ").append(label());
        if (hasNext) {
            text.text(" <act_next><active>" + Texts.RIGHT_ARROW + "</act_next>", actions.chatAction("act_next", next));
        } else {
            text.text(" <inactive>" + Texts.RIGHT_ARROW);
        }
        return text;
    }

    /**
     * The page number, with the number of pages when it is known.
     *
     * @return the label
     */
    public MenuText label() {
        var text = MenuText.create().text("<aqua><page_number>", Values.number("page_number", number));
        count.ifPresent(pages -> text.text("/<page_count>", Values.number("page_count", pages)));
        return text;
    }

    /**
     * Adds the dialog buttons to the previous and the next page, where there are such pages.
     *
     * @param buttons  buttons of the dialog
     * @param actions  binds the buttons
     * @param previous shows the previous page
     * @param next     shows the next page
     */
    public void addButtons(
            List<ActionButton> buttons, MenuActions actions, Consumer<Player> previous, Consumer<Player> next) {
        if (hasPrevious) {
            buttons.add(actions.button(
                    Component.text(Texts.LEFT_ARROW), null, (clicker, response) -> previous.accept(clicker)));
        }
        if (hasNext) {
            buttons.add(actions.button(
                    Component.text(Texts.RIGHT_ARROW), null, (clicker, response) -> next.accept(clicker)));
        }
    }
}
