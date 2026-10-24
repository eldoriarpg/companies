/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
import type {IconName} from '~/util/icons'
import {Feature} from '~/api/generated/protocol'

/** A section of the dashboard. */
export interface Section {
    id: 'portfolio' | 'orders' | 'market' | 'company' | 'inbox' | 'rankings'
    path: string
    icon: IconName
    /** The feature the server has to list for the section to show; none for the sections the snapshot carries. */
    feature?: Feature
    /** Whether the phone's tab bar carries it; the others are in the menu. */
    tab: boolean
}

/** Every section, in the order the navigation lists them. */
export const SECTIONS: readonly Section[] = [
    {id: 'portfolio', path: '/app', icon: 'portfolio', tab: true},
    {id: 'orders', path: '/app/orders', icon: 'orders', feature: Feature['orders.browse'], tab: true},
    {id: 'market', path: '/app/market', icon: 'market', feature: Feature.market, tab: true},
    {id: 'company', path: '/app/company', icon: 'company', tab: true},
    {id: 'inbox', path: '/app/inbox', icon: 'inbox', feature: Feature.inbox, tab: true},
    {id: 'rankings', path: '/app/rankings', icon: 'rankings', feature: Feature.rankings, tab: false},
]

/**
 * The sections a server shows: those whose feature its plugin lists, and those that need none.
 *
 * @param features the server's features
 * @returns the sections
 */
export function sectionsFor(features: readonly string[]): Section[] {
    return SECTIONS.filter(section => !section.feature || features.includes(section.feature))
}

/**
 * The section a path belongs to.
 *
 * @param path the route's path
 * @returns the section, or nothing outside the dashboard
 */
export function sectionOf(path: string): Section | undefined {
    const normalised = path.replace(/\/+$/, '') || '/'
    return SECTIONS.find(section => section.path === normalised)
}
