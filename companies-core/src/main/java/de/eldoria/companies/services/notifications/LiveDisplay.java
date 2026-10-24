/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services.notifications;

import de.eldoria.companies.configuration.elements.NotificationSettings;
import de.eldoria.eldoutilities.messages.MessageSender;
import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Shows a notification to a player who is online on this server. Called on the main thread.
 */
@FunctionalInterface
public interface LiveDisplay {
    /**
     * @param player player to show the notification to
     * @param type   kind of notification
     * @param args   values of the notification
     */
    void show(Player player, NotificationType type, NotificationArgs args);

    /**
     * The display of the server: every notification in chat with its button; important ones also on the action bar
     * and with the configured sound. Paper has no toast without a temporary advancement, so the action bar takes its
     * place. A sound key that cannot be parsed is reported once and then ignored.
     *
     * @param messages renders and sends the texts
     * @param renderer builds the texts
     * @param settings current notification settings
     * @param logger   reports a broken sound key
     * @return the display
     */
    static LiveDisplay paper(
            MessageSender messages,
            NotificationRenderer renderer,
            Supplier<NotificationSettings> settings,
            Logger logger) {
        var reported = new AtomicBoolean();
        return (player, type, args) -> {
            messages.sendMessage(player, renderer.message(type, args));
            if (!type.important()) return;
            var current = settings.get();
            if (current.actionBar()) {
                var body = renderer.body(type, args);
                messages.sendActionBar(player, body.build(), body.replacements().toArray(TagResolver[]::new));
            }
            current.sound().ifPresent(sound -> {
                try {
                    player.playSound(Sound.sound(Key.key(sound), Sound.Source.MASTER, 1f, 1f));
                } catch (InvalidKeyException e) {
                    if (!reported.getAndSet(true)) logger.warning("The notification sound is no valid key: " + sound);
                }
            });
        };
    }
}
