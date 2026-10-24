/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import tsParser from '@typescript-eslint/parser'
import {RuleTester} from 'eslint'
import rule from './max-function-lines.mjs'

/**
 * A long function is reported and a composable wrapper is not.
 *
 * @vitest-environment node
 */
const tester = new RuleTester({languageOptions: {parser: tsParser}})

/**
 * A function of the given name whose body spans the given number of lines in all.
 *
 * @param head what opens the function up to and including its brace
 * @param lines how many lines it spans
 * @returns the source
 */
function spanning(head: string, lines: number): string {
    return `${head}\n${'  call()\n'.repeat(lines - 2)}}`
}

tester.run('max-function-lines', rule, {
    valid: [
        {code: spanning('function short() {', 5), options: [{max: 5}]},
        {code: spanning('export function useThing() {', 12), options: [{max: 5}], filename: '/app/src/composables/useThing.ts'},
        {code: spanning('export function useThing() {', 12), options: [{max: 5}], filename: '/app/src/views/list/useThing.ts'},
    ],
    invalid: [
        {
            code: spanning('function long() {', 6),
            options: [{max: 5}],
            errors: [{messageId: 'tooLong', data: {name: 'long', lines: 6, max: 5}}],
        },
        {
            code: spanning('const load = async () => {', 6),
            options: [{max: 5}],
            errors: [{messageId: 'tooLong', data: {name: 'load', lines: 6, max: 5}}],
        },
        {
            code: spanning('export function useThing() {', 12),
            options: [{max: 5}],
            filename: '/app/src/views/list/ListView.ts',
            errors: [{messageId: 'tooLong'}],
        },
        {
            code: `export function useThing() {\n${spanning('  function inner() {', 6)}\n}`,
            options: [{max: 5}],
            filename: '/app/src/composables/useThing.ts',
            errors: [{messageId: 'tooLong', data: {name: 'inner', lines: 6, max: 5}}],
        },
    ],
})
