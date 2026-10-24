/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import de.eldoria.companies.menus.MenuMode;

/**
 * How menus are shown, written as a single value: {@code menus: CHAT} or {@code menus: DIALOG}.
 * <p>
 * New installations show dialogs, see {@link de.eldoria.companies.configuration.ConfigFile#fresh()}. A missing or
 * empty value means chat. Clients older than 1.21.6 always see chat menus.
 */
public final class MenuSettings {
    private final MenuMode mode;

    public MenuSettings() {
        this(MenuMode.CHAT);
    }

    /**
     * @param mode how menus are shown; null means chat
     */
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public MenuSettings(MenuMode mode) {
        this.mode = mode == null ? MenuMode.CHAT : mode;
    }

    /**
     * @return how menus are shown
     */
    @JsonValue
    public MenuMode mode() {
        return mode;
    }
}
