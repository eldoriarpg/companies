/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.util;

public class Colors {
    public static final String HEADING = "gold";
    public static final String NAME = "aqua";
    public static final String VALUE = "dark_green";
    public static final String REMOVE = "red";
    public static final String ADD = "green";
    public static final String MODIFY = "yellow";
    public static final String SHOW = ADD;
    public static final String INACTIVE = "dark_gray";
    public static final String ACTIVE = "green";
    public static final String NEUTRAL = "dark_aqua";

    public static String active(boolean active) {
        return active ? Colors.ACTIVE : Colors.INACTIVE;
    }
}
