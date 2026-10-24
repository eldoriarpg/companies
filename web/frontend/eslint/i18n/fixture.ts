/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {readFileSync} from 'node:fs'
import {fileURLToPath} from 'node:url'
import {RuleTester} from 'eslint'
import * as localeParser from './locale-parser.mjs'

/** The fixture tree the translation rules are tested against: sources and locales. */
export const FIXTURE = fileURLToPath(new URL('./fixtures/', import.meta.url))

/**
 * The absolute path of a file in the fixture tree.
 *
 * @param path the path below the fixture root
 * @returns the absolute path
 */
export function fixture(path: string): string {
    return `${FIXTURE}${path}`
}

/**
 * The source of a fixture file, which a test lints as the file it stands for.
 *
 * @param path the path below the fixture root
 * @returns its text
 */
export function fixtureSource(path: string): string {
    return readFileSync(fixture(path), 'utf-8')
}

/** The English files of the fixture, as the rules' options name them. */
export const BASE = [
    {file: fixture('src/i18n/en.ts')},
    {file: fixture('src/i18n/errors/forms.ts'), prefix: 'errors'},
    {file: fixture('src/i18n/errors/general.ts'), prefix: 'errors'},
    {file: fixture('src/i18n/en.help.ts'), prefix: 'help'},
]

/**
 * A rule tester reading locale modules through the locale parser, as the project's config does.
 *
 * @returns the tester
 */
export function localeRuleTester(): RuleTester {
    return new RuleTester({languageOptions: {parser: localeParser}})
}
