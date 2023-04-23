/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.util;

import de.eldoria.jacksonbukkit.util.Reflections;
import org.bukkit.Material;

public class Features {
    /**
     * Wheter the method {@link Material#getTranslationKey()} exists.
     * This is a spigot and paper method from 1.19.4 onwards
     */
    public static final boolean HAS_GET_TRANSLATION_KEY;
    /**
     * Whether the method {@link Material#translationKey()} exists.
     * This is a paper method from 1.17.1 onwards
     */
    public static final boolean HAS_TRANSLATION_KEY;

    static {
        HAS_GET_TRANSLATION_KEY = Reflections.methodExists(Material.class, "getTranslationKey");
        HAS_TRANSLATION_KEY = Reflections.methodExists(Material.class, "getTranslationKey");
    }
}
