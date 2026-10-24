/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.company;

import de.eldoria.companies.menus.Keys;
import de.eldoria.companies.menus.Menu;
import de.eldoria.companies.menus.MenuActions;
import de.eldoria.companies.menus.MenuComponents;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.services.notifications.NotificationCategory;
import de.eldoria.eldoutilities.messages.MessageSender;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The mute settings of a player: one switch per category that can be muted. A muted category still arrives in the
 * inbox but is never shown live. A switch changes the setting at once; the handler shows the menu again.
 */
public final class InboxSettingsMenu implements Menu<InboxSettingsMenu.Model> {
    private final MessageSender messages;
    private final Handlers handlers;

    /**
     * @param messages renders the dialog texts
     * @param handlers what the buttons do
     */
    public InboxSettingsMenu(MessageSender messages, Handlers handlers) {
        this.messages = messages;
        this.handlers = handlers;
    }

    @Override
    public MenuText chat(Player player, Model model, MenuActions actions) {
        var text = header();
        for (var category : NotificationCategory.mutables()) {
            var key = Keys.of("toggle", category.name().toLowerCase(Locale.ROOT));
            text.newLine()
                    .text("  <name><i18n:" + category.key() + ">: ")
                    .text(
                            "<" + key + "><u>" + state(model, category) + "</u></" + key + ">",
                            actions.chatAction(key, clicker -> handlers.toggle(clicker, category)));
        }
        return text.newLine()
                .text("<act_back><show>[<i18n:words.back>]</act_back>", actions.chatAction("act_back", handlers::back));
    }

    @Override
    public Optional<Dialog> dialog(Player player, Model model, MenuActions actions) {
        var components = new MenuComponents(messages, player);
        List<ActionButton> buttons = new ArrayList<>();
        for (var category : NotificationCategory.mutables()) {
            var label = MenuText.create().text("<i18n:" + category.key() + ">: " + state(model, category));
            buttons.add(actions.button(
                    components.of(label), null, (clicker, response) -> handlers.toggle(clicker, category)));
        }
        buttons.add(
                actions.button(components.locale("words.back"), null, (clicker, response) -> handlers.back(clicker)));
        var base = DialogBase.builder(components.locale("inbox.settings"))
                .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                .body(List.of(DialogBody.plainMessage(components.locale("inbox.settingsHint"))))
                .build();
        var type = DialogType.multiAction(buttons).columns(2).build();
        return Optional.of(Dialog.create(factory -> factory.empty().base(base).type(type)));
    }

    private static MenuText header() {
        return MenuText.create()
                .text("<heading><i18n:inbox.settings>")
                .newLine()
                .text("<inactive><i18n:inbox.settingsHint>");
    }

    private static String state(Model model, NotificationCategory category) {
        return model.muted(category) ? "<inactive><i18n:words.off></inactive>" : "<active><i18n:words.on></active>";
    }

    /**
     * The mute settings shown.
     *
     * @param mask categories the player muted
     */
    public record Model(long mask) {
        /**
         * @param category a category
         * @return true when the player muted it
         */
        public boolean muted(NotificationCategory category) {
            return category.mutedBy(mask);
        }
    }

    /**
     * What the buttons of the menu do.
     */
    public interface Handlers {
        /**
         * @param player   player whose setting changes
         * @param category category to mute or unmute
         */
        void toggle(Player player, NotificationCategory category);

        /**
         * @param player player going back to the inbox
         */
        void back(Player player);
    }
}
