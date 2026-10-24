/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.company;

import de.eldoria.companies.data.wrapper.inbox.InboxEntry;
import de.eldoria.companies.menus.Keys;
import de.eldoria.companies.menus.Menu;
import de.eldoria.companies.menus.MenuActions;
import de.eldoria.companies.menus.MenuComponents;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.services.notifications.InboxPage;
import de.eldoria.companies.services.notifications.NotificationRenderer;
import de.eldoria.companies.util.Times;
import de.eldoria.eldoutilities.messages.MessageSender;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import org.bukkit.entity.Player;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * A page of the player's inbox, newest first, with the entries grouped under their day.
 * <p>
 * Unread entries carry a marker; the page is marked read once it is shown. Every message keeps the button of its
 * type, which runs a real command. Each message is rendered on its own, so equal placeholder names of two entries
 * never clash. Chat and dialog offer the same actions: read all, clear the read entries, the mute settings and the
 * neighbouring pages. A notice, such as the result of the last action, is shown on top.
 */
public final class InboxMenu implements Menu<InboxMenu.Model> {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final int DIALOG_WIDTH = 400;

    private final MessageSender messages;
    private final NotificationRenderer renderer;
    private final Handlers handlers;

    /**
     * @param messages renders the messages of the entries
     * @param renderer builds the messages of the entries
     * @param handlers what the buttons do
     */
    public InboxMenu(MessageSender messages, NotificationRenderer renderer, Handlers handlers) {
        this.messages = messages;
        this.renderer = renderer;
        this.handlers = handlers;
    }

    @Override
    public MenuText chat(Player player, Model model, MenuActions actions) {
        var text = notice(model);
        text.append(title())
                .space()
                .append(button(actions, "act_read_all", "<add>[<i18n:inbox.readAll>]", handlers::readAll))
                .space()
                .append(button(actions, "act_clear", "<remove>[<i18n:inbox.clear>]", handlers::clearRead))
                .space()
                .text(
                        "<act_settings><modify>[<i18n:inbox.settings>]</act_settings>",
                        actions.chatAction("act_settings", handlers::settings));
        text.newLine().append(entries(player, model.page()));
        var paging = paging(model.page());
        if (paging.hasPrevious() || paging.hasNext()) {
            text.newLine().append(paging.chat(actions, previous(model), next(model)));
        }
        return text;
    }

    @Override
    public Optional<Dialog> dialog(Player player, Model model, MenuActions actions) {
        var components = new MenuComponents(messages, player);
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(actions.buttonOnce(
                components.locale("inbox.readAll"), null, (clicker, response) -> handlers.readAll(clicker)));
        buttons.add(actions.buttonOnce(
                components.locale("inbox.clear"), null, (clicker, response) -> handlers.clearRead(clicker)));
        buttons.add(actions.button(
                components.locale("inbox.settings"), null, (clicker, response) -> handlers.settings(clicker)));
        paging(model.page()).addButtons(buttons, actions, previous(model), next(model));
        var body = notice(model).append(entries(player, model.page()));
        var base = DialogBase.builder(components.of(title()))
                .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                .body(List.of(DialogBody.plainMessage(components.of(body), DIALOG_WIDTH)))
                .build();
        var type = DialogType.multiAction(buttons).columns(2).build();
        return Optional.of(Dialog.create(factory -> factory.empty().base(base).type(type)));
    }

    /**
     * The entries of a page under their day headers.
     *
     * @param player viewing player, for the language of the messages
     * @param page   the page
     * @return the entries
     */
    MenuText entries(Player player, InboxPage page) {
        var entries = page.entries();
        if (entries.isEmpty()) return MenuText.create().text("  <inactive><i18n:inbox.empty>");
        var components = new MenuComponents(messages, player);
        var text = MenuText.create();
        LocalDate day = null;
        var days = 0;
        for (var i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            var date = Times.local(entry.created()).toLocalDate();
            if (!date.equals(day)) {
                if (day != null) text.newLine();
                text.append(dayHeader(date, Times.local(page.now()).toLocalDate(), days++));
                day = date;
            }
            text.newLine().append(entry(components, i, entry));
        }
        return text;
    }

    private MenuText entry(MenuComponents components, int index, InboxEntry entry) {
        var time = Keys.of("entry", index, "time");
        var message = Keys.of("entry", index, "message");
        var marker = entry.read() ? "" : "<add>[<i18n:inbox.unread>]</add> ";
        return MenuText.create()
                .text(
                        "  <name><" + time + "></name> " + marker + "<" + message + ">",
                        Values.text(time, TIME_FORMATTER.format(Times.local(entry.created()))),
                        Values.component(message, components.of(renderer.message(entry.type(), entry.args()))));
    }

    private static MenuText dayHeader(LocalDate date, LocalDate today, int index) {
        if (date.equals(today)) return MenuText.create().text("<heading><i18n:words.today>:");
        if (date.equals(today.minusDays(1L))) return MenuText.create().text("<heading><i18n:words.yesterday>:");
        var key = Keys.of("date", index);
        return MenuText.create().text("<heading><" + key + ">:", Values.text(key, DATE_FORMATTER.format(date)));
    }

    private static MenuText title() {
        return MenuText.create().text("<heading><i18n:inbox.title>");
    }

    private static MenuText notice(Model model) {
        var text = MenuText.create();
        model.notice().ifPresent(notice -> text.append(notice).newLine());
        return text;
    }

    private static MenuText button(MenuActions actions, String key, String label, Consumer<Player> handler) {
        return MenuText.create().text("<" + key + ">" + label + "</" + key + ">", actions.chatActionOnce(key, handler));
    }

    private static Paging paging(InboxPage page) {
        return Paging.open(page.number(), page.hasNext());
    }

    private Consumer<Player> previous(Model model) {
        return viewer -> handlers.page(viewer, model.page().number() - 1);
    }

    private Consumer<Player> next(Model model) {
        return viewer -> handlers.page(viewer, model.page().number() + 1);
    }

    /**
     * What the menu shows.
     *
     * @param page   the page of the inbox
     * @param notice a line shown on top, such as the result of the last action
     */
    public record Model(InboxPage page, Optional<MenuText> notice) {
        /**
         * @param page the page of the inbox
         * @return the page without a notice
         */
        public static Model of(InboxPage page) {
            return new Model(page, Optional.empty());
        }
    }

    /**
     * What the buttons of the menu do.
     */
    public interface Handlers {
        /**
         * @param player viewing player
         * @param number page to show, from one
         */
        void page(Player player, int number);

        /**
         * @param player player whose entries are marked read
         */
        void readAll(Player player);

        /**
         * @param player player whose read entries are deleted
         */
        void clearRead(Player player);

        /**
         * @param player player whose mute settings are shown
         */
        void settings(Player player);
    }
}
