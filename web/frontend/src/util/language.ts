/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */

const LANGUAGE_KEY = 'companies.language'

/**
 * The language the reader picked on an earlier visit.
 *
 * @returns its code, or nothing without one or without storage
 */
export function rememberedLanguage(): string | null {
    try {
        return localStorage.getItem(LANGUAGE_KEY)
    } catch {
        return null
    }
}

/**
 * Remembers the language the reader picked.
 *
 * @param code its code
 */
export function rememberLanguage(code: string): void {
    try {
        localStorage.setItem(LANGUAGE_KEY, code)
    } catch {
        return
    }
}
