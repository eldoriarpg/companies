/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus;

import de.eldoria.companies.configuration.elements.StyleSettings;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Names of the placeholders in menu texts.
 * <p>
 * A key is {@code snake_case}. It may not be the name of a tag every message can use: the style tags such as
 * {@code <name>} or {@code <value>}, {@code <prefix>}, {@code <default>}, {@code <i18n>} and the standard MiniMessage
 * tags such as {@code <red>} or {@code <click>}. A placeholder with such a name would shadow the tag, so order names
 * use {@code order_name} instead of {@code name}.
 */
public final class Keys {
    private static final Pattern SNAKE_CASE = Pattern.compile("[a-z][a-z0-9]*(_[a-z0-9]+)*");
    private static final Set<String> RESERVED = reserved();

    private Keys() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    /**
     * Builds an indexed key for repeated rows, such as {@code line_2_item} from {@code ("line", 2, "item")}.
     *
     * @param parts parts of the key, joined by underscores
     * @return the checked key
     * @throws IllegalArgumentException when the joined key is not a valid key
     */
    public static String of(Object... parts) {
        return require(Arrays.stream(parts).map(String::valueOf).collect(Collectors.joining("_")));
    }

    /**
     * Checks a key.
     *
     * @param key key of a placeholder
     * @return the key
     * @throws IllegalArgumentException when the key is not {@code snake_case} or is a reserved tag name
     */
    public static String require(String key) {
        if (!SNAKE_CASE.matcher(key).matches()) {
            throw new IllegalArgumentException("Placeholder key is not snake_case: " + key);
        }
        if (isReserved(key)) {
            throw new IllegalArgumentException("Placeholder key shadows a message tag: " + key);
        }
        return key;
    }

    /**
     * Whether a name belongs to a tag every message can use.
     *
     * @param key name to check
     * @return true when a placeholder of this name would shadow a tag
     */
    public static boolean isReserved(String key) {
        var name = key.toLowerCase(Locale.ROOT);
        return RESERVED.contains(name) || StandardTags.defaults().has(name);
    }

    private static Set<String> reserved() {
        var names = new HashSet<>(new StyleSettings().tags().keySet());
        names.addAll(Set.of("prefix", "default", "i18n"));
        return Set.copyOf(names);
    }
}
