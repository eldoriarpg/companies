/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
import {join} from 'node:path'
import tsParser from '@typescript-eslint/parser'
import {RuleTester} from 'eslint'
import vueParser from 'vue-eslint-parser'
import {SRC} from './vue-template.mjs'

/**
 * A rule tester reading single file components the way the project's ESLint config reads them.
 *
 * @returns the tester
 */
export function vueRuleTester(): RuleTester {
    return new RuleTester({
        languageOptions: {
            parser: vueParser,
            parserOptions: {parser: tsParser, ecmaVersion: 'latest', sourceType: 'module'},
        },
    })
}

/**
 * The absolute path of a file below `src/`, for rules that decide by where a file sits.
 *
 * @param path the path below `src/`
 * @returns the absolute path
 */
export function sourceFile(path: string): string {
    return join(SRC, path)
}
