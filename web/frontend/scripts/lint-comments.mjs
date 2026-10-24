/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */

/**
 * Comment Linter
 *
 * Code documents itself in this project. Clear names, small functions and honest structure carry
 * the meaning, and a passage that needs a comment beside it to be understood wants rewriting rather
 * than annotating.
 *
 * So only doc comments are allowed: the block that introduces a class, a method, a function or a
 * constant, and says what it is for. Everything else goes. Where a rationale genuinely has to be
 * written down it belongs in the doc comment of the thing it is about. The one exception is a TODO
 * marking a real gap: something left unimplemented or knowingly missing.
 *
 * Every source below the frontend directory is held to it: the application, its tests, the
 * end-to-end stories, the ESLint plugin, the scripts and the configuration files. Template
 * comments in `.vue` files fail as well.
 *
 * Exit code 1 if a comment is found that is neither a doc comment nor a TODO.
 */
import {readFileSync} from 'node:fs'
import {BOLD, GREEN, RESET, createReporter, frontendFiles} from './lint-utils.mjs'

const reporter = createReporter()

/** What is scanned: every source a person wrote below the frontend directory. */
const EXTENSIONS = ['.ts', '.vue', '.js', '.mjs', '.css']

/**
 * A line that opens a block comment which is a doc comment, and so may stand. A licence header is a
 * block comment at the top of the file that every source here carries, and stands as well.
 */
const DOC_OPENER = /^\s*\/\*\*/
const BLOCK_OPENER = /^\s*\/\*/
const LICENCE_MARKER = /SPDX-License-Identifier/

const TODO = /^\s*(\/\/|\/\*|\*)\s*TODO\b/

/** Where a comment opens in a Vue template, which is markup and holds no doc comments at all. */
const TEMPLATE_COMMENT = /<!--/

/**
 * Where a comment starts on a line, telling a `//` inside a string or a regular expression from one
 * that starts a comment.
 *
 * <p>Walking the line character by character is the only way to tell: an address in a string, a
 * protocol in a template literal and a character class in a regular expression all carry two
 * slashes and none of them is a comment.
 *
 * @param line one line of code
 * @returns the column the comment starts at, or -1
 */
function commentStart(line) {
    let quote = null
    for (let i = 0; i < line.length; i++) {
        const c = line[i]
        if (quote) {
            if (c === '\\') i++
            else if (c === quote) quote = null
            continue
        }
        if (c === '\\') {
            i++
            continue
        }
        if (c === '"' || c === "'" || c === '`') {
            quote = c
            continue
        }
        if (c === '/' && line[i + 1] === '/') return i
        if (c === '/' && line[i + 1] === '*') return i
    }
    return -1
}

/**
 * Reports every comment of one file that is neither a doc comment, a licence header nor a TODO.
 *
 * @param file the absolute path
 */
function check(file) {
    const lines = readFileSync(file, 'utf-8').split('\n')
    const found = []
    const markup = file.endsWith('.vue')
    let inBlock = false
    let inTemplateComment = false
    let inCode = !markup

    for (let i = 0; i < lines.length; i++) {
        const line = lines[i]

        if (inTemplateComment) {
            if (line.includes('-->')) inTemplateComment = false
            continue
        }

        if (markup && /^\s*<(script|style)\b/.test(line)) {
            inCode = true
            continue
        }
        if (markup && /^\s*<\/(script|style)>/.test(line)) {
            inCode = false
            continue
        }

        if (markup && !inCode && TEMPLATE_COMMENT.test(line)) {
            inTemplateComment = !line.slice(line.indexOf('<!--')).includes('-->')
            found.push({line: i + 1, message: 'Template comment. Name the block with a component or a class instead.'})
            continue
        }

        if (inBlock) {
            if (line.includes('*/')) inBlock = false
            continue
        }

        if (!inCode) continue

        if (TODO.test(line)) continue

        if (DOC_OPENER.test(line)) {
            inBlock = !line.includes('*/')
            continue
        }

        if (BLOCK_OPENER.test(line) || LICENCE_MARKER.test(line)) {
            const licence = LICENCE_MARKER.test(line) || LICENCE_MARKER.test(lines[i + 1] ?? '')
            inBlock = !line.includes('*/')
            if (!licence) {
                found.push({
                    line: i + 1,
                    message: 'Block comment that is not a doc comment. Put the reasoning in the doc comment of'
                        + ' the class or function it belongs to, or rewrite the passage so it needs none.',
                })
            }
            continue
        }

        const start = commentStart(line)
        if (start === -1) continue
        if (line.slice(start).startsWith('/*')) {
            inBlock = !line.slice(start).includes('*/')
            found.push({line: i + 1, message: 'Inline block comment. Only doc comments are allowed.'})
            continue
        }
        found.push({
            line: i + 1,
            message: `Line comment at column ${start + 1}. Only doc comments and TODO markers are allowed;`
                + ' put the reasoning in the doc comment of the enclosing class or function.',
        })
    }

    for (const one of found) {
        reporter.error(file, one.line, one.message)
    }

}

/** The generated API types, whose comments are the generator's and say what the backend declares. */
const GENERATED = '/src/api/generated/'

const scanned = frontendFiles(EXTENSIONS).filter(file => !file.includes(GENERATED))
scanned.forEach(check)

if (reporter.errors.length === 0 && reporter.warnings.length === 0) {
    console.log(`\n${GREEN}${BOLD}Comment lint passed.${RESET} ${scanned.length} files checked.\n`)
} else {
    reporter.print()
    reporter.exit()
}
