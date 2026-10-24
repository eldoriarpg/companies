/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services;

import de.eldoria.companies.commands.support.CommandFailure;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/**
 * The result of an action a player takes, wherever it was started from.
 * <p>
 * A performed action carries what the caller needs to tell the player about it; the services never send messages
 * themselves. A refused action changed nothing and carries the error the player sees, with its locale key naming the
 * reason.
 *
 * @param <T> type of the value of a performed action
 */
public sealed interface ActionResult<T> {
    /**
     * A performed action.
     *
     * @param value what the action returns
     * @param <T>   type of the value
     * @return a performed action
     */
    static <T> ActionResult<T> done(T value) {
        return new Done<>(value);
    }

    /**
     * A refused action.
     *
     * @param failure the error the player sees
     * @param <T>     type of the value of a performed action
     * @return a refused action
     */
    static <T> ActionResult<T> refused(CommandFailure failure) {
        return new Refused<>(failure);
    }

    /**
     * A refused action whose error a player sees in the action bar.
     *
     * @param localeKey locale key of the error
     * @param values    values of the placeholders in the error
     * @param <T>       type of the value of a performed action
     * @return a refused action
     */
    static <T> ActionResult<T> refusedActionBar(String localeKey, TagResolver... values) {
        return refused(CommandFailure.actionBar(localeKey, values));
    }

    /**
     * A refused action whose error a player sees in the chat.
     *
     * @param localeKey locale key of the error
     * @param values    values of the placeholders in the error
     * @param <T>       type of the value of a performed action
     * @return a refused action
     */
    static <T> ActionResult<T> refusedChat(String localeKey, TagResolver... values) {
        return refused(CommandFailure.of(localeKey, values));
    }

    /**
     * A performed action.
     *
     * @param value what the action returns
     * @param <T>   type of the value
     */
    record Done<T>(T value) implements ActionResult<T> {}

    /**
     * A refused action, which changed nothing.
     *
     * @param failure the error the player sees
     * @param <T>     type of the value of a performed action
     */
    record Refused<T>(CommandFailure failure) implements ActionResult<T> {}
}
