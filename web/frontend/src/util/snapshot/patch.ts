/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
import type {Patch, Snapshot} from '~/api/generated/protocol'

/** The parts of the snapshot this version knows, as the gateway's patch applier knows them. */
const ROOTS: ReadonlySet<string> = new Set(['player', 'company', 'orders', 'rankings'])

/** The fields that tell the entries of a list apart, in the order they are tried, as the gateway tries them. */
const KEY_FIELDS = ['id', 'uuid', 'key'] as const

type Container = Record<string, unknown>

/** A patch that does not fit the snapshot; the snapshot is then loaded again. */
export class Unapplicable extends Error {
    /**
     * @param reason why it does not fit
     */
    constructor(reason: string) {
        super(reason)
        this.name = 'Unapplicable'
    }
}

/**
 * Whether a patch changes a part of the snapshot this version knows. A patch into a part a newer plugin added does
 * not fit and is dropped rather than making the snapshot load again and again, as the gateway does.
 *
 * @param patch the patch
 * @returns true for a path below `player`, `company`, `orders` or `rankings`
 */
export function knownRoot(patch: Pick<Patch, 'path'>): boolean {
    return ROOTS.has((patch.path ?? '').split('.')[0] as string)
}

/**
 * Whether a patch is newer than what the snapshot holds of its node. Each node numbers its patches across every
 * session it serves, so a browser sees gaps in a node's numbers all the time; finding real gaps is the gateway's
 * job, which then sends a `resync`. Only a patch at or below the last one seen of its node is already contained.
 *
 * @param last the last sequence number seen per node, the snapshot's node included
 * @param patch the patch
 * @returns true when it is to be applied
 */
export function isNewer(last: Readonly<Record<string, number>>, patch: Pick<Patch, 'node' | 'seq'>): boolean {
    const before = last[patch.node]
    return before === undefined || patch.seq > before
}

/**
 * A copy of the snapshot with the patch applied, exactly as the gateway's patch applier does it. The snapshot itself
 * is left as it is.
 *
 * <p>The path is dotted into the snapshot, and every object on the way has to exist. `REPLACE`, and `UPSERT` without a
 * key, set the value at the path; `REMOVE` without a key drops it. With a key, `UPSERT` replaces or appends the list
 * entry with that key and `REMOVE` drops it; on an object the key names a property. A list entry's key is its `id`,
 * `uuid` or `key`, or the entry itself when it is a plain value.
 *
 * @param snapshot the snapshot
 * @param patch the patch
 * @returns the patched copy
 * @throws Unapplicable when the patch does not fit the snapshot
 */
export function applyPatch(snapshot: Snapshot, patch: Patch): Snapshot {
    if (!patch.path || !['UPSERT', 'REMOVE', 'REPLACE'].includes(patch.op)) throw new Unapplicable('No path or operation')
    const copy = structuredClone(snapshot) as unknown as Container
    const segments = patch.path.split('.')
    const field = segments.pop() as string
    const parent = walk(copy, segments)
    const value = patch.value === undefined ? null : patch.value
    if (patch.op === 'REPLACE' || (patch.op === 'UPSERT' && patch.key === undefined)) parent[field] = value
    else if (patch.op === 'REMOVE' && patch.key === undefined) Reflect.deleteProperty(parent, field)
    else parent[field] = keyed(parent[field], patch.op, patch.key as string, value)
    return copy as unknown as Snapshot
}

/**
 * The object at a path, which has to exist all the way.
 *
 * @param root where the path starts
 * @param segments the path
 * @returns the object
 * @throws Unapplicable when an object on the way is missing
 */
function walk(root: Container, segments: readonly string[]): Container {
    let current = root
    for (const segment of segments) {
        const next = current[segment]
        if (next === null || typeof next !== 'object' || Array.isArray(next)) throw new Unapplicable(`No object at ${segment}`)
        current = next as Container
    }
    return current
}

/**
 * A list or object with the entry of a key upserted or removed.
 *
 * @param target the list or object at the path
 * @param op `UPSERT` or `REMOVE`
 * @param key the entry's key
 * @param value the new entry
 * @returns the new list or object
 * @throws Unapplicable when there is no list or object at the path
 */
function keyed(target: unknown, op: Patch['op'], key: string, value: unknown): unknown {
    if (Array.isArray(target)) {
        const index = target.findIndex(entry => keyOf(entry) === key)
        if (op === 'REMOVE') return index === -1 ? target : target.filter((_, position) => position !== index)
        return index === -1 ? [...target, value] : target.map((entry, position) => (position === index ? value : entry))
    }
    if (target === null || typeof target !== 'object') throw new Unapplicable('No list or object at the path')
    const object = {...target as Container}
    if (op === 'REMOVE') Reflect.deleteProperty(object, key)
    else object[key] = value
    return object
}

/**
 * The key of a list entry, as text.
 *
 * @param entry the entry
 * @returns the key, or nothing for an entry without one
 */
function keyOf(entry: unknown): string | undefined {
    if (entry === null || entry === undefined) return undefined
    if (typeof entry !== 'object') return String(entry)
    const record = entry as Container
    const field = KEY_FIELDS.find(name => record[name] !== undefined && record[name] !== null && typeof record[name] !== 'object')
    return field === undefined ? undefined : String(record[field])
}
