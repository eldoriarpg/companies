/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.company;

import de.eldoria.companies.menus.MenuText;
import de.eldoria.eldoutilities.messages.MessageSender;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;

/**
 * Renders the texts of the search dialogs for one player, with the plugin's styles and the player's language.
 *
 * @param messages message sender of the plugin
 * @param player   viewing player
 */
record SearchTexts(MessageSender messages, Player player) {
    /**
     * @param text text to render
     * @return the rendered text
     */
    Component of(MenuText text) {
        return messages.serializeMessage(player, text.build(), resolvers(text));
    }

    /**
     * @param key key of the locale text
     * @return the rendered locale text
     */
    Component locale(String key) {
        return of(MenuText.create().locale(key));
    }

    /**
     * @param text text to render as an error
     * @return the rendered text in the error style
     */
    Component error(MenuText text) {
        return messages.serializeError(player, text.build(), resolvers(text));
    }

    private static TagResolver[] resolvers(MenuText text) {
        return text.replacements().toArray(TagResolver[]::new);
    }
}
