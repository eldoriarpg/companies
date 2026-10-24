/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {resolve} from 'node:path'
import {leafKeys, localeMessagesOf, messagesOf} from './locale-parser.mjs'

/**
 * The locale files a rule is told about, resolved against the directory ESLint runs in.
 *
 * <p>`base` lists the files the English messages are made of, each with the key it is merged in
 * under: the locale itself at the top, a block kept in a file of its own under its prefix. English is
 * the language every other one falls back to. `translations` lists the other languages, which are
 * read against it.
 *
 * @param context the rule context
 * @param options the rule's options
 * @returns the files, absolute
 */
export function localeFiles(context, options) {
    return {
        base: (options.base ?? []).map(entry => ({file: resolve(context.cwd, entry.file), prefix: entry.prefix ?? ''})),
        translations: (options.translations ?? []).map(file => resolve(context.cwd, file)),
    }
}

/**
 * Every leaf key of the file being linted, with the property that defines it.
 *
 * @param program the JSON AST the locale parser produced
 * @param prefix the key the file is merged in under
 * @returns the properties by dotted key, in source order
 */
export function definedProperties(program, prefix) {
    const found = new Map()
    collect(program.body[0].expression, prefix, found)
    return found
}

/**
 * Every leaf key of the base messages, with the file being linted read from its AST rather than
 * from disk, so a rule sees what the editor holds.
 *
 * @param base the base language's files
 * @param current the absolute path of the file being linted
 * @param program its JSON AST
 * @returns the keys
 */
export function baseKeys(base, current, program) {
    const keys = new Set()
    for (const {file, prefix} of base) {
        const messages = file === current ? messagesOf(program) : localeMessagesOf(file)
        for (const key of leafKeys(messages, prefix)) keys.add(key)
    }
    return keys
}

/**
 * The name of a property's key.
 *
 * @param property the JSON AST property
 * @returns the name
 */
function nameOf(property) {
    return property.key.type === 'JSONIdentifier' ? property.key.name : String(property.key.value)
}

/**
 * Walks an object of the JSON AST, recording its leaves.
 *
 * @param object the object
 * @param path the key it sits under
 * @param found the leaves so far
 */
function collect(object, path, found) {
    for (const property of object.properties) {
        const key = path ? `${path}.${nameOf(property)}` : nameOf(property)
        if (property.value.type === 'JSONObjectExpression') collect(property.value, key, found)
        else found.set(key, property)
    }
}
