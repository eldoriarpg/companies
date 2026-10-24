/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
import {elementsOf} from './sfc.mjs'

/** Components that carry no content of their own, walked past when looking for the first that does. */
export const WRAPPERS = new Set([
    'ViewContent',
    'Spinner',
    'Alert',
    'Modal',
    'SidePanel',
    'ClientOnly',
    'Teleport',
    'Transition',
    'TransitionGroup',
    'KeepAlive',
    'NuxtPage',
    'RouterView',
    'router-view',
    'template',
    'slot',
])

/**
 * The first `ViewContent` of a template.
 *
 * @param template the root template element
 * @returns the element, or undefined
 */
export function firstViewContent(template) {
    return elementsOf(template).find(element => element.rawName === 'ViewContent')
}
