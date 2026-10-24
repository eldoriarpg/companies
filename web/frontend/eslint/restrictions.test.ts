/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import tsParser from '@typescript-eslint/parser'
import {RuleTester} from 'eslint'
import {builtinRules} from 'eslint/use-at-your-own-risk'
import {GENERIC_FAILURE, INLINE_DATE_FORMAT, INLINE_STATE_TYPE} from './restrictions.mjs'

/**
 * What each refused shape catches and what it leaves alone.
 *
 * @vitest-environment node
 *
 * <p>A selector reads as nothing like the code it stands against, so the cases are the only honest
 * account of it: too eager and it refuses code that is fine, too shy and the shape grows back.
 */
const tester = new RuleTester({languageOptions: {parser: tsParser}})
const restricted = builtinRules.get('no-restricted-syntax')!

/**
 * The cases for one restriction.
 *
 * @param restriction the selector and its message
 * @param valid code it must leave alone
 * @param invalid code it must report once
 */
function holds(restriction: {selector: string, message: string}, valid: string[], invalid: string[]) {
    tester.run('no-restricted-syntax', restricted, {
        valid: valid.map(code => ({code, options: [restriction]})),
        invalid: invalid.map(code => ({code, options: [restriction], errors: [{message: restriction.message}]})),
    })
}

holds(INLINE_STATE_TYPE, [
    'const count = ref(0)',
    'const impact = ref<DeleteImpact | null>(null)',
    'const byId = ref<Record<string, number>>({})',
], [
    'const impact = ref<{folders: number}>({folders: 0})',
    'const impact = ref<{folders: number} | null>(null)',
])

holds(GENERIC_FAILURE, [
    "t('market.failed')",
    'describeFailure(error, t)',
], [
    "t('failure.unknown.message')",
    "i18n.t('failure.offline.guidance')",
])

holds(INLINE_DATE_FORMAT, [
    'count.toLocaleString(locale)',
    'when(time, now, locale)',
], [
    'date.toLocaleDateString(locale)',
    "date.toLocaleString(locale, {hour: '2-digit'})",
])
