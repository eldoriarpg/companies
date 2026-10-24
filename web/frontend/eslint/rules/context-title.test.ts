/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
import {join} from 'node:path'
import tsParser from '@typescript-eslint/parser'
import {RuleTester} from 'eslint'
import vueParser from 'vue-eslint-parser'
import {describe, expect, it} from 'vitest'
import rule, {isConstantTitle, viewContentTitle, writesItsOwnHead} from './context-title.mjs'
import {parseComponent} from './sfc.mjs'

/**
 * Telling a title that can name what a page shows from one that never could.
 *
 * @vitest-environment node
 *
 * <p>The whole rule rests on this one judgement, and both halves of it are easy to get wrong in a
 * way that only shows much later: too eager and it refuses the constant a page rightly falls back
 * to while its record is on its way, too shy and every catalogue in the product goes on being
 * called "Catalogue".
 */
describe('a title that says which thing', () => {
    function titleOf(template: string) {
        return viewContentTitle(parseComponent(template).template)
    }

    function bound(expression: string) {
        return titleOf(`<template><ViewContent :title="${expression}"/></template>`)!
    }

    it('reads a bound title off the opening tag, however many lines it is written over', () => {
        const found = titleOf(`
<template>
    <ViewContent
        :title="pageTitle"
        :subtitle="pageSubtitle"
    >
        <p>Hallo</p>
    </ViewContent>
</template>
`)

        expect(found?.written).toBe(false)
        expect(found?.expression).toMatchObject({type: 'Identifier', name: 'pageTitle'})
    })

    it('reads a title written out rather than bound', () => {
        const found = titleOf('<template><ViewContent title="Catalogue"/></template>')

        expect(found?.written).toBe(true)
        expect(found?.expression).toBe('Catalogue')
    })

    it('finds nothing where the page hands no title', () => {
        expect(titleOf('<template><div>Hallo</div></template>')).toBe(null)
    })

    /**
     * A public page's heading and its tab are two different strings, and only the head holds the
     * second. Reading the heading there would refuse "Neuigkeiten", which is what that page should
     * be headed.
     */
    it('stands aside where the page names its own tab', () => {
        const script = (body: string) => parseComponent(`<script setup lang="ts">\n${body}\n</script>`).script

        expect(writesItsOwnHead(script('useHead(computed(() => ({title: titleWithStation(f.name, s)})))'))).toBe(true)
        expect(writesItsOwnHead(script('const pageTitle = computed(() => file.value?.name)'))).toBe(false)
    })

    it('calls a translated constant what it is', () => {
        expect(isConstantTitle(bound("t('pages.quiz-catalog-detail.title')"))).toBe(true)
    })

    /** A title written out rather than bound cannot change, whatever it happens to say. */
    it('calls a written out title a constant whatever is in it', () => {
        expect(isConstantTitle(titleOf('<template><ViewContent title="Catalogue"/></template>')!)).toBe(true)
    })

    /** The shape every converted page uses: the record where there is one, the constant until then. */
    it('accepts a title falling back to the constant while the record is on its way', () => {
        expect(isConstantTitle(bound("catalog?.name || t('pages.quiz-catalog-detail.title')"))).toBe(false)
    })

    it('accepts a title built out of more than one thing the page loaded', () => {
        expect(isConstantTitle(bound('`${board.shortKey}-${ticket.ticketNumber} ${ticket.title}`'))).toBe(false)
    })

    /** A constant is a constant however it is spelled, so a joined one must not pass for a name. */
    it('is not fooled by a constant spelled as two strings', () => {
        expect(isConstantTitle(bound("'Cata' + 'logue'"))).toBe(true)
    })
})

const PAGES = join(import.meta.dirname, 'fixtures', 'pages', 'src', 'pages')

/** A page of the fixture tree rendering the named view. */
function page(view: string) {
    return `
<script setup lang="ts">
import ${view} from '~/views/${view}.vue'

definePageMeta({
  layout: 'default',
  name: 'catalog-detail',
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

tester.run('context-title', rule, {
    valid: [
        {name: 'a page naming the catalogue it shows', filename: join(PAGES, 'catalogs', '[id].vue'), code: page('NamedTitleView')},
        {name: 'a page whose address points at no one thing', filename: join(PAGES, 'catalogs', 'index.vue'), code: page('KindTitleView')},
        {name: 'a page reached with and without the parameter', filename: join(PAGES, 'catalogs', '[[id]].vue'), code: page('KindTitleView')},
        {name: 'a public page naming its own tab', filename: join(PAGES, 'blog', '[id].vue'), code: page('OwnHeadView')},
    ],
    invalid: [
        {
            name: 'a page pointing at one catalogue and titled after the kind',
            filename: join(PAGES, 'catalogs', '[id].vue'),
            code: page('KindTitleView'),
            errors: [{messageId: 'kind', data: {path: 'catalogs/:id', shown: ":title=\"t('pages.catalog.title')\""}}],
        },
    ],
})
