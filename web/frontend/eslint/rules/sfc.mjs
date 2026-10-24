/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
import {existsSync, readFileSync, readdirSync} from 'node:fs'
import {basename, dirname, join, relative, resolve, sep} from 'node:path'
import tsParser from '@typescript-eslint/parser'
import {parseForESLint} from 'vue-eslint-parser'

/**
 * What the rules about pages and views need to know about a component or a page, read through
 * the same parsers ESLint uses for the file being linted.
 *
 * <p>A rule about a page looks at the view the page renders, and a rule about a view follows the
 * component it delegates to, so other files have to be read as well. Each is parsed once per
 * process and kept, since several pages render the same views.
 */
const parsed = new Map()

/** Keys of an AST node that lead back up or sideways rather than into its children. */
const NOT_CHILDREN = new Set(['parent', 'loc', 'range', 'tokens', 'comments', 'errors', 'templateBody'])

/**
 * A component file, parsed.
 *
 * @param file absolute path of a `.vue` file
 * @returns `{script, template, text}`, the script program, the root template element (or null) and
 *          the source, or null when the file does not exist
 */
export function readComponent(file) {
    if (parsed.has(file)) return parsed.get(file)
    const component = existsSync(file) ? parseComponent(readFileSync(file, 'utf-8'), file) : null
    parsed.set(file, component)
    return component
}

/**
 * A component's source, parsed.
 *
 * @param text the source of a `.vue` file
 * @param file the path it is read as
 * @returns `{script, template, text}` as {@link readComponent} answers it
 */
export function parseComponent(text, file = 'Component.vue') {
    const {ast} = parseForESLint(text, {
        parser: tsParser,
        sourceType: 'module',
        ecmaVersion: 'latest',
        filePath: file,
    })
    return {script: ast, template: ast.templateBody ?? null, text}
}

const scripts = new Map()

/**
 * A TypeScript module, parsed.
 *
 * @param file absolute path of a `.ts` file
 * @returns its program, or null when the file does not exist
 */
export function readScript(file) {
    if (!scripts.has(file)) {
        scripts.set(file, existsSync(file) ? parseScript(readFileSync(file, 'utf-8')) : null)
    }
    return scripts.get(file)
}

/**
 * A TypeScript module's source, parsed.
 *
 * @param text the source
 * @returns its program
 */
export function parseScript(text) {
    return tsParser.parse(text, {sourceType: 'module', ecmaVersion: 'latest', range: true, loc: true})
}

/**
 * Visits a node and everything below it, parents before children, in source order.
 *
 * @param node the node to start at
 * @param visit called with each node; returning false skips that node's children
 */
export function walk(node, visit) {
    if (!node || typeof node.type !== 'string') return
    if (visit(node) === false) return
    for (const [key, value] of Object.entries(node)) {
        if (NOT_CHILDREN.has(key)) continue
        if (Array.isArray(value)) value.forEach(child => walk(child, visit))
        else if (value && typeof value.type === 'string') walk(value, visit)
    }
}

/**
 * Every node below a root that satisfies a test, in source order.
 *
 * @param root where to look
 * @param test the predicate
 * @returns the matching nodes
 */
export function findAll(root, test) {
    const found = []
    walk(root, node => {
        if (test(node)) found.push(node)
    })
    return found
}

/**
 * The elements of a template in source order, the root `<template>` itself left out.
 *
 * @param template the root template element
 * @returns every element below it
 */
export function elementsOf(template) {
    if (!template) return []
    return findAll(template, node => node.type === 'VElement' && node !== template)
}

/**
 * The direct child elements of an element, text and comments left out.
 *
 * @param element the parent element
 * @returns its element children
 */
export function childElements(element) {
    return (element?.children ?? []).filter(child => child.type === 'VElement')
}

/**
 * The attribute of an element with the given name, plain (`title="…"`) or bound
 * (`:title="…"`, `v-bind:title="…"`).
 *
 * @param element the element
 * @param name the attribute or prop name
 * @returns `{bound, attribute}` or null
 */
export function attributeOf(element, name) {
    for (const attribute of element.startTag.attributes) {
        if (!attribute.directive && attribute.key.name === name) return {bound: false, attribute}
        if (attribute.directive && attribute.key.name.name === 'bind'
            && attribute.key.argument?.type === 'VIdentifier' && attribute.key.argument.name === name) {
            return {bound: true, attribute}
        }
    }
    return null
}

/**
 * The expression a bound attribute holds, or null where it holds none.
 *
 * @param attribute a directive attribute
 * @returns the expression node
 */
export function boundExpression(attribute) {
    return attribute.value?.type === 'VExpressionContainer' ? attribute.value.expression : null
}

/**
 * The directory holding `pages`, `views` and the rest, worked out from where a page lives.
 *
 * @param file absolute path of a file under `src/pages`
 * @returns the source directory, or null for a file outside it
 */
