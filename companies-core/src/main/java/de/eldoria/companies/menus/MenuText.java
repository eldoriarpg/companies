/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus;

import de.eldoria.eldoutilities.localization.IMessageComposer;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.intellij.lang.annotations.Language;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The text of a menu: MiniMessage with named placeholders, built without {@link String#format(String, Object...)}.
 * <p>
 * Placeholders come from {@link Values} and {@link MenuActions}. Their keys are unique within one text, so a value
 * can never silently replace another one; a clash throws {@link IllegalArgumentException}. Locale texts are added as
 * {@code <i18n:key>} tags and may use the placeholders as well.
 */
public final class MenuText implements IMessageComposer {
    private final StringBuilder text = new StringBuilder();
    private final Map<String, TagResolver.Single> values = new LinkedHashMap<>();

    private MenuText() {}

    /**
     * @return an empty text
     */
    public static MenuText create() {
        return new MenuText();
    }

    /**
     * Adds MiniMessage text.
     *
     * @param miniMessage text, using the placeholders as {@code <key>}
     * @param values      placeholders used by the text
     * @return this text
     * @throws IllegalArgumentException when a key is already used in this text
     */
    public MenuText text(@Language("MiniMessage") String miniMessage, TagResolver.Single... values) {
        text.append(miniMessage);
        return add(values);
    }

    /**
     * Adds a locale text.
     *
     * @param key    key of the locale text
     * @param values placeholders used by the locale text
     * @return this text
     * @throws IllegalArgumentException when a key is already used in this text
     */
    public MenuText locale(String key, TagResolver.Single... values) {
        text.append("<i18n:").append(key).append('>');
        return add(values);
    }

    /**
     * @return this text, continued on a new line
     */
    public MenuText newLine() {
        text.append('\n');
        return this;
    }

    /**
     * @return this text, followed by a space
     */
    public MenuText space() {
        text.append(' ');
        return this;
    }

    /**
     * Appends another text with its placeholders.
     *
     * @param other text to append
     * @return this text
     * @throws IllegalArgumentException when both texts use the same key
     */
    public MenuText append(MenuText other) {
        text.append(other.text);
        return add(other.values.values().toArray(TagResolver.Single[]::new));
    }

    @Override
    public String build() {
        return text.toString();
    }

    @Override
    public List<TagResolver> replacements() {
        return List.copyOf(values.values());
    }

    private MenuText add(TagResolver.Single... added) {
        for (var value : added) {
            var key = Keys.require(value.key());
            if (values.putIfAbsent(key, value) != null) {
                throw new IllegalArgumentException("Placeholder key is used twice: " + key);
            }
        }
        return this;
    }
}
