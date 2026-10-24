/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
import {readdirSync, readFileSync} from 'node:fs'
import {join, relative, sep} from 'node:path'
import {fileURLToPath} from 'node:url'
import tsParser from '@typescript-eslint/parser'
import {parseForESLint} from 'vue-eslint-parser'

/** The frontend's source directory, which the rules read conventions and other components from. */
export const SRC = fileURLToPath(new URL('../../src/', import.meta.url))

const parsed = new Map()

/**
 * A component file parsed the way ESLint parses it, once per process.
 *
 * @param file the absolute path of a `.vue` file
 * @returns the program, carrying `templateBody` for the template, and the file's lines
 */
export function parseVueFile(file) {
    if (!parsed.has(file)) {
        const text = readFileSync(file, 'utf-8')
        const {ast} = parseForESLint(text, {
            parser: tsParser,
            ecmaVersion: 'latest',
            sourceType: 'module',
            filePath: file,
        })
        parsed.set(file, {ast, lines: text.split('\n')})
    }
    return parsed.get(file)
}

/**
 * Every `.vue` file below a directory.
 *
 * @param dir the directory to walk
 * @returns absolute paths
 */
export function vueFilesIn(dir) {
    const found = []
    for (const entry of readdirSync(dir, {withFileTypes: true})) {
        const full = join(dir, entry.name)
        if (entry.isDirectory()) found.push(...vueFilesIn(full))
        else if (entry.name.endsWith('.vue')) found.push(full)
    }
    return found
}

/**
 * Where a file sits below `src/`, with forward slashes.
 *
 * @param file an absolute path
 * @returns the path relative to the source directory
 */
export function sourcePath(file) {
    return relative(SRC, file).split(sep).join('/')
}

/**
 * Whether a file lies in one directory of `src/components/`.
 *
 * @param file an absolute path
 * @param dir the directory name below `components/`
 * @returns true when the file is inside it
 */
export function isInsideComponentDir(file, dir) {
    return sourcePath(file).startsWith(`components/${dir}/`)
}

/**
 * The elements directly below an element, in source order.
 *
 * @param element a template element
 * @returns its child elements
 */
export function childElements(element) {
    return element.children.filter(child => child.type === 'VElement')
}

/**
 * Visits every element below a root, depth first, with its depth. The root has depth 1.
 *
 * @param root the element to start from
 * @param visit called with each element and its depth; returning false skips its children
 * @param depth the depth of the root
 */
export function walkElements(root, visit, depth = 1) {
    if (visit(root, depth) === false) return
    for (const child of childElements(root)) walkElements(child, visit, depth + 1)
}

/**
 * A plain attribute of an element.
 *
 * @param element a template element
 * @param name the attribute name
 * @returns the attribute node, or undefined
 */
export function staticAttribute(element, name) {
    return element.startTag.attributes.find(attribute => !attribute.directive && attribute.key.name === name)
}

/**
 * A directive of an element, such as `v-else` or `:class`.
 *
 * @param element a template element
 * @param name the directive name without `v-`, `bind` for `:`
 * @param argument the argument after the colon, if any
 * @returns the directive node, or undefined
 */
export function directive(element, name, argument) {
    return element.startTag.attributes.find(attribute => attribute.directive
        && attribute.key.name.name === name
        && (argument === undefined || attribute.key.argument?.name === argument))
}

/**
 * The text of an element's plain `class` attribute.
 *
 * @param element a template element
 * @returns the classes, or an empty string
 */
export function staticClasses(element) {
    return staticAttribute(element, 'class')?.value?.value ?? ''
}

/**
 * The plain classes of an element and the source of its bound `:class`, as one string, for the
 * checks that care about a class token wherever it is written.
 *
 * @param element a template element
 * @param sourceCode the source the element belongs to, to read the bound expression from
 * @returns the text to test class tokens against
 */
export function anyClasses(element, sourceCode) {
    const bound = directive(element, 'bind', 'class')
    const boundText = bound?.value ? sourceCode.getText(bound.value) : ''
    return `${staticClasses(element)} ${boundText}`
}

/**
 * Whether an element is the other branch of a condition, and so never stands beside the one before.
 *
 * @param element a template element
 * @returns true for `v-else` and `v-else-if`
 */
export function isAlternative(element) {
    return Boolean(directive(element, 'else') || directive(element, 'else-if'))
}
