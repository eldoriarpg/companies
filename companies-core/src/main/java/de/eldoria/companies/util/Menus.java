/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.util;

import de.eldoria.eldoutilities.messages.MessageSender;
import de.eldoria.eldoutilities.localization.IMessageComposer;
import de.eldoria.messageblocker.blocker.MessageBlocker;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;

/**
 * Sends chat menus past the message blocker, so a player whose chat is held still sees the menu they opened.
 */
public final class Menus {
    private Menus() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    /**
     * Renders a menu like a regular plugin message, prefix included, and sends it without it being held.
     *
     * @param blocker  message blocker of the plugin
     * @param player   player who opened the menu
     * @param composer menu content
     */
    public static void show(MessageBlocker blocker, Player player, IMessageComposer composer) {
        var sender = MessageSender.getPluginMessageSender(blocker.plugin());
        var content = sender.serializeMessage(player, composer.build(), composer.replacements().toArray(TagResolver[]::new));
        blocker.sendBypassing(player, sender.prefix().appendSpace().append(content));
    }
}
