/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus;

import de.eldoria.eldoutilities.messages.MessageSender;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;

/**
 * Renders menu texts to components for one player, with the plugin's styles and the player's language. Dialogs take
 * components where the chat takes MiniMessage.
 *
 * @param messages message sender of the plugin
 * @param player   viewing player
 */
public record MenuComponents(MessageSender messages, Player player) {
    /**
     * @param text text to render
     * @return the rendered text
     */
    public Component of(MenuText text) {
        return messages.serializeMessage(player, text.build(), resolvers(text));
    }

    /**
     * @param key    key of the locale text
     * @param values placeholders used by the locale text
     * @return the rendered locale text
     */
    public Component locale(String key, TagResolver.Single... values) {
        return of(MenuText.create().locale(key, values));
    }

    /**
     * @param text text to render as an error
     * @return the rendered text in the error style
     */
    public Component error(MenuText text) {
        return messages.serializeError(player, text.build(), resolvers(text));
    }

    private static TagResolver[] resolvers(MenuText text) {
        return text.replacements().toArray(TagResolver[]::new);
    }
}
