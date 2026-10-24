/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
import {resolve} from 'node:path'
import {baseKeys, definedProperties, localeFiles} from './catalogue.mjs'
import {collectUsage, isReferenced} from './usage.mjs'

const LOCALE_ENTRY = {
    type: 'object',
    properties: {file: {type: 'string'}, prefix: {type: 'string'}},
    required: ['file'],
    additionalProperties: false,
}

/**
 * Reports every translation nothing refers to, at the property that defines it.
 *
 * <p>Runs on the locale files, parsed by the locale parser. What counts as a reference is what
 * `collectUsage` finds in the sources. A file under `translations` is read against English as well:
 * a key it defines that English does not is one no reader can reach, since English is the language
 * everything falls back to.
 *
 * <p>`enableFix` lets `--fix` delete what it reports, for a deliberate clean-up. It is off by
 * default, so an ordinary fix run never removes a translation.
 */
export default {
    meta: {
        type: 'suggestion',
        docs: {description: 'disallow translations nothing refers to'},
        fixable: 'code',
        schema: [{
            type: 'object',
            properties: {
                sources: {type: 'string'},
                base: {type: 'array', items: LOCALE_ENTRY},
                translations: {type: 'array', items: {type: 'string'}},
                enableFix: {type: 'boolean'},
            },
            required: ['sources', 'base'],
            additionalProperties: false,
        }],
        messages: {
            unused: "i18n key '{{key}}' is never referenced.",
            notInBase: "i18n key '{{key}}' is not defined in English, which every other language falls back to.",
        },
    },
    create(context) {
        const options = context.options[0]
        const files = localeFiles(context, options)
        const current = resolve(context.filename)
        const base = files.base.find(entry => entry.file === current)
        const translation = files.translations.includes(current)
        if (!base && !translation) return {}

        return {
            'Program:exit'(program) {
                const defined = baseKeys(files.base, current, program)
                const sorted = [...defined].sort()
                const excluded = new Set([...files.base.map(entry => entry.file), ...files.translations])
                const usage = collectUsage(resolve(context.cwd, options.sources), excluded)
                const definesPrefix = head => leadsIntoKeys(sorted, head)

                for (const [key, property] of definedProperties(program, base?.prefix ?? '')) {
                    const fix = options.enableFix ? fixer => fixer.removeRange(removalRange(context.sourceCode.text, property)) : null
                    if (translation && !defined.has(key)) {
                        context.report({node: property.key, messageId: 'notInBase', data: {key}, fix})
                        continue
                    }
                    if (isReferenced(usage, key, definesPrefix)) continue
                    context.report({node: property.key, messageId: 'unused', data: {key}, fix})
                }
            },
        }
    },
}

/**
 * The text a property takes up, with its comma, and with its whole line where it stands alone on
 * it, so that removing it leaves no gap behind.
 *
 * @param text the file's source
 * @param property the JSON AST property
 * @returns the range to remove
 */
function removalRange(text, property) {
    let [start, end] = property.range
    if (text[end] === ',') end++
    const lineStart = text.lastIndexOf('\n', start - 1) + 1
    const lineEnd = text.indexOf('\n', end)
    const alone = text.slice(lineStart, start).trim() === '' && text.slice(end, lineEnd === -1 ? text.length : lineEnd).trim() === ''
    return alone ? [lineStart, lineEnd === -1 ? text.length : lineEnd + 1] : [start, end]
}

/**
 * Whether at least one defined key starts with the given text. The static head of a template
 * literal only counts as a prefix when it leads into the key space, which keeps unrelated literals
 * such as addresses and file names from reaching anything. A partial last segment
 * (`quiz.batch.action_`) counts, so a lookup by segment is not enough.
 *
 * @param sorted the defined keys, sorted
 * @param text the head
 * @returns true when a key starts with it
 */
function leadsIntoKeys(sorted, text) {
    let low = 0
    let high = sorted.length
    while (low < high) {
        const middle = (low + high) >> 1
        if (sorted[middle] < text) low = middle + 1
        else high = middle
    }
    return low < sorted.length && sorted[low].startsWith(text)
}
