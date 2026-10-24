/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import type {MemberPermission} from '~/api/generated/protocol'
import {openLabel, type Translator} from '~/util/labels'
import {sectionOf, sectionsFor, type Section} from '~/util/navigation'
import {useDashboard} from './useDashboard'

/**
 * What the dashboard's frame shows around a page: the sections the server supports, the one open, whether the server
 * offers it at all (a page of a feature the server does not list is not shown, also when its address is typed), its
 * title, the counts beside them and the player's role in their company.
 *
 * @returns the frame's state
 */
export function useShell() {
    const {t, te} = useI18n()
    const route = useRoute()
    const {server, snapshot} = useDashboard()

    const current = computed(() => sectionOf(route.path))

    return {
        sections: computed(() => sectionsFor(server.value?.features ?? [])),
        current,
        unavailable: computed(() => {
            const feature = current.value?.feature
            return Boolean(feature && server.value && !server.value.features.includes(feature))
        }),
        title: computed(() => (current.value ? t(`nav.${current.value.id}`) : '')),
        badges: computed<Partial<Record<Section['id'], number>>>(() => ({inbox: snapshot.value?.player.unreadInbox ?? 0})),
        role: computed(() => roleOf(snapshot.value?.player.permissions ?? [], {t, te})),
    }
}

/**
 * The player's role as the sidebar names it: the owner, or the permissions they hold, or a plain member.
 *
 * @param permissions the player's permissions in their company
 * @param translator the translation functions
 * @returns the role as text
 */
export function roleOf(permissions: readonly MemberPermission[], translator: Translator): string {
    if (permissions.includes('OWNER')) return translator.t('permission.OWNER')
    if (permissions.length === 0) return translator.t('permission.member')
    return permissions.map(permission => openLabel(translator, `permission.${permission}`, permission)).join(' · ')
}
