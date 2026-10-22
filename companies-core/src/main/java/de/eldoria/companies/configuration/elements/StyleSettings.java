/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The look of every message: the prefix in MiniMessage format and the colours of the message tags.
 * <p>
 * A colour is a hex value such as {@code #f05316} or a named colour such as {@code dark_aqua}. A value that is
 * neither falls back to the default colour of that tag.
 */
@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "RedundantNoArgConstructor", "CanBeFinal"})
public class StyleSettings {
    private static final Pattern HEX = Pattern.compile("#[0-9a-fA-F]{6}");
    private static final StyleSettings DEFAULTS = new StyleSettings();
    private String prefix = "<#00a6ff>[C]";
    private String message = "dark_aqua";
    private String error = "#f05316";
    private String heading = "gold";
    private String name = "aqua";
    private String value = "dark_green";
    private String remove = "red";
    private String add = "green";
    private String modify = "yellow";
    private String show = "green";
    private String inactive = "dark_gray";
    private String active = "green";
    private String neutral = "dark_aqua";

    public StyleSettings() {}

    /**
     * The prefix put in front of every message.
     *
     * @return prefix in MiniMessage format
     */
    public String prefix() {
        return prefix;
    }

    /**
     * The colour of normal messages.
     *
     * @return message colour
     */
    public TextColor message() {
        return color(message, DEFAULTS.message);
    }

    /**
     * The colour of error messages.
     *
     * @return error colour
     */
    public TextColor error() {
        return color(error, DEFAULTS.error);
    }

    /**
     * The colours of the tags usable in messages, such as {@code <heading>} or {@code <value>}.
     *
     * @return colour per tag name
     */
    public Map<String, TextColor> tags() {
        return Map.of(
                "heading", color(heading, DEFAULTS.heading),
                "name", color(name, DEFAULTS.name),
                "value", color(value, DEFAULTS.value),
                "remove", color(remove, DEFAULTS.remove),
                "add", color(add, DEFAULTS.add),
                "modify", color(modify, DEFAULTS.modify),
                "show", color(show, DEFAULTS.show),
                "inactive", color(inactive, DEFAULTS.inactive),
                "active", color(active, DEFAULTS.active),
                "neutral", color(neutral, DEFAULTS.neutral));
    }

    private static TextColor color(String value, String fallback) {
        return parse(value).orElseGet(() -> parse(fallback).orElseThrow());
    }

    private static Optional<TextColor> parse(String value) {
        if (value == null) return Optional.empty();
        var trimmed = value.trim();
        if (HEX.matcher(trimmed).matches()) return Optional.ofNullable(TextColor.fromHexString(trimmed));
        return Optional.ofNullable(NamedTextColor.NAMES.value(trimmed.toLowerCase(Locale.ROOT)));
    }
}
