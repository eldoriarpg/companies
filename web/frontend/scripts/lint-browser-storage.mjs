/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
/**
 * Browser Storage Linter
 *
 * Holds what the frontend keeps in the browser to the catalogue in `browser-storage.json`, which
 * names every key with what it is for. A key added to the code without an entry would keep
 * something in the reader's browser that nobody wrote down; an entry no code uses any more is a
 * stale promise.
 *
 * Data is kept in local storage and nowhere else: a cookie the page writes travels to the server
 * with every request, and session storage is lost with the tab. Both are refused outright.
 *
 * Keys are collected from `localStorage.getItem/setItem/removeItem` calls. A key held in a
 * constant of the same file resolves to its literal; any other argument cannot be checked and is
 * reported as well.
 *
 * Exit code 1 on an undeclared key, a key that cannot be resolved, a stale entry or a refused store.
 */
import {readFileSync} from 'node:fs'
import {join} from 'node:path'
import {ROOT, SRC, createReporter, rel, walk} from './lint-utils.mjs'

const reporter = createReporter()

const CATALOGUE = join(ROOT, 'browser-storage.json')
const STORAGE_CALL = /\blocalStorage\??\.(?:getItem|setItem|removeItem)\(\s*([^,)]+)/g
const REFUSED = [
    {pattern: /\bsessionStorage\b/, message: 'Session storage is lost with the tab. Keep the value in local storage.'},
    {pattern: /\bdocument\.cookie\b/, message: 'A cookie travels to the server with every request. Keep the value in local storage.'},
]

/**
 * The `const NAME = 'value'` bindings of a file, so a key held in a constant resolves to its literal.
 *
 * @param content the file's source
 * @returns the literal by constant name
 */
function constantsIn(content) {
    return new Map([...content.matchAll(/const\s+([A-Za-z_$][\w$]*)\s*=\s*'([^']*)'/g)].map(match => [match[1], match[2]]))
}

/**
 * The storage keys a file uses, and the arguments that could not be resolved to a literal.
 *
 * @param content the file's source
 * @returns the keys and the unresolved arguments
 */
function keysIn(content) {
    const constants = constantsIn(content)
    const keys = new Set()
    const dynamic = []
    for (const match of content.matchAll(STORAGE_CALL)) {
        const argument = match[1].trim()
        const literal = argument.match(/^'([^']*)'$/)
        if (literal) keys.add(literal[1])
        else if (constants.has(argument)) keys.add(constants.get(argument))
        else dynamic.push(argument)
    }
    return {keys, dynamic}
}

const declared = new Set(JSON.parse(readFileSync(CATALOGUE, 'utf-8')).entries.map(entry => entry.key))
const used = new Map()

for (const file of [...walk(SRC, '.ts'), ...walk(SRC, '.vue')].filter(name => !name.endsWith('.test.ts'))) {
    const content = readFileSync(file, 'utf-8')
    for (const {pattern, message} of REFUSED) {
        if (pattern.test(content)) reporter.error(file, 0, message, 'refused')
    }
    const {keys, dynamic} = keysIn(content)
    for (const key of keys) if (!used.has(key)) used.set(key, file)
    for (const argument of dynamic) {
        reporter.error(file, 0, `storage key is not a literal (${argument}), so it cannot be checked against ${rel(CATALOGUE)}`, 'dynamic')
    }
}

for (const [key, file] of used) {
    if (!declared.has(key)) reporter.error(file, 0, `local storage key '${key}' is not declared in ${rel(CATALOGUE)}`, 'undeclared')
}
for (const key of declared) {
    if (!used.has(key)) reporter.error(rel(CATALOGUE), 0, `declared local storage key '${key}' is no longer used`, 'stale')
}

console.log(`Checked ${used.size} storage keys against ${declared.size} declared in ${rel(CATALOGUE)}`)
reporter.print()
process.exit(reporter.errors.length > 0 ? 1 : 0)
