/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
import type {Patch, Snapshot} from '~/api/generated/protocol'

/** The field that tells the entries of a list apart, by the list's path; lists not named here try the usual ones. */
const KEY_FIELDS: ReadonlyMap<string, string> = new Map([
    ['company.members', 'uuid'],
    ['company.orders', 'id'],
    ['company.alerts', 'name'],
    ['orders', 'id'],
    ['player.invites', 'company'],
    ['rankings.entries', 'company'],
])

/** The fields tried, in order, for a list whose key field is not known. */
const USUAL_KEYS = ['id', 'uuid', 'key', 'name'] as const

/** What a patch does to the snapshot it arrives for. */
export type Arrival = 'apply' | 'gap' | 'old'

type Container = Record<string, unknown>

/**
 * Whether a patch follows the last one seen from its node. A node first heard of after the snapshot starts its count
 * with this patch; a skipped number means patches were lost and the snapshot has to be loaded again.
 *
 * @param last the last sequence number seen per node, the snapshot's node included
 * @param patch the patch
 * @returns whether to apply it, reload, or drop it as already contained
 */
export function arrivalOf(last: Readonly<Record<string, number>>, patch: Pick<Patch, 'node' | 'seq'>): Arrival {
    const before = last[patch.node]
    if (before === undefined || patch.seq === before + 1) return 'apply'
    return patch.seq <= before ? 'old' : 'gap'
}

/**
 * A copy of the snapshot with the patch applied. The snapshot itself is left as it is.
 *
 * <p>The path is dotted into the snapshot. `REPLACE` sets the value at the path. On a list, `UPSERT` replaces the
 * entry with the patch's key or appends it, and `REMOVE` drops it; on an object, they set or delete the field named by
 * the key, or the path itself without one. A path whose parent does not exist, such as `company.members` for a player
 * without a company, is created only by a `REPLACE`.
 *
 * @param snapshot the snapshot
 * @param patch the patch
 * @returns the patched copy
 */
export function applyPatch(snapshot: Snapshot, patch: Patch): Snapshot {
    const copy = structuredClone(snapshot) as unknown as Container
    const segments = patch.path.split('.')
    const field = segments.pop() as string
    const parent = walk(copy, segments, patch.op === 'REPLACE')
    if (!parent) return copy as unknown as Snapshot
    if (patch.op === 'REPLACE') parent[field] = patch.value
    else if (Array.isArray(parent[field])) parent[field] = patchList(parent[field] as unknown[], patch)
    else patchObject(parent, field, patch)
    return copy as unknown as Snapshot
}

/**
 * The object at a path, created on the way where asked to.
 *
 * @param root where the path starts
 * @param segments the path
 * @param create whether missing objects are created
 * @returns the object, or nothing where the path does not exist
 */
function walk(root: Container, segments: string[], create: boolean): Container | null {
    let current = root
    for (const segment of segments) {
        const next = current[segment]
        if (next === null || next === undefined) {
            if (!create) return null
            current[segment] = {}
        } else if (typeof next !== 'object') {
            return null
        }
        current = current[segment] as Container
    }
    return current
}

/**
 * A list with one entry upserted or removed.
 *
 * @param list the list
 * @param patch the patch
 * @returns the new list
 */
function patchList(list: unknown[], patch: Patch): unknown[] {
    const keyField = KEY_FIELDS.get(patch.path)
    const matches = (entry: unknown) => patch.key !== undefined && keyOf(entry, keyField) === patch.key
    const kept = list.filter(entry => !matches(entry))
    if (patch.op === 'REMOVE') return kept
    const index = list.findIndex(matches)
    if (index === -1) return [...list, patch.value]
    return list.map((entry, position) => (position === index ? patch.value : entry))
}

/**
 * Sets or deletes a field of an object.
 *
 * @param parent the object holding the path
 * @param field the path's last segment
 * @param patch the patch
 */
function patchObject(parent: Container, field: string, patch: Patch): void {
    const target = patch.key === undefined ? parent : parent[field] as Container | undefined
    const name = patch.key ?? field
    if (!target || typeof target !== 'object') {
        if (patch.op === 'UPSERT' && patch.key === undefined) parent[field] = patch.value
        return
    }
    if (patch.op === 'REMOVE') Reflect.deleteProperty(target, name)
    else target[name] = patch.value
}

/**
 * The key of a list entry, as text.
 *
 * @param entry the entry
 * @param field the key field, where the list's is known
 * @returns the key, or nothing for an entry without one
 */
function keyOf(entry: unknown, field: string | undefined): string | undefined {
    if (!entry || typeof entry !== 'object') return undefined
    const record = entry as Container
    const name = field ?? USUAL_KEYS.find(candidate => record[candidate] !== undefined)
    const value = name === undefined ? undefined : record[name]
    return value === undefined || value === null ? undefined : String(value)
}
