/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */

/** The translation functions a label needs: whether a key exists, and its text. */
export interface Translator {
    t: (key: string, values?: Record<string, unknown>) => string
    te: (key: string) => boolean
}

/**
 * The label of a value from an open set, such as a ranking or a notification type: its translation where the
 * dashboard knows the value, the value itself where it is newer than the dashboard.
 *
 * @param translator the translation functions
 * @param key the key the value is translated under
 * @param value the value
 * @returns the label
 */
export function openLabel(translator: Translator, key: string, value: string): string {
    return translator.te(key) ? translator.t(key) : value
}
