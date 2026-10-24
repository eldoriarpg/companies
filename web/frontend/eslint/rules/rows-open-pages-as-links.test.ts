/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import tsParser from '@typescript-eslint/parser'
import {RuleTester} from 'eslint'
import vueParser from 'vue-eslint-parser'
import rule from './rows-open-pages-as-links.mjs'

/**
 * What the rule catches and what it leaves alone is the whole of its worth.
 *
 * @vitest-environment node
 *
 * It decides what a sweep over every list in the product has to change, and both halves are easy to
 * get wrong in a way that only shows up much later: too eager and it asks for links where going on
 * after an act is meant, too shy and the lists it was written for stay unreachable by address.
 */
const tester = new RuleTester({
    languageOptions: {parser: vueParser, parserOptions: {parser: tsParser, sourceType: 'module'}},
})

tester.run('rows-open-pages-as-links', rule, {
    valid: [
        {
            name: 'a handler that saves before it navigates',
            filename: 'Row.vue',
            code: `
<script setup lang="ts">
async function saveAndOpen() {
  const created = await quiz.createCatalog(draft.value)
  router.push({name: 'quiz-catalog', params: {id: created.id}})
}
</script>

<template>
  <NeutralContainer @click="saveAndOpen">{{ draft.name }}</NeutralContainer>
</template>
`,
        },
        {
            name: 'a button, whose navigation is a design decision',
            filename: 'Row.vue',
            code: `
<script setup lang="ts">
function goBack() {
  router.push({name: 'quiz-catalogs'})
}
</script>

<template>
  <SecondaryButton @click="goBack">{{ t('common.back') }}</SecondaryButton>
</template>
`,
        },
        {
            name: 'an entry of a menu, which is a button under its name',
            filename: 'Row.vue',
            code: `
<script setup lang="ts">
function openResults() {
  router.push({name: 'quiz-test-results'})
}
</script>

<template>
  <DropdownMenuItem @click="openResults">{{ t('quiz.results') }}</DropdownMenuItem>
</template>
`,
        },
        {
            name: 'a row that is already a link',
            filename: 'Row.vue',
            code: `
<template>
  <RowLink :to="{name: 'quiz-catalog'}" @click="router.push({name: 'quiz-catalog'})">
    {{ catalog.name }}
  </RowLink>
</template>
`,
        },
        {
            name: 'a handler that does something other than navigate',
            filename: 'Row.vue',
            code: `
<script setup lang="ts">
function expand() {
  open.value = !open.value
}
</script>

<template>
  <NeutralContainer @click="expand">{{ catalog.name }}</NeutralContainer>
</template>
`,
        },
    ],
    invalid: [
        {
            name: 'a row whose click handler is nothing but a push',
            filename: 'Row.vue',
            code: `
<script setup lang="ts">
function openCatalog() {
  router.push({name: 'quiz-catalog', params: {id: props.catalog.id}})
}
</script>

<template>
  <NeutralContainer @click="openCatalog">{{ catalog.name }}</NeutralContainer>
</template>
`,
            errors: [{messageId: 'link', data: {element: 'NeutralContainer'}}],
        },
        {
            name: 'a push written in the template',
            filename: 'Row.vue',
            code: `
<template>
  <div @click="router.push({name: 'quiz-catalog'})">{{ catalog.name }}</div>
</template>
`,
            errors: [{messageId: 'link', data: {element: 'div'}}],
        },
    ],
})
