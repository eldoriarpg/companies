/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services.notifications;

import de.eldoria.eldoutilities.messages.Replacement;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The named values a notification message shows, in insertion order. Every value is inserted as plain text, so player
 * input cannot add formatting.
 */
public final class NotificationArgs {
    private final Map<String, Arg> args = new LinkedHashMap<>();

    private NotificationArgs() {}

    /**
     * Creates empty arguments.
     *
     * @return arguments
     */
    public static NotificationArgs create() {
        return new NotificationArgs();
    }

    /**
     * Adds a text, such as a name or player input.
     *
     * @param key   placeholder name
     * @param value text
     * @return these arguments
     */
    public NotificationArgs text(String key, String value) {
        args.put(key, new Arg(value, Kind.TEXT));
        return this;
    }

    /**
     * Adds an amount of money, already formatted by the economy.
     *
     * @param key       placeholder name
     * @param formatted formatted amount
     * @return these arguments
     */
    public NotificationArgs money(String key, String formatted) {
        args.put(key, new Arg(formatted, Kind.MONEY));
        return this;
    }

    /**
     * Adds a number.
     *
     * @param key   placeholder name
     * @param value number
     * @return these arguments
     */
    public NotificationArgs number(String key, long value) {
        args.put(key, new Arg(String.valueOf(value), Kind.NUMBER));
        return this;
    }

    /**
     * The arguments by placeholder name.
     *
     * @return unmodifiable view in insertion order
     */
    public Map<String, Arg> values() {
        return Collections.unmodifiableMap(args);
    }

    /**
     * Resolvers inserting every argument as plain text.
     *
     * @return one resolver per argument
     */
    public TagResolver[] resolvers() {
        return args.entrySet().stream()
                .map(entry ->
                        Replacement.unparsed(entry.getKey(), entry.getValue().value()))
                .toArray(TagResolver[]::new);
    }

    /**
     * Replaces the placeholders {@code <name>} of a command by the arguments.
     *
     * @param command command with placeholders
     * @return command with the values
     */
    public String fill(String command) {
        var result = command;
        for (var entry : args.entrySet()) {
            result = result.replace("<" + entry.getKey() + ">", entry.getValue().value());
        }
        return result;
    }

    /**
     * How a value is meant to be shown.
     */
    public enum Kind {
        TEXT,
        MONEY,
        NUMBER
    }

    /**
     * One argument.
     *
     * @param value value as text
     * @param kind  kind of value
     */
    public record Arg(String value, Kind kind) {}
}
