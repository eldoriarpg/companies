/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MenuModeTest {
    private static final int OLD_CLIENT = 770;
    private static final int UNKNOWN_CLIENT = -1;

    @Test
    void dialogWhenConfiguredSupportedAndProvided() {
        assertEquals(MenuMode.DIALOG, MenuMode.select(MenuMode.DIALOG, MenuMode.DIALOG_PROTOCOL, true));
        assertEquals(MenuMode.DIALOG, MenuMode.select(MenuMode.DIALOG, 774, true));
    }

    @Test
    void chatWhenConfigured() {
        assertEquals(MenuMode.CHAT, MenuMode.select(MenuMode.CHAT, MenuMode.DIALOG_PROTOCOL, true));
    }

    @Test
    void chatWhenTheMenuHasNoDialog() {
        assertEquals(MenuMode.CHAT, MenuMode.select(MenuMode.DIALOG, MenuMode.DIALOG_PROTOCOL, false));
    }

    @Test
    void chatForClientsWithoutDialogs() {
        assertEquals(MenuMode.CHAT, MenuMode.select(MenuMode.DIALOG, OLD_CLIENT, true));
        assertEquals(MenuMode.CHAT, MenuMode.select(MenuMode.DIALOG, UNKNOWN_CLIENT, true));
    }

    @Test
    void dialogsStartWithMinecraft1216() {
        assertEquals(771, MenuMode.DIALOG_PROTOCOL);
    }
}
