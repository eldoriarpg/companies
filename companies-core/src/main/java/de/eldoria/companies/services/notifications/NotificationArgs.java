/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services.notifications;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.eldoria.companies.menus.Values;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The named values a notification message shows, in insertion order. Every value is inserted as plain text, so player
 * input cannot add formatting.
 * <p>
 * Stored as a JSON object of {@code {"value": …, "kind": …}} per name. A plain string value is read as text, which is
 * how notifications stored before the inbox existed keep their serialized message.
 */
public final class NotificationArgs {
    /**
     * The argument holding the serialized message of a {@link NotificationType#LEGACY} notification.
     */
    public static final String LEGACY_MESSAGE = "message";

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
     * Reads stored arguments. Values of an unknown kind are read as text; entries that are neither a string nor a
     * value object are skipped.
     *
     * @param json stored arguments
     * @return arguments
     * @throws IllegalArgumentException when the text is not a JSON object
     */
    public static NotificationArgs fromJson(String json) {
        JsonElement root;
        try {
            root = JsonParser.parseString(json);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Notification arguments are not JSON: " + json, e);
        }
        if (!root.isJsonObject()) throw new IllegalArgumentException("Notification arguments are no object: " + json);
        var result = create();
        for (var entry : root.getAsJsonObject().entrySet()) {
            readArg(entry.getValue()).ifPresent(arg -> result.args.put(entry.getKey(), arg));
        }
        return result;
    }

    private static Optional<Arg> readArg(JsonElement element) {
        if (element.isJsonPrimitive()) return Optional.of(new Arg(element.getAsString(), Kind.TEXT));
        if (!element.isJsonObject()) return Optional.empty();
        var object = element.getAsJsonObject();
        var value = object.get("value");
        if (value == null || !value.isJsonPrimitive()) return Optional.empty();
        var kind = object.get("kind");
        return Optional.of(new Arg(value.getAsString(), kind == null ? Kind.TEXT : Kind.parse(kind)));
    }

    /**
     * Writes the arguments for storage.
     *
     * @return JSON object
     */
    public String toJson() {
        var root = new JsonObject();
        for (var entry : args.entrySet()) {
            var value = new JsonObject();
            value.addProperty("value", entry.getValue().value());
            value.addProperty("kind", entry.getValue().kind().name());
            root.add(entry.getKey(), value);
        }
        return root.toString();
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
     * Placeholders inserting every argument as plain text.
     *
     * @return one placeholder per argument
     */
    public TagResolver.Single[] resolvers() {
        return args.entrySet().stream()
                .map(entry -> Values.text(entry.getKey(), entry.getValue().value()))
                .toArray(TagResolver.Single[]::new);
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
        NUMBER;

        private static Kind parse(JsonElement element) {
            if (!element.isJsonPrimitive()) return TEXT;
            try {
                return valueOf(element.getAsString().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return TEXT;
            }
        }
    }

    /**
     * One argument.
     *
     * @param value value as text
     * @param kind  kind of value
     */
    public record Arg(String value, Kind kind) {}
}
