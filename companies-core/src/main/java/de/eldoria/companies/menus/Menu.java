/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus;

import io.papermc.paper.dialog.Dialog;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Optional;

/**
 * A menu with a chat rendering and, optionally, a dialog rendering. {@link MenuService#show(Player, Menu, Object)}
 * chooses between them.
 * <p>
 * Both renderings bind their buttons through the same {@link MenuActions}, so a handler does not care where it was
 * clicked. The domain logic stays in services; a menu only renders a view model and calls them.
 *
 * @param <M> the view model, built by the caller
 */
public interface Menu<M> {
    /**
     * The chat rendering, used when no dialog can be shown.
     *
     * @param player  viewing player
     * @param model   what to show
     * @param actions binds the buttons
     * @return text of the menu
     */
    MenuText chat(Player player, M model, MenuActions actions);

    /**
     * The dialog rendering. Only asked for when the configuration and the client allow dialogs.
     *
     * @param player  viewing player
     * @param model   what to show
     * @param actions binds the buttons
     * @return the dialog, or empty when this menu has none
     */
    default Optional<Dialog> dialog(Player player, M model, MenuActions actions) {
        return Optional.empty();
    }

    /**
     * How long the buttons of one showing stay clickable. It matches the state the menu belongs to, such as a draft.
     *
     * @return lifetime of the buttons
     */
    default Duration lifetime() {
        return MenuActions.DEFAULT_LIFETIME;
    }

    /**
     * Whether the chat rendering holds the chat. A short prompt, such as a confirmation, is sent as one line instead,
     * so the chat stays usable when it is not answered.
     *
     * @return true when the chat is held while the menu is shown
     */
    default boolean holdsChat() {
        return true;
    }
}
