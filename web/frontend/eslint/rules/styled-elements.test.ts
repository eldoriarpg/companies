/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import rule from './styled-elements.mjs'
import {sourceFile, vueRuleTester} from './rule-tester'

/**
 * Raw controls, headings and badges are written raw only where their styled component is built.
 *
 * @vitest-environment node
 */
const view = sourceFile('views/SomeView.vue')
const template = (inner: string) => `<template><div>${inner}</div></template>`

vueRuleTester().run('styled-elements', rule, {
    valid: [
        {code: template('<button type="button">x</button>'), filename: sourceFile('components/button/BaseButton.vue')},
        {code: template('<button type="button">x</button>'), filename: sourceFile('components/input/Chip.vue')},
        {code: template('<input type="text"/><select/><textarea/>'), filename: sourceFile('components/input/TextInput.vue')},
        {code: template('<input type="file" accept="image/*"/>'), filename: view},
        {code: template('<h2>x</h2>'), filename: sourceFile('components/typography/SectionHeader.vue')},
        {code: template('<span class="rounded-full px-2">x</span>'), filename: sourceFile('components/badge/BaseBadge.vue')},
        {code: template('<span class="rounded-full h-2 w-2"/>'), filename: view},
        {code: template('<PrimaryButton>x</PrimaryButton><SectionHeader>x</SectionHeader>'), filename: view},
    ],
    invalid: [
        {
            code: template('<button\n  type="button"\n>x</button>'),
            filename: view,
            errors: [{messageId: 'button'}],
        },
        {code: template('<button>x</button>'), filename: sourceFile('components/typography/HintIcon.vue'), errors: [{messageId: 'button'}]},
        {
            code: template('<input type="text"/><select v-model="x"/><textarea v-model="y"/>'),
            filename: sourceFile('components/button/Search.vue'),
            errors: [{messageId: 'input'}, {messageId: 'select'}, {messageId: 'textarea'}],
        },
        {
            code: template('<h1>a</h1><h3>b</h3><h4>c</h4>'),
            filename: view,
            errors: [{messageId: 'heading', data: {tag: 'h1'}}, {messageId: 'heading', data: {tag: 'h3'}}],
        },
        {
            code: template('<span v-if="x"\n      class="inline-flex rounded-full px-1.5 text-xs">x</span>'),
            filename: view,
            errors: [{messageId: 'badge'}],
        },
    ],
})
