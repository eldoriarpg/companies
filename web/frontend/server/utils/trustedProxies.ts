/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
import {isIP} from 'node:net'

/** One trusted address or network. */
interface Range {
    version: 4 | 6
    network: bigint
    bits: number
}

/**
 * An address as a number of its version, IPv4-mapped IPv6 addresses taken as the IPv4 address they carry.
 *
 * @param address the address
 * @returns the version and the number, or nothing for text that is no address
 */
function parse(address: string): {version: 4 | 6, value: bigint} | null {
    const plain = address.trim().replace(/^\[|\]$/g, '').replace(/^::ffff:(\d+\.\d+\.\d+\.\d+)$/i, '$1')
    const version = isIP(plain)
    if (version === 4) {
        return {version, value: plain.split('.').reduce((sum, part) => (sum << 8n) + BigInt(Number(part)), 0n)}
    }
    if (version !== 6) return null
    const [head = '', tail = ''] = plain.split('::')
    const left = head ? head.split(':') : []
    const right = plain.includes('::') ? (tail ? tail.split(':') : []) : []
    const groups = [...left, ...Array<string>(8 - left.length - right.length).fill('0'), ...right]
    return {version, value: groups.reduce((sum, group) => (sum << 16n) + BigInt(Number.parseInt(group || '0', 16)), 0n)}
}

/**
 * The ranges of a list such as `127.0.0.1, 10.0.0.0/8, fd00::/8`; entries that are no address or network are left
 * out.
 *
 * @param list the comma separated list
 * @returns the ranges
 */
export function parseRanges(list: string): Range[] {
    return list.split(',').map(entry => entry.trim()).filter(Boolean).flatMap(entry => {
        const [address = '', size] = entry.split('/')
        const parsed = parse(address)
        if (!parsed) return []
        const width = parsed.version === 4 ? 32 : 128
        const bits = size === undefined ? width : Number(size)
        if (!Number.isInteger(bits) || bits < 0 || bits > width) return []
        const shift = BigInt(width - bits)
        return [{version: parsed.version, network: parsed.value >> shift, bits}]
    })
}

/**
 * Whether an address lies in one of the ranges.
 *
 * @param address the address the connection came from
 * @param ranges the trusted ranges
 * @returns true when it is trusted
 */
export function isTrusted(address: string | undefined, ranges: readonly Range[]): boolean {
    const parsed = address ? parse(address) : null
    if (!parsed) return false
    return ranges.some(range => {
        if (range.version !== parsed.version) return false
        const width = range.version === 4 ? 32 : 128
        return parsed.value >> BigInt(width - range.bits) === range.network
    })
}
