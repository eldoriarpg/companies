/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {join} from 'node:path'
import tsParser from '@typescript-eslint/parser'
import {RuleTester} from 'eslint'
import vueParser from 'vue-eslint-parser'
import rule from './route-view-content.mjs'

/**
 * Whether the header over a page can say anything is decided by the view the page renders and the
 * components it hands its template to, so the rule follows them as the page would.
 *
 * @vitest-environment node
 */
const PAGE = join(import.meta.dirname, 'fixtures', 'pages', 'src', 'pages', 'orders', 'index.vue')

/** A page of the fixture tree rendering the named view under the given layout. */
function page(view: string, layout = 'default') {
    return `
<script setup lang="ts">
import ${view} from '~/views/${view}.vue'

definePageMeta({
  layout: '${layout}',
  name: 'orders',
})
</script>

<template>
  <${view}/>
</template>
`
}

const tester = new RuleTester({
    languageOptions: {parser: vueParser, parserOptions: {parser: tsParser, sourceType: 'module'}},
})

tester.run('route-view-content', rule, {
    valid: [
        {name: 'a view with a titled ViewContent', filename: PAGE, code: page('KindTitleView')},
        {name: 'a view handing its title down to the component it delegates to', filename: PAGE, code: page('PassingView')},
        {name: 'a view that only forwards the reader', filename: PAGE, code: page('ForwardingView')},
        {name: 'a page under a layout that is not titled', filename: PAGE, code: page('BareView', 'editor')},
    ],
    invalid: [
        {
            name: 'a view that never reaches a ViewContent',
            filename: PAGE,
            code: page('BareView'),
            errors: [{messageId: 'noViewContent', data: {name: 'orders', path: 'orders'}}],
        },
        {
            name: 'a view whose delegate takes its title from a prop nobody passes',
            filename: PAGE,
            code: page('DelegatingView'),
            errors: [{messageId: 'untitled', data: {name: 'orders', path: 'orders', delegate: 'OrderShell', missing: '`heading`'}}],
        },
    ],
})
