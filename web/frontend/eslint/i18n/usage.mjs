/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {readdirSync, readFileSync} from 'node:fs'
import {join, resolve} from 'node:path'
import * as typescriptParser from '@typescript-eslint/parser'
import {parseForESLint as parseVue} from 'vue-eslint-parser'

const TRANSLATE = new Set(['t', 'te', 'tc', 'tm', '$t', '$te', '$tc', '$tm'])
const SUBTREE = new Set(['tm', '$tm'])
const KEY_SHAPE = /^[A-Za-z][\w-]*(?:\.[\w-]+)+$/
const PREFIX_SHAPE = /^[A-Za-z][\w-]*(?:\.[\w-]*)+$/
const TAIL_SHAPE = /^(?:\.[\w-]+)+$/
const PREFIX_VALUE_SHAPE = /^[A-Za-z][\w-]*(?:\.[\w-]+)*$/
const PREFIX_ATTRIBUTE = /^i18n[-_]?prefix$/i
const SKIPPED = new Set(['parent', 'loc', 'range', 'tokens', 'comments', 'errors', 'references', 'variables', 'templateBody'])

const cache = new Map()

/**
 * How the sources refer to translation keys, collected once per process from every `.ts` and
 * `.vue` file under a directory.
 *
 * <p>Keys are handed around in more ways than a call to `t()`, and each of these counts:
 * <ul>
 *   <li>`t`, `te`, `tc`, `tm` and their `$` forms called with a key, and a key built from a
 *   string and a `+` or from a template literal reaches every key below its static head;</li>
 *   <li>`tm` reaches the whole subtree below its key;</li>
 *   <li>a template literal anywhere whose static head leads into the key space reaches every key
 *   below it, because help pages and error mappers build the key into a local first;</li>
 *   <li>a template literal that is only an interpolation and a static tail, such as
 *   `${props.i18nPrefix}.form.title`, pairs with the values callers pass to an `i18n-prefix`
 *   attribute, and one that goes on into a second interpolation, such as
 *   `${props.i18nPrefix}.form.types.${type}`, reaches every key below the pair;</li>
 *   <li>a string that spells a key, in the script or as an attribute value, because route tables,
 *   error maps and props pass keys around as data;</li>
 *   <li>the `keypath` of an `<i18n-t>`.</li>
 * </ul>
 *
 * @param directory the absolute directory to read
 * @param excluded absolute paths of files to leave out, the locale files themselves
 * @returns the references found
 */
export function collectUsage(directory, excluded) {
    const cacheKey = `${directory}\n${[...excluded].sort().join('\n')}`
    if (cache.has(cacheKey)) return cache.get(cacheKey)
    const usage = {exact: new Set(), prefixes: new Set(), heads: new Set(), prefixValues: new Set(), tails: new Set(), openTails: new Set()}
    for (const file of sourceFiles(directory)) {
        if (excluded.has(file)) continue
        for (const root of parseSource(file)) walk(root, node => visit(node, usage))
    }
    cache.set(cacheKey, usage)
    return usage
}

/**
 * Whether a key counts as referenced by what {@link collectUsage} found.
 *
 * @param usage the references
 * @param key the dotted key
 * @param definesPrefix whether a static template head leads into the key space, which is what
 *                      keeps unrelated literals such as addresses out of the prefixes
 * @returns true when something refers to it
 */
export function isReferenced(usage, key, definesPrefix) {
    if (usage.exact.has(key)) return true
    for (const prefix of usage.prefixes) {
        if (key.startsWith(prefix)) return true
    }
    for (const head of usage.heads) {
        const prefix = head.replace(/\.+$/, '')
        if (prefix.length > 0 && key.startsWith(prefix) && definesPrefix(head)) return true
    }
    for (const value of usage.prefixValues) {
        for (const tail of usage.tails) {
            if (value + tail === key) return true
        }
        for (const tail of usage.openTails) {
            if (key.startsWith(`${value}${tail}.`)) return true
        }
    }
    return false
}

/**
 * Every `.ts` and `.vue` file below a directory, declarations left out.
 *
 * @param directory the absolute directory
 * @returns absolute paths
 */
function sourceFiles(directory) {
    const found = []
    for (const entry of readdirSync(directory, {withFileTypes: true})) {
        const path = join(directory, entry.name)
        if (entry.isDirectory()) found.push(...sourceFiles(path))
        else if (entry.name.endsWith('.vue') || (entry.name.endsWith('.ts') && !entry.name.endsWith('.d.ts'))) found.push(resolve(path))
    }
    return found
}

/**
 * The syntax trees of one source file: the script, and the template of a component.
 *
 * @param file the absolute path
 * @returns the roots to walk
 */
function parseSource(file) {
    const code = readFileSync(file, 'utf-8')
    if (file.endsWith('.vue')) {
        const {ast} = parseVue(code, {parser: typescriptParser, sourceType: 'module', ecmaVersion: 'latest', filePath: file})
        return ast.templateBody ? [ast, ast.templateBody] : [ast]
    }
    return [typescriptParser.parse(code, {sourceType: 'module', filePath: file})]
}

