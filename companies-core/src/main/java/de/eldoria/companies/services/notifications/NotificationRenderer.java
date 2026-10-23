/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services.notifications;

import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import net.kyori.adventure.text.event.ClickEvent;

import java.util.function.UnaryOperator;

/**
 * Turns a notification into the text a player reads, live or in the inbox.
 * <p>
 * Rendering happens at display time with the current language, so a stored notification follows a later language
 * change. Arguments are inserted as plain text. A {@link NotificationType#LEGACY} notification was rendered before it
 * was stored and is shown as it was serialized.
 */
public final class NotificationRenderer {
    /**
     * The placeholder of the button next to a message.
     */
    static final String ACTION_KEY = "notification_action";

    private final UnaryOperator<String> localize;

    /**
     * @param localize resolves locale codes left in the serialized messages of legacy notifications
     */
    public NotificationRenderer(UnaryOperator<String> localize) {
        this.localize = localize;
    }

    /**
     * The message with the button of its type.
     *
     * @param type kind of notification
     * @param args values of the notification
     * @return the message
     */
    public MenuText message(NotificationType type, NotificationArgs args) {
        var text = body(type, args);
        type.action().ifPresent(action -> text.space()
                .text(
                        "<" + ACTION_KEY + "><add>[",
                        Values.click(ACTION_KEY, ClickEvent.runCommand(args.fill(action.command()))))
                .locale(action.label())
                .text("]</" + ACTION_KEY + ">"));
        return text;
    }

    /**
     * The message without a button, as shown on the action bar.
     *
     * @param type kind of notification
     * @param args values of the notification
     * @return the message
     */
    public MenuText body(NotificationType type, NotificationArgs args) {
        if (type == NotificationType.LEGACY) return legacy(args);
        return MenuText.create().text("<neutral>").locale(type.key(), args.resolvers());
    }

    private MenuText legacy(NotificationArgs args) {
        var message = args.values().get(NotificationArgs.LEGACY_MESSAGE);
        return MenuText.create().text(message == null ? "" : localize.apply(message.value()));
    }
}
