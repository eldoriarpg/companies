/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
import {readdirSync} from 'node:fs'
import {join, relative} from 'node:path'
import {fileURLToPath} from 'node:url'

/** The frontend directory, which every linter of the registry is run from and reports against. */
export const ROOT = fileURLToPath(new URL('..', import.meta.url))

/** The application's sources. */
export const SRC = join(ROOT, 'src')

/** Directories below the frontend that hold what a tool wrote rather than what a person did. */
const GENERATED_DIRS = new Set(['node_modules', '.nuxt', '.output', 'coverage', 'dist', 'report', 'results'])

export const RED = '\x1b[31m'
export const GREEN = '\x1b[32m'
export const YELLOW = '\x1b[33m'
export const RESET = '\x1b[0m'
export const BOLD = '\x1b[1m'

/**
 * Every file below a directory with the given extension.
 *
 * @param dir the directory to walk
 * @param ext the extension, dot included
 * @returns absolute paths
 */
export function walk(dir, ext) {
    const results = []
    for (const entry of readdirSync(dir, {withFileTypes: true})) {
        const full = join(dir, entry.name)
        if (entry.isDirectory()) {
            results.push(...walk(full, ext))
        } else if (entry.name.endsWith(ext)) {
            results.push(full)
        }
    }
    return results
}

/**
 * Every file of the frontend a person wrote with one of the given extensions, the directories tools
 * write into left out.
 *
 * @param extensions the extensions, dot included
 * @param dir where to start, the frontend directory unless told otherwise
 * @returns absolute paths
 */
export function frontendFiles(extensions, dir = ROOT) {
    const results = []
    for (const entry of readdirSync(dir, {withFileTypes: true})) {
        const full = join(dir, entry.name)
        if (entry.isDirectory()) {
            if (!GENERATED_DIRS.has(entry.name)) results.push(...frontendFiles(extensions, full))
        } else if (extensions.some(extension => entry.name.endsWith(extension))) {
            results.push(full)
        }
    }
    return results
}

/**
 * A path as the reports print it, relative to the frontend directory.
 *
 * @param file an absolute path
 * @returns the relative path
 */
export function rel(file) {
    return relative(ROOT, file)
}

/**
 * The template block of a single file component, opening and closing tag included.
 *
 * @param content the component's source
 * @returns the template, or an empty string where there is none
 */
export function extractTemplate(content) {
    const start = content.indexOf('<template>')
    const end = content.lastIndexOf('</template>')
    if (start === -1 || end === -1) return ''
    return content.substring(start, end + '</template>'.length)
}

/**
 * An error and warning collector with grouped, coloured output.
 *
 * @returns the reporter
 */
export function createReporter() {
    const errors = []
    const warnings = []
    const entry = (file, line, msg, category) => ({file: typeof file === 'string' && file.startsWith('/') ? rel(file) : file, line, msg, category})

    return {
        error(file, line, msg, category = '') {
            errors.push(entry(file, line, msg, category))
        },
        warn(file, line, msg, category = '') {
            warnings.push(entry(file, line, msg, category))
        },
        get errors() { return errors },
        get warnings() { return warnings },

        print() {
            printGroup(warnings, YELLOW, 'Warnings')
            printGroup(errors, RED, 'Errors')
        },

        exit() {
            if (errors.length > 0) {
                console.log(`\n${RED}Lint failed with ${errors.length} error(s).${RESET}\n`)
                process.exit(1)
            } else if (warnings.length > 0) {
                console.log(`\n${YELLOW}Lint passed with ${warnings.length} warning(s).${RESET}\n`)
            } else {
                console.log('\nLint passed.\n')
            }
        },
    }
}

/**
 * Prints one group of findings, by category where there is more than one.
 *
 * @param items the findings
 * @param color the colour of the group
 * @param label what the group is called
 */
function printGroup(items, color, label) {
    if (items.length === 0) return
    console.log(`\n${color}${BOLD}${label} (${items.length}):${RESET}`)
    const groups = new Map()
    for (const item of items) {
        const category = item.category || 'Other'
        if (!groups.has(category)) groups.set(category, [])
        groups.get(category).push(item)
    }
    const kind = label.toLowerCase().slice(0, -1)
    for (const [category, entries] of groups) {
        if (groups.size > 1) console.log(`\n  ${BOLD}${category}${RESET}`)
        for (const item of entries) {
            const location = item.file ? `${item.file}${item.line > 0 ? `:${item.line}` : ''}: ` : ''
            console.log(`    ${color}${kind}${RESET} ${location}${item.msg}`)
        }
    }
}
