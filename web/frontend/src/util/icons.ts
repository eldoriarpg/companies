/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */

/**
 * The icons of the dashboard as stroked 24 by 24 paths, drawn in the current text colour. Every icon the app shows is
 * here, so a name that is not cannot be drawn and the type says so.
 */
export const ICONS = {
    portfolio: 'M3 17l5-5 4 4 8-8M15 8h5v5',
    orders: 'M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01',
    market: 'M4 20V10M10 20V4M16 20v-8M22 20H2',
    company: 'M4 21V7l8-4 8 4v14M9 21v-6h6v6',
    inbox: 'M3 13h5l2 3h4l2-3h5M5 5h14l2 8v6H3v-6z',
    rankings: 'M8 21h8M12 17v4M7 4h10v5a5 5 0 0 1-10 0zM7 6H4v2a3 3 0 0 0 3 3M17 6h3v2a3 3 0 0 1-3 3',
    menu: 'M4 6h16M4 12h16M4 18h16',
    close: 'M6 6l12 12M18 6L6 18',
    back: 'M15 18l-6-6 6-6',
    search: 'M11 18a7 7 0 1 0 0-14 7 7 0 0 0 0 14zM21 21l-5-5',
    logout: 'M9 21H5V3h4M16 17l5-5-5-5M21 12H9',
    screen: 'M7 2h10v20H7zM11 18h2',
    warning: 'M12 3l10 18H2zM12 10v5M12 18h.01',
    check: 'M4 12l5 5L20 6',
    info: 'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM12 11v6M12 7h.01',
    chevronDown: 'M6 9l6 6 6-6',
    plus: 'M12 5v14M5 12h14',
    admin: 'M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6z',
    pencil: 'M4 20h4L19 9l-4-4L4 16zM13 7l4 4',
    trash: 'M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13',
} as const

/** The name of an icon. */
export type IconName = keyof typeof ICONS
