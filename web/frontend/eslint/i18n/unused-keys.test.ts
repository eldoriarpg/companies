/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import rule from './unused-keys.mjs'
import {BASE, fixture, fixtureSource, localeRuleTester} from './fixture'

/**
 * Which translations count as referenced.
 *
 * @vitest-environment node
 *
 * <p>Keys reach `t()` in more ways than being written into a call, and every one the rule misses
 * is a translation it would have somebody delete from under a screen that shows it. The fixture's
 * sources use each of those ways once, and the English file defines one key none of them reaches.
 */
const OPTIONS = {
    sources: fixture('src'),
    base: BASE,
    translations: [fixture('src/i18n/de.ts')],
}

localeRuleTester().run('i18n-unused-keys', rule, {
    valid: [
        {
            name: 'every error is reached through the key built from its code',
            filename: fixture('src/i18n/errors/forms.ts'),
            code: fixtureSource('src/i18n/errors/forms.ts'),
            options: [OPTIONS],
        },
        {
            name: 'a file the rule is not told about is left alone',
            filename: fixture('src/i18n/other.ts'),
            code: 'export default {nobody: {uses: \'this\'}}',
            options: [OPTIONS],
        },
    ],
    invalid: [
        {
            name: 'reports the one English key nothing reaches, and nothing the sources reach another way',
            filename: fixture('src/i18n/en.ts'),
            code: fixtureSource('src/i18n/en.ts'),
            options: [OPTIONS],
            errors: [{message: "i18n key 'common.unused' is never referenced."}],
        },
        {
            name: 'reads a block under the key it is merged in under',
            filename: fixture('src/i18n/en.help.ts'),
            code: fixtureSource('src/i18n/en.help.ts'),
            options: [OPTIONS],
            errors: [{message: "i18n key 'help.orphan' is never referenced."}],
        },
        {
            name: 'reports a key another language defines and English does not',
            filename: fixture('src/i18n/de.ts'),
            code: fixtureSource('src/i18n/de.ts'),
            options: [OPTIONS],
            errors: [{message: "i18n key 'extra.only' is not defined in English, which every other language falls back to."}],
        },
        {
            name: 'deletes the key with its line when asked to',
            filename: fixture('src/i18n/en.ts'),
            code: fixtureSource('src/i18n/en.ts'),
            options: [{...OPTIONS, enableFix: true}],
            output: fixtureSource('src/i18n/en.ts').replace("        unused: 'Never used',\n", ''),
            errors: [{message: "i18n key 'common.unused' is never referenced."}],
        },
    ],
})
