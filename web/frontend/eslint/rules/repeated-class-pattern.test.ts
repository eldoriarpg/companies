/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import rule from './repeated-class-pattern.mjs'
import {sourceFile, vueRuleTester} from './rule-tester'

/**
 * An element pattern counts where the element opens its line with a plain, specific class list on
 * that line, and is reported at each occurrence once the count reaches the threshold.
 *
 * @vitest-environment node
 */
const options = [{threshold: 3, scanProject: false}]
const view = sourceFile('views/SomeView.vue')
const repeated = (line: string, count: number) => `<template><div>\n${`  ${line}\n`.repeat(count)}</div></template>`
const muted = '<p class="py-4 text-(--text-muted) text-center text-sm">x</p>'

vueRuleTester().run('repeated-class-pattern', rule, {
    valid: [
        {code: repeated(muted, 2), filename: view, options},
        {code: repeated(muted, 5), filename: sourceFile('components/display/Hint.vue'), options},
        {code: repeated('<div class="flex items-center gap-2 mt-4">x</div>', 5), filename: view, options},
        {code: repeated('<p class="font-medium truncate">x</p>', 5), filename: view, options},
        {code: repeated('<p class="a-b c-d">x</p>', 5), filename: view, options},
        {code: repeated(`<span>y</span> ${muted}`, 5), filename: view, options},
        {code: repeated('<p\n    class="py-4 text-(--text-muted) text-center text-sm">x</p>', 5), filename: view, options},
    ],
    invalid: [
        {
            code: repeated(muted, 3),
            filename: view,
            options,
            errors: Array.from({length: 3}, () => ({messageId: 'repeated'})),
        },
        {
            code: `<template><div>\n  ${muted}\n  <p class="text-sm text-center text-(--text-muted) py-4">y</p>\n  ${muted}\n</div></template>`,
            filename: view,
            options,
            errors: Array.from({length: 3}, () => ({messageId: 'repeated'})),
        },
    ],
})
