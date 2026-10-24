/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.company;

import de.eldoria.companies.data.wrapper.company.Application;
import de.eldoria.companies.menus.Keys;
import de.eldoria.companies.menus.Menu;
import de.eldoria.companies.menus.MenuActions;
import de.eldoria.companies.menus.MenuService;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Page;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.services.MembershipService.ApplicationOverview;
import de.eldoria.eldoutilities.messages.MessageSender;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The open applications to a company, a page at a time, with buttons to accept or deny each of them.
 * <p>
 * The message of an applicant is shown as typed, never parsed. In chat every application is a line with its buttons
 * and the message below; as a dialog the page is the body and every action is a button. Accept and deny work once;
 * the handlers show the menu again with the new state.
 */
public final class ApplicationsMenu implements Menu<ApplicationsMenu.Model> {
    /**
     * Applications on one page.
     */
    static final int PAGE_SIZE = 5;

    private final MessageSender messages;
    private final MenuService menus;
    private final Handlers handlers;

    /**
     * @param messages renders the dialog texts
     * @param menus    shows other pages
     * @param handlers what the buttons do
     */
    public ApplicationsMenu(MessageSender messages, MenuService menus, Handlers handlers) {
        this.messages = messages;
        this.menus = menus;
        this.handlers = handlers;
    }

    @Override
    public MenuText chat(Player player, Model model, MenuActions actions) {
        var text = title(model).newLine().append(entries(model, Optional.of(actions)));
        var page = model.page();
        if (page.count() > 1) {
            text.newLine().append(Paging.of(page).chat(actions, previous(model), next(model)));
        }
        return text;
    }

    @Override
    public Optional<Dialog> dialog(Player player, Model model, MenuActions actions) {
        var texts = new SearchTexts(messages, player);
        var overview = model.overview();
        List<ActionButton> buttons = new ArrayList<>();
        for (var application : model.page().items()) {
            var name = overview.name(application.applicant());
            buttons.add(actions.buttonOnce(
                    label(texts, "<add><i18n:words.accept>", name),
                    null,
                    (clicker, response) -> handlers.accept(clicker, application)));
            buttons.add(actions.buttonOnce(
                    label(texts, "<remove><i18n:words.deny>", name),
                    null,
                    (clicker, response) -> handlers.deny(clicker, application)));
        }
        Paging.of(model.page()).addButtons(buttons, actions, previous(model), next(model));
        var base = DialogBase.builder(texts.of(title(model)))
                .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                .body(List.of(DialogBody.plainMessage(texts.of(entries(model, Optional.empty())))))
                .build();
        var type = buttons.isEmpty()
                ? DialogType.notice()
                : DialogType.multiAction(buttons).columns(2).build();
        return Optional.of(Dialog.create(factory -> factory.empty().base(base).type(type)));
    }

    /**
     * The applications of the shown page, with chat buttons when actions are given.
     *
     * @param model   the applications
     * @param actions binds the chat buttons, empty for the dialog body
     * @return the applications
     */
    MenuText entries(Model model, Optional<MenuActions> actions) {
        var items = model.page().items();
        if (items.isEmpty()) return MenuText.create().text("  <inactive><i18n:company.applications.none>");
        var text = MenuText.create();
        for (var i = 0; i < items.size(); i++) {
            if (i > 0) text.newLine();
            text.append(entry(model.overview(), i, items.get(i), actions));
        }
        return text;
    }

    private MenuText entry(
            ApplicationOverview overview, int index, Application application, Optional<MenuActions> actions) {
        var name = Keys.of("application", index, "player");
        var age = Keys.of("application", index, "age");
        var left = Keys.of("application", index, "left");
        var message = Keys.of("application", index, "message");
        var text = MenuText.create()
                .text(
                        "  <value><" + name + "> <neutral>(<i18n:company.applications.age> <" + age
                                + ">, <i18n:company.invites.expiresIn> <" + left + ">)",
                        Values.text(name, overview.name(application.applicant())),
                        Values.text(age, TimeSpans.between(application.created(), overview.now())),
                        Values.text(left, TimeSpans.between(overview.now(), application.expires())));
        actions.ifPresent(bound -> text.space()
                .append(button(
                        bound,
                        Keys.of("act_accept", index),
                        "<add>[<i18n:words.accept>]",
                        player -> handlers.accept(player, application)))
                .space()
                .append(button(
                        bound,
                        Keys.of("act_deny", index),
                        "<remove>[<i18n:words.deny>]",
                        player -> handlers.deny(player, application))));
        return text.newLine().text("    <gray><" + message + ">", Values.text(message, application.message()));
    }

    private static MenuText title(Model model) {
        return MenuText.create()
                .text(
                        "<heading><i18n:company.applications.title> <value><company_name>",
                        Values.text("company_name", model.overview().companyName()));
    }

    private static MenuText button(MenuActions actions, String key, String label, Consumer<Player> handler) {
        return MenuText.create().text("<" + key + ">" + label + "</" + key + ">", actions.chatActionOnce(key, handler));
    }

    private static Component label(SearchTexts texts, String action, String subject) {
        return texts.of(MenuText.create().text(action + " <value><subject>", Values.text("subject", subject)));
    }

    private Consumer<Player> previous(Model model) {
        return viewer -> menus.show(viewer, this, model.page(model.number() - 1));
    }

    private Consumer<Player> next(Model model) {
        return viewer -> menus.show(viewer, this, model.page(model.number() + 1));
    }

    /**
     * A page of the applications.
     *
     * @param overview the applications
     * @param number   index of the shown page, from zero
     */
    public record Model(ApplicationOverview overview, int number) {
        /**
         * @param overview the applications
         * @return the first page
         */
        public static Model of(ApplicationOverview overview) {
            return new Model(overview, 0);
        }

        /**
         * @return the shown page
         */
        public Page<Application> page() {
            return Page.of(overview.applications(), number, PAGE_SIZE);
        }

        /**
         * @param requested index of the page asked for, from zero
         * @return the same applications on another page
         */
        public Model page(int requested) {
            return new Model(
                    overview,
                    Page.of(overview.applications(), requested, PAGE_SIZE).number());
        }
    }

    /**
     * What the buttons of the menu do.
     */
    public interface Handlers {
        /**
         * @param player      member who clicked
         * @param application application to accept
         */
        void accept(Player player, Application application);

        /**
         * @param player      member who clicked
         * @param application application to deny
         */
        void deny(Player player, Application application);
    }
}
