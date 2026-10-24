/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
/** One entry of a dropdown: what it stands for, what it reads, and the heading it sits under. */
export interface SelectOption {
    value: string
    label: string
    group?: string
}

/** The entries under one heading, or under none where the list has no headings at all. */
export interface OptionGroup {
    group?: string
    options: SelectOption[]
}

/**
 * Sorts entries under their headings, each heading where its first entry stood.
 *
 * <p>A list without a single heading stays one group, so it is drawn without any.
 */
export function groupOptions(options: SelectOption[]): OptionGroup[] {
    if (!options.some(option => option.group)) return [{options}]
    const groups = new Map<string | undefined, SelectOption[]>()
    for (const option of options) {
        const members = groups.get(option.group) ?? []
        members.push(option)
        groups.set(option.group, members)
    }
    return [...groups].map(([group, members]) => ({group, options: members}))
}

/** The entries whose label holds what was typed, ignoring case. Nothing typed keeps them all. */
export function filterOptions<T extends { label: string }>(options: T[], query: string): T[] {
    const typed = query.trim().toLowerCase()
    if (!typed) return options
    return options.filter(option => option.label.toLowerCase().includes(typed))
}
