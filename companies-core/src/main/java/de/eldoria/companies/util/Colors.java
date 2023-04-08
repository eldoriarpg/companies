/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.util;

public final class Colors {
    private Colors() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    public static String active(boolean active) {
        return active ? "active" : "inactive";
    }
}