/**
 * Visits every node below a root, in no particular order.
 *
 * @param root the root
 * @param visitor called with each node
 */
function walk(root, visitor) {
    const pending = [root]
    while (pending.length > 0) {
        const node = pending.pop()
        visitor(node)
        for (const [name, value] of Object.entries(node)) {
            if (SKIPPED.has(name) || value === null || typeof value !== 'object') continue
            if (Array.isArray(value)) {
                for (const item of value) if (item && typeof item.type === 'string') pending.push(item)
            } else if (typeof value.type === 'string') {
                pending.push(value)
            }
        }
    }
}

/**
 * Records what one node says about keys.
 *
 * @param node the node
 * @param usage the references collected so far
 */
function visit(node, usage) {
    switch (node.type) {
        case 'CallExpression':
            visitCall(node, usage)
            break
        case 'Literal':
            if (typeof node.value === 'string' && KEY_SHAPE.test(node.value)) usage.exact.add(node.value)
            break
        case 'TemplateLiteral':
            visitTemplate(node, usage)
            break
        case 'VAttribute':
            visitAttribute(node, usage)
            break
        default:
            break
    }
}

/**
 * A call to one of the translation functions.
 *
 * @param node the call
 * @param usage the references collected so far
 */
function visitCall(node, usage) {
    const name = calleeName(node.callee)
    if (!TRANSLATE.has(name) || node.arguments.length === 0) return
    const argument = node.arguments[0]
    const whole = staticString(argument)
    if (whole !== null) {
        if (!whole.includes('.')) return
        usage.exact.add(whole)
        if (SUBTREE.has(name)) usage.prefixes.add(whole)
        return
    }
    if (argument.type === 'TemplateLiteral') {
        if (!argument.quasis.some(quasi => quasi.value.cooked.includes('.'))) return
        addPrefix(usage, argument.quasis[0].value.cooked)
        return
    }
    if (argument.type === 'BinaryExpression' && argument.operator === '+') {
        const head = staticString(leftmost(argument))
        if (head !== null) addPrefix(usage, head)
    }
}

/**
 * A template literal, which may be a key built from a static head or a tail after a prefix.
 *
 * @param node the template literal
 * @param usage the references collected so far
 */
function visitTemplate(node, usage) {
    if (node.expressions.length === 0) {
        const value = node.quasis[0].value.cooked
        if (KEY_SHAPE.test(value)) usage.exact.add(value)
        return
    }
    const head = node.quasis[0].value.cooked
    if (PREFIX_SHAPE.test(head)) usage.heads.add(head)
    if (head !== '') return
    const middle = node.quasis[1].value.cooked
    if (node.quasis.length === 2 && TAIL_SHAPE.test(middle)) usage.tails.add(middle)
    if (node.quasis.length > 2 && TAIL_SHAPE.test(middle.replace(/\.$/, ''))) usage.openTails.add(middle.replace(/\.$/, ''))
}

/**
 * A static attribute of a template: a key written out, a key path or a prefix handed down.
 *
 * @param node the attribute
 * @param usage the references collected so far
 */
function visitAttribute(node, usage) {
    if (node.directive || !node.value) return
    const name = node.key.rawName ?? node.key.name
    const value = node.value.value
    if (PREFIX_ATTRIBUTE.test(name) && PREFIX_VALUE_SHAPE.test(value)) usage.prefixValues.add(value)
    if (KEY_SHAPE.test(value) || name === 'keypath') usage.exact.add(value)
}

/**
 * Adds a static head as a prefix every key below it counts under.
 *
 * @param usage the references collected so far
 * @param head the static text before the dynamic part
 */
function addPrefix(usage, head) {
    const prefix = head.replace(/\.+$/, '')
    if (prefix.length > 0) usage.prefixes.add(prefix)
}

/**
 * The name a call is made through, whether plain or as a member.
 *
 * @param callee the callee
 * @returns the name, or an empty string
 */
function calleeName(callee) {
    if (callee.type === 'Identifier') return callee.name
    if (callee.type === 'MemberExpression' && !callee.computed && callee.property.type === 'Identifier') return callee.property.name
    return ''
}

/**
 * The value of a string literal or a template literal without substitutions.
 *
 * @param node the expression
 * @returns the string, or null for anything else
 */
function staticString(node) {
    if (node.type === 'Literal' && typeof node.value === 'string') return node.value
    if (node.type === 'TemplateLiteral' && node.expressions.length === 0) return node.quasis[0].value.cooked
    return null
}

/**
 * The first operand of a chain of `+`.
 *
 * @param node the expression
 * @returns the operand furthest to the left
 */
function leftmost(node) {
    let current = node
    while (current.type === 'BinaryExpression' && current.operator === '+') current = current.left
    return current
}
