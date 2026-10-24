/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.api;

import java.util.Locale;

/**
 * The names enum constants carry on the wire.
 */
public final class Wire {
    private Wire() {}

    /**
     * Turns a constant such as {@code SELF_HOSTED} into its wire name {@code self-hosted}.
     *
     * @param constant the constant
     * @return its name in lower case with hyphens
     */
    public static String name(Enum<?> constant) {
        return constant.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
