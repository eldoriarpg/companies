/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.company;

import de.eldoria.companies.data.wrapper.company.Application;
import de.eldoria.companies.data.wrapper.company.Invite;
import de.eldoria.companies.menus.Keys;
import de.eldoria.companies.menus.Menu;
import de.eldoria.companies.menus.MenuActions;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.services.MembershipService.InviteOverview;
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
import java.util.function.Consumer;

/**
 * The invites of a player: those the player received, with buttons to accept or deny them, the player's own
 * applications, which can be withdrawn, and, for members who may invite, the invites their company sent, which can be
 * revoked.
 * <p>
 * In chat each invite is one line with its buttons; as a dialog the list is the body and every action is a button.
 * Every button works once; the handlers show the menu again with the new state.
 */
public final class InvitesMenu implements Menu<InviteOverview> {
    private static final String NONE = "  <inactive><i18n:company.invites.none>";

    private final MessageSender messages;
    private final Handlers handlers;

    /**
     * @param messages renders the dialog texts
     * @param handlers what the buttons do
     */
    public InvitesMenu(MessageSender messages, Handlers handlers) {
        this.messages = messages;
        this.handlers = handlers;
    }

    @Override
    public MenuText chat(Player player, InviteOverview model, MenuActions actions) {
        return MenuText.create()
                .text("<heading><i18n:company.invites.title>")
                .newLine()
                .append(entries(model, Optional.of(actions)));
    }

    @Override
    public Optional<Dialog> dialog(Player player, InviteOverview model, MenuActions actions) {
        var texts = new SearchTexts(messages, player);
        List<ActionButton> buttons = new ArrayList<>();
        for (var invite : model.received()) {
            buttons.add(actions.buttonOnce(
                    label(texts, "<add><i18n:words.accept>", invite.companyName()),
                    null,
                    (clicker, response) -> handlers.accept(clicker, invite)));
            buttons.add(actions.buttonOnce(
                    label(texts, "<remove><i18n:words.deny>", invite.companyName()),
                    null,
                    (clicker, response) -> handlers.deny(clicker, invite)));
        }
        for (var invite : model.sent()) {
            buttons.add(actions.buttonOnce(
                    label(texts, "<remove><i18n:words.revoke>", model.name(invite.target())),
                    null,
                    (clicker, response) -> handlers.revoke(clicker, invite)));
        }
        for (var application : model.applications()) {
            buttons.add(actions.buttonOnce(
                    label(texts, "<remove><i18n:words.withdraw>", application.companyName()),
                    null,
                    (clicker, response) -> handlers.withdraw(clicker, application)));
        }
        var base = DialogBase.builder(texts.locale("company.invites.title"))
                .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                .body(List.of(DialogBody.plainMessage(texts.of(entries(model, Optional.empty())))))
                .build();
        var type = buttons.isEmpty()
                ? DialogType.notice()
                : DialogType.multiAction(buttons).columns(2).build();
        return Optional.of(Dialog.create(factory -> factory.empty().base(base).type(type)));
    }

    /**
     * The sections of the menu, with chat buttons when actions are given.
     *
     * @param model   the invites
     * @param actions binds the chat buttons, empty for the dialog body
     * @return the sections
     */
    MenuText entries(InviteOverview model, Optional<MenuActions> actions) {
        var text = MenuText.create().text("<name><i18n:company.invites.received>:");
        if (model.received().isEmpty()) text.newLine().text(NONE);
        for (var i = 0; i < model.received().size(); i++) {
            text.newLine().append(received(model, i, actions));
        }
        if (!model.sent().isEmpty()) {
            text.newLine().text("<name><i18n:company.invites.sent>:");
            for (var i = 0; i < model.sent().size(); i++) {
                text.newLine().append(sent(model, i, actions));
            }
        }
        if (!model.applications().isEmpty()) {
            text.newLine().text("<name><i18n:company.invites.applications>:");
            for (var i = 0; i < model.applications().size(); i++) {
                text.newLine().append(application(model, i, actions));
            }
        }
        return text;
    }

    private MenuText received(InviteOverview model, int index, Optional<MenuActions> actions) {
        var invite = model.received().get(index);
        var company = Keys.of("received", index, "company");
        var inviter = Keys.of("received", index, "inviter");
        var text = MenuText.create()
                .text(
                        "  <value><" + company + "> <neutral>(<i18n:company.invites.from> <" + inviter + ">, ",
                        Values.text(company, invite.companyName()),
                        Values.text(inviter, model.name(invite.inviter())))
                .append(left("received", index, model.now(), invite.expires()))
                .text(")");
        actions.ifPresent(bound -> text.space()
                .append(button(
                        bound,
                        Keys.of("act_accept", index),
                        "<add>[<i18n:words.accept>]",
                        player -> handlers.accept(player, invite)))
                .space()
                .append(button(
                        bound,
                        Keys.of("act_deny", index),
                        "<remove>[<i18n:words.deny>]",
                        player -> handlers.deny(player, invite))));
        return text;
    }

    private MenuText sent(InviteOverview model, int index, Optional<MenuActions> actions) {
        var invite = model.sent().get(index);
        var target = Keys.of("sent", index, "player");
        var text = MenuText.create()
                .text("  <value><" + target + "> <neutral>(", Values.text(target, model.name(invite.target())))
                .append(left("sent", index, model.now(), invite.expires()))
                .text(")");
        actions.ifPresent(bound -> text.space()
                .append(button(
                        bound,
                        Keys.of("act_revoke", index),
                        "<remove>[<i18n:words.revoke>]",
                        player -> handlers.revoke(player, invite))));
        return text;
    }

    private MenuText application(InviteOverview model, int index, Optional<MenuActions> actions) {
        var application = model.applications().get(index);
        var company = Keys.of("application", index, "company");
        var text = MenuText.create()
                .text("  <value><" + company + "> <neutral>(", Values.text(company, application.companyName()))
                .append(left("application", index, model.now(), application.expires()))
                .text(")");
        actions.ifPresent(bound -> text.space()
                .append(button(
                        bound,
                        Keys.of("act_withdraw", index),
                        "<remove>[<i18n:words.withdraw>]",
                        player -> handlers.withdraw(player, application))));
        return text;
    }

    private static MenuText left(String row, int index, LocalDateTime now, LocalDateTime expires) {
        var key = Keys.of(row, index, "left");
        return MenuText.create()
                .text(
                        "<i18n:company.invites.expiresIn> <" + key + ">",
                        Values.text(key, TimeSpans.between(now, expires)));
    }

    private static MenuText button(MenuActions actions, String key, String label, Consumer<Player> handler) {
        return MenuText.create().text("<" + key + ">" + label + "</" + key + ">", actions.chatActionOnce(key, handler));
    }

    private static Component label(SearchTexts texts, String action, String subject) {
        return texts.of(MenuText.create().text(action + " <value><subject>", Values.text("subject", subject)));
    }

    /**
     * What the buttons of the menu do.
     */
    public interface Handlers {
        /**
         * @param player player who clicked
         * @param invite invite to accept
         */
        void accept(Player player, Invite invite);

        /**
         * @param player player who clicked
         * @param invite invite to decline
         */
        void deny(Player player, Invite invite);

        /**
         * @param player player who clicked
         * @param invite invite of the player's company to take back
         */
        void revoke(Player player, Invite invite);

        /**
         * @param player      player who clicked
         * @param application own application to take back
         */
        void withdraw(Player player, Application application);
    }
}
