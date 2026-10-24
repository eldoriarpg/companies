/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import rule from './no-plural-messages.mjs'
import {fixture, localeRuleTester} from './fixture'

/**
 * A pipe written as text, which vue-i18n reads as the start of a second plural choice.
 *
 * @vitest-environment node
 */
localeRuleTester().run('i18n-no-plural-messages', rule, {
    valid: [
        {
            name: 'an escaped pipe is text',
            filename: fixture('src/i18n/en.ts'),
            code: "export default {a: {b: 'Ja {\\'|\\'} Nein'}}",
        },
        {
            name: 'a message without a pipe',
            filename: fixture('src/i18n/en.ts'),
            code: "export default {a: 'Ja oder Nein'}",
        },
    ],
    invalid: [
        {
            name: 'a bare pipe splits the message',
            filename: fixture('src/i18n/en.ts'),
            code: "export default {a: {b: 'Ja | Nein'}}",
            errors: [{messageId: 'plural', line: 1}],
        },
        {
            name: 'also when it is concatenated or shared through a constant',
            filename: fixture('src/i18n/en.ts'),
            code: "const SHARED = 'eins | zwei'\nexport default {a: SHARED, b: 'drei ' + '| vier'}",
            errors: [{messageId: 'plural', line: 1}, {messageId: 'plural', line: 2}],
        },
    ],
})
