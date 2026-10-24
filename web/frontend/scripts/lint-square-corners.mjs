/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */

/**
 * Square Corners Linter
 *
 * The dashboard is a serious tool, and its corners are square everywhere: no rounded edge on any box, button, bar,
 * badge or input. Tailwind's radius scale is switched off in `src/style.css`, so a `rounded` class draws nothing, but
 * a class that draws nothing still reads as a promise. This refuses every `rounded` utility and every `border-radius`
 * other than zero in the application's sources, the stylesheet included.
 *
 * Exit code 1 if a rounded corner is found.
 */
import {readFileSync} from 'node:fs'
import {BOLD, GREEN, RESET, SRC, createReporter, frontendFiles} from './lint-utils.mjs'

const reporter = createReporter()

/** A Tailwind radius utility, with or without a variant in front. */
const ROUNDED_CLASS = /(^|[\s"'`:])rounded(-[a-z0-9-[\]./]+)?(?=[\s"'`]|$)/

/** A radius declared in CSS or an inline style that is not zero. */
const RADIUS = /border-([a-z]+-)*radius\s*:(?!\s*0(px)?\s*[;}"'!])/

/** A line of a doc comment, which may say the word without drawing anything. */
const COMMENT_LINE = /^\s*(\/\*\*?|\*|\/\/)/

/** What is scanned: the application's sources and its stylesheet. */
const EXTENSIONS = ['.ts', '.vue', '.css']

/**
 * Reports every line of one file that rounds a corner.
 *
 * @param file the absolute path
 */
function check(file) {
    const lines = readFileSync(file, 'utf-8').split('\n')
    lines.forEach((line, index) => {
        if (COMMENT_LINE.test(line)) return
        if (ROUNDED_CLASS.test(line)) reporter.error(file, index + 1, 'Rounded corner class. Corners are square everywhere.')
        else if (RADIUS.test(line)) reporter.error(file, index + 1, 'Border radius other than zero. Corners are square everywhere.')
    })
}

const scanned = frontendFiles(EXTENSIONS, SRC).filter(file => !file.endsWith('.test.ts'))
scanned.forEach(check)

if (reporter.errors.length === 0) {
    console.log(`\n${GREEN}${BOLD}Square corner lint passed.${RESET} ${scanned.length} files round nothing.\n`)
} else {
    reporter.print()
    reporter.exit()
}
