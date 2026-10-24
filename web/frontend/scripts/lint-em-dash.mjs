/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */

/**
 * Em Dash Linter
 *
 * The em dash is forbidden in this project. The rule exists so the text the application ships reads
 * the same everywhere, and a rule nobody checks is a rule that comes back. Every text file below the
 * frontend directory is scanned, translations and doc comments included.
 *
 * Exit code 1 if the character is found anywhere.
 */
import {readFileSync} from 'node:fs'
import {BOLD, GREEN, RESET, createReporter, frontendFiles} from './lint-utils.mjs'

const reporter = createReporter()

/** Built from its code point so this file does not trip over itself. */
const EM_DASH = String.fromCharCode(0x2014)

/**
 * The names markup gives the same character, assembled here for the same reason.
 *
 * <p>A page can name the dash instead of carrying it, and it then reaches the reader as the dash
 * while the file itself holds none. A check that looks only for the character passes such a page.
 */
const ENTITIES = ['mdash', '#8212', '#x2014', '#X2014'].map(name => `&${name};`)

const SPELLINGS = [EM_DASH, ...ENTITIES]

/** What is scanned: every text file a person wrote below the frontend directory. */
const EXTENSIONS = ['.ts', '.vue', '.js', '.mjs', '.css', '.json', '.md', '.html']

/**
 * Reports every line of one file that carries the dash in any spelling.
 *
 * @param file the absolute path
 */
function check(file) {
    const lines = readFileSync(file, 'utf-8').split('\n')
    for (let i = 0; i < lines.length; i++) {
        for (const spelling of SPELLINGS) {
            const column = lines[i].indexOf(spelling)
            if (column === -1) continue
            reporter.error(
                file,
                i + 1,
                `Em dash at column ${column + 1}. Use a comma, a colon, parentheses, a full stop or a spaced hyphen instead.`,
            )
            break
        }
    }
}

const scanned = frontendFiles(EXTENSIONS).filter(file => !file.endsWith('package-lock.json'))
scanned.forEach(check)

if (reporter.errors.length === 0 && reporter.warnings.length === 0) {
    console.log(`\n${GREEN}${BOLD}Em dash lint passed.${RESET} ${scanned.length} files carry none.\n`)
} else {
    reporter.print()
    reporter.exit()
}
