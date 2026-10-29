/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import type {IconName} from '~/util/icons'
import {Feature} from '~/api/generated/protocol'

/** A section of the dashboard. */
export interface Section {
    id: 'portfolio' | 'orders' | 'market' | 'company' | 'inbox' | 'events' | 'rankings' | 'admin'
    path: string
    icon: IconName
    /** The feature the server has to list for the section to show; none for the sections the snapshot carries. */
    feature?: Feature
    /** Whether the phone's tab bar carries it; the others are in the menu. */
    tab: boolean
    /** Shown while the server lists any admin feature, which it does for players with admin rights only. */
    adminOnly?: boolean
}

/** Every section, in the order the navigation lists them. */
export const SECTIONS: readonly Section[] = [
    {id: 'portfolio', path: '/app', icon: 'portfolio', tab: true},
    {id: 'orders', path: '/app/orders', icon: 'orders', feature: Feature['orders.browse'], tab: true},
    {id: 'market', path: '/app/market', icon: 'market', feature: Feature.market, tab: true},
    {id: 'company', path: '/app/company', icon: 'company', tab: true},
    {id: 'inbox', path: '/app/inbox', icon: 'inbox', feature: Feature.inbox, tab: true},
    {id: 'events', path: '/app/events', icon: 'events', feature: Feature.events, tab: false},
    {id: 'rankings', path: '/app/rankings', icon: 'rankings', feature: Feature.rankings, tab: false},
    {id: 'admin', path: '/app/admin', icon: 'admin', adminOnly: true, tab: false},
]

/**
 * The sections a server shows: those whose feature its plugin lists, those that need none, and the admin section while
 * any admin feature is listed.
 *
 * @param features the server's features
 * @returns the sections
 */
export function sectionsFor(features: readonly string[]): Section[] {
    return SECTIONS.filter(section => (section.adminOnly
        ? features.some(feature => feature.startsWith('admin.'))
        : !section.feature || features.includes(section.feature)))
}

/**
 * The section a path belongs to: the one whose path it is or lies under, the deepest where several match, so a page
 * such as placing an order belongs to the order book.
 *
 * @param path the route's path
 * @returns the section, or nothing outside the dashboard
 */
export function sectionOf(path: string): Section | undefined {
    const normalised = path.replace(/\/+$/, '') || '/'
    return SECTIONS
        .filter(section => normalised === section.path || normalised.startsWith(`${section.path}/`))
        .reduce<Section | undefined>((deepest, section) => (deepest && deepest.path.length >= section.path.length ? deepest : section), undefined)
}
