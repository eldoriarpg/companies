/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
import rule from './no-stacked-inline-text.mjs'
import {vueRuleTester} from './rule-tester'

/**
 * Only two inline typography components with nothing but a line break between them glue together,
 * and only where nothing else already separates them.
 *
 * @vitest-environment node
 */
const options = [{components: {DetailLabel: {tagProperty: null}, MutedText: {tagProperty: 'tag'}}}]

const stacked = (first: string, second: string, parent = '<div>') =>
    `<template>${parent}\n  ${first}\n  ${second}\n</div></template>`

vueRuleTester().run('no-stacked-inline-text', rule, {
    valid: [
        {code: stacked('<DetailLabel>a</DetailLabel>', '<DetailLabel>b</DetailLabel>', '<div class="flex gap-2">'), options},
        {code: stacked('<DetailLabel>a</DetailLabel>', '<DetailLabel>b</DetailLabel>', '<div class="space-x-2">'), options},
        {code: '<template><div><DetailLabel>a</DetailLabel> <DetailLabel>b</DetailLabel></div></template>', options},
        {code: stacked('<DetailLabel v-if="x">a</DetailLabel>', '<DetailLabel v-else>b</DetailLabel>'), options},
        {code: stacked('<MutedText tag="p">a</MutedText>', '<MutedText tag="p">b</MutedText>'), options},
        {code: stacked('<MutedText :tag="tag">a</MutedText>', '<MutedText>b</MutedText>'), options},
        {code: stacked('<DetailLabel class="block">a</DetailLabel>', '<DetailLabel>b</DetailLabel>'), options},
        {code: stacked('<DetailLabel>a</DetailLabel>', '<DetailLabel class="ml-2">b</DetailLabel>'), options},
        {code: stacked('<DetailLabel>a</DetailLabel>', '<span>b</span>'), options},
        {code: '<template><div>\n  <DetailLabel>a</DetailLabel>\n  {{ x }}\n  <DetailLabel>b</DetailLabel>\n</div></template>', options},
        {code: '<template><div>\n  <DetailLabel>a</DetailLabel>\n  <!-- x -->\n  <DetailLabel>b</DetailLabel>\n</div></template>', options},
    ],
    invalid: [
        {
            code: stacked('<DetailLabel>a</DetailLabel>', '<DetailLabel>b</DetailLabel>'),
            options,
            errors: [{messageId: 'stacked', data: {current: 'DetailLabel', previous: 'DetailLabel'}}],
        },
        {
            code: stacked('<MutedText>a</MutedText>', '<MutedText tag="span">b</MutedText>'),
            options,
            errors: [{messageId: 'stacked', data: {current: 'MutedText', previous: 'MutedText'}}],
        },
    ],
})
