/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus;

/**
 * How menus are shown: as dialogs, or as chat messages with clickable buttons.
 * <p>
 * A dialog is shown only when the configuration asks for dialogs, the menu has one and the client can show dialogs,
 * which needs Minecraft 1.21.6 (protocol {@value #DIALOG_PROTOCOL}) or newer. Clients joining through a protocol
 * translator report their own protocol, so older clients fall back to chat.
 */
public enum MenuMode {
    DIALOG,
    CHAT;

    /**
     * The protocol version of Minecraft 1.21.6, the first release with dialogs.
     */
    public static final int DIALOG_PROTOCOL = 771;

    /**
     * Whether a dialog may be shown at all, before asking the menu for one.
     *
     * @param configured      mode set in the configuration
     * @param protocolVersion protocol version of the client, -1 when unknown
     * @return true when the configuration asks for dialogs and the client can show them
     */
    public static boolean dialogsPossible(MenuMode configured, int protocolVersion) {
        return configured == DIALOG && protocolVersion >= DIALOG_PROTOCOL;
    }

    /**
     * Chooses the rendering of one menu for one client.
     *
     * @param configured      mode set in the configuration
     * @param protocolVersion protocol version of the client, -1 when unknown
     * @param menuHasDialog   whether the menu provides a dialog
     * @return {@link #DIALOG} when all three allow it, otherwise {@link #CHAT}
     */
    public static MenuMode select(MenuMode configured, int protocolVersion, boolean menuHasDialog) {
        return dialogsPossible(configured, protocolVersion) && menuHasDialog ? DIALOG : CHAT;
    }
}
