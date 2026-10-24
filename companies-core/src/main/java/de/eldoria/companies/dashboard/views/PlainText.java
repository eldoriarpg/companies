/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard.views;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Turns the plugin's texts into what the dashboard shows: plain text, never MiniMessage or components.
 */
public final class PlainText {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private PlainText() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    /**
     * Renders a component as plain text.
     *
     * @param component the component
     * @return its text
     */
    public static String of(Component component) {
        return PLAIN.serialize(component);
    }

    /**
     * Renders MiniMessage text as plain text, dropping every tag.
     *
     * @param miniMessage the text
     * @return its plain text
     */
    public static String ofMiniMessage(String miniMessage) {
        return PLAIN.serialize(MiniMessage.miniMessage().deserialize(miniMessage));
    }

    /**
     * The English name of a material, e.g. {@code Spruce Planks} for {@code SPRUCE_PLANKS}.
     *
     * @param material the material
     * @return the name
     */
    public static String materialName(Material material) {
        var words = material.name().toLowerCase(Locale.ROOT).split("_");
        var name = new StringBuilder();
        for (var word : words) {
            if (word.isEmpty()) continue;
            if (!name.isEmpty()) name.append(' ');
            name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return name.toString();
    }

    /**
     * A company colour as hex.
     *
     * @param color the name of a named text colour or a {@code #rrggbb} value
     * @return the colour as {@code #rrggbb}, none when it is no colour
     */
    public static @Nullable String hexColor(@Nullable String color) {
        if (color == null || color.isBlank()) return null;
        var named = NamedTextColor.NAMES.value(color.toLowerCase(Locale.ROOT));
        if (named != null) return named.asHexString();
        var parsed = TextColor.fromHexString(color);
        return parsed == null ? null : parsed.asHexString();
    }
}
