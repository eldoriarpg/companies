/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
import vueI18n from '@intlify/eslint-plugin-vue-i18n'
import {describe, expect, it} from 'vitest'
import {leafKeys, localeMessagesOf, messagesOf, parseForESLint} from './locale-parser.mjs'
import {fixture, FIXTURE, localeRuleTester} from './fixture'

/**
 * Reading a TypeScript locale module as the JSON the vue-i18n plugin expects.
 *
 * @vitest-environment node
 *
 * <p>The plugin's rules for locale files see nothing but this parser's AST, so what it leaves out
 * they never check and where it puts a node is where they report.
 */
describe('the locale parser', () => {
    it('yields the messages the module exports, constants and concatenations resolved', () => {
        const {ast} = parseForESLint("const SHARED = 'geteilt'\nexport default {a: {b: 'x' + 'y', c: SHARED}, d: `e`}")

        expect(messagesOf(ast)).toEqual({a: {b: 'xy', c: 'geteilt'}, d: 'e'})
    })

    it('stands an imported block in as an empty object, so it holds no messages of this file', () => {
        const {ast} = parseForESLint("import errors from './en.errors'\nexport default {a: 'b', errors}")

        expect(leafKeys(messagesOf(ast))).toEqual(['a'])
    })

    it('resolves a constant imported from a sibling module to its string', () => {
        expect(localeMessagesOf(fixture('src/i18n/errors/general.ts')))
            .toEqual({'G-001': 'This no longer exists', 'G-009': 'Old'})
    })

    it('leaves an imported constant unknown when the module has no path to resolve it against', () => {
        const {ast} = parseForESLint("import {GONE} from './shared'\nexport default {a: GONE, b: 'c'}")

        expect(leafKeys(messagesOf(ast))).toEqual(['b'])
    })

    it('reads a module from disk, keys dotted under a prefix', () => {
        expect(leafKeys(localeMessagesOf(fixture('src/i18n/en.help.ts')), 'help'))
            .toEqual(['help.title', 'help.orphan'])
    })
})

localeRuleTester().run('valid-message-syntax through the locale parser', vueI18n.rules['valid-message-syntax'], {
    valid: [
        {
            filename: fixture('src/i18n/en.ts'),
            code: "export default {a: 'Hallo {name}', b: \"mail {'@'} x\"}",
            settings: {'vue-i18n': {localeDir: `${FIXTURE}src/i18n/*.ts`, messageSyntaxVersion: '^11.0.0'}},
        },
    ],
    invalid: [
        {
            filename: fixture('src/i18n/en.ts'),
            code: "export default {\n    a: 'offen {name',\n}",
            settings: {'vue-i18n': {localeDir: `${FIXTURE}src/i18n/*.ts`, messageSyntaxVersion: '^11.0.0'}},
            errors: [{message: 'Unterminated closing brace', line: 2}],
        },
    ],
})
