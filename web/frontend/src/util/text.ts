/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */

/**
 * Whether any of the texts holds every word of a search, regardless of case. An empty search matches everything.
 *
 * @param query what the reader typed
 * @param texts the texts of one entry
 * @returns true when the entry matches
 */
export function matches(query: string, ...texts: (string | undefined)[]): boolean {
    const words = query.toLocaleLowerCase().split(/\s+/).filter(word => word.length > 0)
    if (words.length === 0) return true
    const haystack = texts.filter(text => text !== undefined).join(' ').toLocaleLowerCase()
    return words.every(word => haystack.includes(word))
}

/**
 * Up to two letters standing for a name, as a square mark shows it: the first letters of its first two words, or
 * the first two letters of a single word.
 *
 * @param name the name
 * @returns the letters, upper case
 */
export function initials(name: string): string {
    const words = name.replace(/[^\p{L}\p{N}\s_]/gu, ' ').split(/[\s_]+/).filter(word => word.length > 0)
    if (words.length === 0) return '?'
    const letters = words.length === 1 ? (words[0] as string).slice(0, 2) : words.slice(0, 2).map(word => word[0]).join('')
    return letters.toLocaleUpperCase()
}

/**
 * A colour for a material's square, the same for the same material on every page, dark enough for white letters.
 *
 * @param material the material key
 * @returns the colour as an HSL value
 */
export function swatchOf(material: string): string {
    let hash = 0
    for (const char of material) hash = (hash * 31 + char.charCodeAt(0)) >>> 0
    return `hsl(${hash % 360} 28% 32%)`
}
