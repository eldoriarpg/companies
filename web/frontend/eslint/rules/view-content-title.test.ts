/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import tsParser from '@typescript-eslint/parser'
import {RuleTester} from 'eslint'
import vueParser from 'vue-eslint-parser'
import rule from './view-content-title.mjs'

/**
 * A view titles its page once, through its `ViewContent`, and a second title above it is the
 * mistake the header chrome was introduced to end.
 *
 * @vitest-environment node
 */
const tester = new RuleTester({
    languageOptions: {parser: vueParser, parserOptions: {parser: tsParser, sourceType: 'module'}},
})

tester.run('view-content-title', rule, {
    valid: [
        {
            name: 'a view titled through its ViewContent',
            filename: 'OrdersView.vue',
            code: '<template><ViewContent :title="order.name"><p>{{ order.intro }}</p></ViewContent></template>',
        },
        {
            name: 'a section header introducing a part further down',
            filename: 'OrdersView.vue',
            code: '<template><ViewContent title="Orders"><p>{{ intro }}</p><SectionHeader>Lines</SectionHeader></ViewContent></template>',
        },
        {
            name: 'a component without a ViewContent of its own',
            filename: 'OrderCard.vue',
            code: '<template><div><SectionHeader>Lines</SectionHeader></div></template>',
        },
    ],
    invalid: [
        {
            name: 'a ViewContent without a title',
            filename: 'OrdersView.vue',
            code: '<template><ViewContent><p>{{ intro }}</p></ViewContent></template>',
            errors: [{messageId: 'missingTitle'}],
        },
        {
            name: 'a view opening with a header of its own',
            filename: 'OrdersView.vue',
            code: '<template><ViewContent title="Orders"><div><PageHeader>Orders</PageHeader></div></ViewContent></template>',
            errors: [{messageId: 'ownTitle'}],
        },
    ],
})
