/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {mkdirSync, writeFileSync} from 'node:fs'
import {fileURLToPath} from 'node:url'
import {createJiti} from 'jiti'

const I18N = fileURLToPath(new URL('../src/i18n/', import.meta.url))
const SNAPSHOTS = fileURLToPath(new URL('../node_modules/.cache/companies-eslint/locales/', import.meta.url))

/** The languages of the application, each a module under `src/i18n` named by its code. */
export const LANGUAGES = ['en', 'de', 'fr']

/**
 * The locale files as `@intlify/eslint-plugin-vue-i18n` reads them.
 *
 * <p>The plugin loads messages from JSON, YAML or CommonJS files only, and the locales here are
 * TypeScript modules, one per language. So the modules are evaluated as the application sees them
 * and written out as one JSON file per language for the plugin's `localeDir`.
 *
 * @returns the glob the plugin's `localeDir` setting takes
 */
export async function localeSnapshots() {
    const jiti = createJiti(import.meta.url, {moduleCache: false})
    mkdirSync(SNAPSHOTS, {recursive: true})
    for (const language of LANGUAGES) {
        const messages = await jiti.import(`${I18N}${language}.ts`, {default: true})
        writeFileSync(`${SNAPSHOTS}${language}.json`, JSON.stringify(messages))
    }
    return `${SNAPSHOTS}*.json`
}

/**
 * The locale modules themselves, as a glob for the plugin's `localeDir`.
 *
 * <p>The plugin's rules for locale files act only on a file it knows as a locale, and it knows one
 * by finding it in `localeDir`. The locale parser in `i18n/locale-parser.mjs` hands them the file.
 *
 * @returns the glob of the language modules
 */
export function localeModules() {
    return `${I18N}{${LANGUAGES.join(',')}}.ts`
}
