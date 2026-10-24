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
  const created = await orders.create(draft.value)
  router.push({name: 'order', params: {id: created.id}})
}
</script>

<template>
  <PanelBox @click="saveAndOpen">{{ draft.name }}</PanelBox>
</template>
`,
        },
        {
            name: 'a button, whose navigation is a design decision',
            filename: 'Row.vue',
            code: `
<script setup lang="ts">
function goBack() {
  router.push({name: 'orders'})
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
  router.push({name: 'order-history'})
}
</script>

<template>
  <DropdownMenuItem @click="openResults">{{ t('orders.history') }}</DropdownMenuItem>
</template>
`,
        },
        {
            name: 'a row that is already a link',
            filename: 'Row.vue',
            code: `
<template>
  <NuxtLink :to="{name: 'order'}" @click="router.push({name: 'order'})">
    {{ order.name }}
  </NuxtLink>
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
  <PanelBox @click="expand">{{ order.name }}</PanelBox>
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
function openOrder() {
  router.push({name: 'order', params: {id: props.order.id}})
}
</script>

<template>
  <PanelBox @click="openOrder">{{ order.name }}</PanelBox>
</template>
`,
            errors: [{messageId: 'link', data: {element: 'PanelBox'}}],
        },
        {
            name: 'a push written in the template',
            filename: 'Row.vue',
            code: `
<template>
  <div @click="router.push({name: 'order'})">{{ order.name }}</div>
</template>
`,
            errors: [{messageId: 'link', data: {element: 'div'}}],
        },
    ],
})
