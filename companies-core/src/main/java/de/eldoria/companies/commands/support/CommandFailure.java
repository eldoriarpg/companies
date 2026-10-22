/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.support;

import de.eldoria.eldoutilities.messages.MessageSender;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * A command that can not go on. It carries the locale key of the error the sender sees.
 * <p>
 * Thrown in the synchronous part of an executor and turned into a message by {@link CommandFailures}. Failures in
 * asynchronous callbacks are still sent by hand.
 */
public final class CommandFailure extends RuntimeException {
    private final String localeKey;
    private final boolean actionBar;
    private final transient TagResolver[] values;

    private CommandFailure(String localeKey, boolean actionBar, TagResolver... values) {
        super(localeKey, null, false, false);
        this.localeKey = localeKey;
        this.actionBar = actionBar;
        this.values = values.clone();
    }

    /**
     * Creates a failure shown in the chat.
     *
     * @param localeKey locale key of the error
     * @param values    values of the placeholders in the error
     * @return the failure
     */
    public static CommandFailure of(String localeKey, TagResolver... values) {
        return new CommandFailure(localeKey, false, values);
    }

    /**
     * Creates a failure shown in the action bar of a player, or in the chat of any other sender.
     *
     * @param localeKey locale key of the error
     * @param values    values of the placeholders in the error
     * @return the failure
     */
    public static CommandFailure actionBar(String localeKey, TagResolver... values) {
        return new CommandFailure(localeKey, true, values);
    }

    /**
     * @return locale key of the error
     */
    public String localeKey() {
        return localeKey;
    }

    /**
     * @return whether a player sees the error in the action bar
     */
    public boolean isActionBar() {
        return actionBar;
    }

    /**
     * @return values of the placeholders in the error
     */
    public TagResolver[] values() {
        return values.clone();
    }

    /**
     * Sends the error to the sender of the failed command.
     *
     * @param sender   sender of the command
     * @param messages message sender of the plugin
     */
    public void sendTo(CommandSender sender, MessageSender messages) {
        if (actionBar && sender instanceof Player player) {
            messages.sendErrorActionBar(player, localeKey, values);
            return;
        }
        messages.sendError(sender, localeKey, values);
    }
}