export function sourceDirOf(file) {
    const source = `${sep}src`
    const at = file.indexOf(`${source}${sep}pages${sep}`)
    return at === -1 ? null : file.slice(0, at + source.length)
}

/**
 * The first call of a function by name anywhere in a program.
 *
 * @param program the script program
 * @param name the callee's name
 * @returns the call expression, or null
 */
export function callOf(program, name) {
    return findAll(program, node => node.type === 'CallExpression'
        && node.callee.type === 'Identifier' && node.callee.name === name)[0] ?? null
}

/**
 * The string value of a property of an object literal, or null.
 *
 * @param object the object expression
 * @param name the property's name
 * @returns the string it holds
 */
export function stringProperty(object, name) {
    const property = object?.properties?.find(candidate => candidate.type === 'Property'
        && !candidate.computed && (candidate.key.name ?? candidate.key.value) === name)
    return property?.value.type === 'Literal' && typeof property.value.value === 'string' ? property.value.value : null
}

/**
 * What a page file under `src/pages` declares about itself.
 *
 * @param program the page's script program
 * @param file absolute path of the page
 * @returns `{name, layout, redirect, path, view, viewImport}` or null where the page has no name
 */
export function pageOf(program, file) {
    const meta = callOf(program, 'definePageMeta')?.arguments[0]
    const name = stringProperty(meta, 'name')
    if (!name) return null
    const viewImport = program.body.find(statement => statement.type === 'ImportDeclaration'
        && statement.specifiers.some(specifier => specifier.type === 'ImportDefaultSpecifier')
        && /^[~@]\/views\//.test(statement.source.value)) ?? null
    const srcDir = sourceDirOf(file)
    return {
        name,
        layout: stringProperty(meta, 'layout') ?? 'default',
        redirect: stringProperty(meta, 'redirect') !== null,
        path: routePath(join(srcDir, 'pages'), file),
        view: viewImport ? resolveComponent(viewImport.source.value, file, srcDir) : null,
        viewImport,
    }
}

/**
 * The route a page answers on, relative to the pages directory.
 *
 * @param pagesDir the `pages` directory
 * @param file the page file
 * @returns the path with `:param`, `:param?` and `:pathMatch(.*)*` for its dynamic segments
 */
function routePath(pagesDir, file) {
    let path = relative(pagesDir, file).split(sep).join('/').replace(/\.vue$/, '')
    if (path.endsWith('/index')) path = path.slice(0, -'/index'.length)
    if (path === 'index') path = ''
    return path.split('/').map(segment => {
        if (segment.startsWith('[...') && segment.endsWith(']')) return ':pathMatch(.*)*'
        if (segment.startsWith('[[') && segment.endsWith(']]')) return `:${segment.slice(2, -2)}?`
        if (segment.startsWith('[') && segment.endsWith(']')) return `:${segment.slice(1, -1)}`
        return segment
    }).join('/')
}

/**
 * Resolves an import specifier to a `.vue` file: the `@/` and `~/` source aliases, and paths
 * relative to the importing file.
 *
 * @param spec the specifier
 * @param fromFile the importing file
 * @param srcDir the source directory
 * @returns the absolute path, or null for a package
 */
export function resolveComponent(spec, fromFile, srcDir) {
    const withExtension = path => (path.endsWith('.vue') ? path : `${path}.vue`)
    if (spec.startsWith('@/') || spec.startsWith('~/')) return withExtension(join(srcDir, spec.slice(2)))
    if (spec.startsWith('./') || spec.startsWith('../')) return withExtension(resolve(dirname(fromFile), spec))
    return null
}

const componentIndexes = new Map()

/**
 * A component found by its file name anywhere under the source directory, which is how Nuxt
 * resolves a component nobody imported. A name two files share resolves to nothing, so a guess is
 * never followed.
 *
 * @param srcDir the source directory
 * @param name the component's name
 * @returns the absolute path, or null
 */
export function componentByName(srcDir, name) {
    if (!componentIndexes.has(srcDir)) {
        const index = new Map()
        for (const file of vueFilesUnder(srcDir)) {
            const base = basename(file, '.vue')
            index.set(base, index.has(base) ? null : file)
        }
        componentIndexes.set(srcDir, index)
    }
    return componentIndexes.get(srcDir).get(name) ?? null
}

/**
 * Every `.vue` file below a directory.
 *
 * @param dir the directory
 * @returns absolute paths
 */
function vueFilesUnder(dir) {
    if (!existsSync(dir)) return []
    return readdirSync(dir, {withFileTypes: true}).flatMap(entry => {
        const full = join(dir, entry.name)
        if (entry.isDirectory()) return vueFilesUnder(full)
        return entry.name.endsWith('.vue') ? [full] : []
    })
}
