/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.integrations.discord;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Text helpers for Discord messages.
 */
public final class DiscordText {
    private static final Pattern MARKDOWN = Pattern.compile("([\\\\*_~`|>#<\\[\\]():-])");
    private static final Pattern LINE_BREAK = Pattern.compile("\\R");
    private static final String ELLIPSIS = "…";

    private DiscordText() {}

    /**
     * Escapes Markdown, mention and link characters and joins lines, so player text is shown as written.
     *
     * @param text text written by a player
     * @return the escaped text on one line
     */
    public static String escape(String text) {
        var oneLine = LINE_BREAK.matcher(text).replaceAll(" ");
        return MARKDOWN.matcher(oneLine).replaceAll("\\\\$1");
    }

    /**
     * Cuts the text to the limit, ending in an ellipsis when cut. An escape is never cut in half.
     *
     * @param text  the text
     * @param limit the most characters allowed
     * @return the text within the limit
     */
    public static String cut(String text, int limit) {
        if (text.length() <= limit) return text;
        var kept = text.substring(0, limit - ELLIPSIS.length());
        var backslashes = kept.length() - kept.replaceAll("\\\\+$", "").length();
        if (backslashes % 2 == 1) kept = kept.substring(0, kept.length() - 1);
        return kept + ELLIPSIS;
    }

    /**
     * The plain English name of a material key, such as {@code Iron Ingot} for {@code minecraft:iron_ingot}.
     *
     * @param key material key, with or without namespace
     * @return the name in title case
     */
    public static String itemName(String key) {
        var path = key.substring(key.indexOf(':') + 1).toLowerCase(Locale.ROOT);
        var name = new StringBuilder();
        for (var word : path.split("_")) {
            if (word.isEmpty()) continue;
            if (!name.isEmpty()) name.append(' ');
            name.append(Character.toUpperCase(word.charAt(0))).append(word, 1, word.length());
        }
        return name.toString();
    }
}
