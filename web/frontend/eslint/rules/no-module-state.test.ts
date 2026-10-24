/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import tsParser from '@typescript-eslint/parser'
import {RuleTester} from 'eslint'
import rule from './no-module-state.mjs'
import {sourceFile, vueRuleTester} from './rule-tester'

/**
 * Mutable state at module level is reported, and so is shared state a server render could leak or
 * start differently from the browser. State inside functions, constant tables and read-only
 * collections are left alone.
 *
 * @vitest-environment node
 */
const tester = new RuleTester({languageOptions: {parser: tsParser}})

const composable = sourceFile('composables/useThing.ts')
const stateModule = sourceFile('util/thingState.ts')

tester.run('no-module-state', rule, {
    valid: [
        {code: "const LIMIT = 20\nconst NAMES = ['a', 'b']\nconst TABLE = {a: 1, b: [2, 3]}", filename: stateModule},
        {code: "const METHODS: ReadonlySet<string> = new Set(['post', 'put'])", filename: stateModule},
        {code: "const BY_ID: ReadonlyMap<number, string> = new Map([[1, 'a']])", filename: stateModule},
        {code: "const TABLE = {checks: new Map([[1, true]]) as ReadonlyMap<number, boolean>}", filename: stateModule},
        {code: 'const NONE: readonly string[] = []\nconst EMPTY = {} as const', filename: stateModule},
        {code: 'const cache = new WeakMap<object, string>()\nconst seen = new WeakSet<object>()', filename: stateModule},
        {code: "import {ref} from 'vue'\nexport function useThing() {\n  let count = 0\n  const open = ref(false)\n  const list = new Map()\n  return {open, list, count}\n}", filename: composable},
        {code: "export function useThing() {\n  return useState('useThing', () => false)\n}", filename: composable},
        {code: "export function useThing() {\n  return useState<string[]>('useThing.list', () => [])\n}", filename: composable},
        {code: "export function thingState() {\n  return useState('thingState.counts', () => ({open: 0, items: [], byId: new Map()}))\n}", filename: stateModule},
        {code: 'const BASE = 50\nconst layers = browserShallowRef({open: 0, top: BASE})', filename: stateModule},
        {code: 'const open = browserRef(false)\nconst list = browserRef<string[]>([])\nconst gone = browserRef(undefined)', filename: stateModule},
        {code: "import {ref} from './fakeVue'\nconst open = ref(false)", filename: stateModule},
        {code: 'export const handler = () => {\n  const holder: string[] = []\n  return holder\n}', filename: stateModule},
    ],
    invalid: [
        {
            code: "import {ref, computed} from 'vue'\nconst open = ref(false)\nexport const label = computed(() => String(open.value))",
            filename: stateModule,
            errors: [{messageId: 'reactive', data: {name: 'ref'}}, {messageId: 'reactive', data: {name: 'computed'}}],
        },
        {
            code: 'const state = reactive({open: false})\nconst view = shallowReadonly(state)',
            filename: stateModule,
            errors: [{messageId: 'reactive', data: {name: 'reactive'}}, {messageId: 'reactive', data: {name: 'shallowReadonly'}}],
        },
        {
            code: "import {ref} from 'vue'\nexport const holder = {open: ref(false)}",
            filename: stateModule,
            errors: [{messageId: 'reactive', data: {name: 'ref'}}],
        },
        {
            code: 'let count = 0\nexport var flag = false',
            filename: stateModule,
            errors: [{messageId: 'binding', data: {kind: 'let'}}, {messageId: 'binding', data: {kind: 'var'}}],
        },
        {
            code: "const METHODS = new Set(['post'])\nconst BY_ID: Map<number, string> = new Map()",
            filename: stateModule,
            errors: [
                {messageId: 'collection', data: {type: 'Set', readonly: 'ReadonlySet'}},
                {messageId: 'collection', data: {type: 'Map', readonly: 'ReadonlyMap'}},
            ],
        },
        {
            code: 'const TABLE = {checks: new Map([[1, true]])}',
            filename: stateModule,
            errors: [{messageId: 'collection', data: {type: 'Map', readonly: 'ReadonlyMap'}}],
        },
        {
            code: 'const queue: string[] = []\nconst byKey: Record<string, number> = {}',
            filename: stateModule,
            errors: [{messageId: 'empty', data: {what: 'array'}}, {messageId: 'empty', data: {what: 'object'}}],
        },
        {
            code: "export function useThing() {\n  return useState('open', () => false)\n}",
            filename: composable,
            errors: [{messageId: 'key', data: {base: 'useThing'}}],
        },
        {
            code: "export function useThing(id: string) {\n  return useState(`useThing.${id}`, () => false)\n}",
            filename: composable,
            errors: [{messageId: 'key', data: {base: 'useThing'}}],
        },
        {
            code: "export function useThing() {\n  return useState('useThingy', () => false)\n}",
            filename: composable,
            errors: [{messageId: 'key', data: {base: 'useThing'}}],
        },
        {
            code: "export function useThing() {\n  return useState('useThing', () => localStorage.getItem('open') === '1')\n}",
            filename: composable,
            errors: [{messageId: 'initial'}],
        },
        {
            code: "const stored = getItem('station')\nexport function useThing() {\n  return useState('useThing', () => stored)\n}",
            filename: composable,
            errors: [{messageId: 'initial'}],
        },
        {
            code: 'const wide = browserRef(window.innerWidth < 768)',
            filename: stateModule,
            errors: [{messageId: 'initial'}],
        },
    ],
})

vueRuleTester().run('no-module-state in components', rule, {
    valid: [
        {
            code: "<script setup lang=\"ts\">\nimport {ref} from 'vue'\nlet clicks = 0\nconst open = ref(false)\nconst seen = new Set()\n</script>\n<template><div/></template>",
            filename: sourceFile('components/Thing.vue'),
        },
        {
            code: "<script setup lang=\"ts\">\nconst open = useState('Thing.open', () => false)\n</script>\n<template><div/></template>",
            filename: sourceFile('components/Thing.vue'),
        },
    ],
    invalid: [
        {
            code: "<script lang=\"ts\">\nimport {ref} from 'vue'\nconst shared = ref(0)\n</script>\n<script setup lang=\"ts\">\nconst own = ref(0)\n</script>\n<template><div/></template>",
            filename: sourceFile('components/Thing.vue'),
            errors: [{messageId: 'reactive', data: {name: 'ref'}, line: 3}],
        },
        {
            code: "<script setup lang=\"ts\">\nconst open = useState('open', () => false)\n</script>\n<template><div/></template>",
            filename: sourceFile('components/Thing.vue'),
            errors: [{messageId: 'key', data: {base: 'Thing'}}],
        },
    ],
})
