/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {existsSync} from 'node:fs'
import {
    attributeOf,
    boundExpression,
    callOf,
    childElements,
    componentByName,
    elementsOf,
    findAll,
    pageOf,
    readComponent,
    resolveComponent,
    sourceDirOf,
} from './sfc.mjs'
import {WRAPPERS, firstViewContent} from './view-content.mjs'

/**
 * Every page under a titled layout has a title to show.
 *
 * <p>`ViewContent` heads the page and names the browser tab from its `title` prop, so the view a
 * page renders has to reach a titled `ViewContent`, itself or through the component
 * it hands its whole template to. Following that delegation, a `ViewContent` whose title is one of
 * the component's own props only counts as titled when the caller passes the prop; a title made
 * of anything else counts on its own.
 *
 * <p>A page that only forwards the reader somewhere else renders nothing to title, and a
 * `ViewContent` would flash a header for the tick before the redirect lands, so it is left alone.
 * Delegation is followed six components deep, and a component named but not imported is found by
 * its file name only where that name is unique, so a guess is never followed.
 */

/** The layouts whose pages are titled through `ViewContent`, unless the rule is told others. */
const TITLED_LAYOUTS = ['default']

const MAX_DELEGATION_DEPTH = 6

/** Plain elements that arrange or space content rather than carry it. */
const STRUCTURAL_HTML = new Set(['div', 'section', 'main', 'span', 'br', 'hr'])

/**
 * Whether a view does nothing but send the router elsewhere: it calls `router.replace` and its
 * template holds no text, no value and no element that carries content.
 *
 * @param view the parsed view
 * @returns true for a redirect stub
 */
function isRedirectStub(view) {
    const replaces = [view.script, view.template].some(root => findAll(root, node => node.type === 'CallExpression'
        && node.callee.type === 'MemberExpression' && node.callee.object.type === 'Identifier'
        && node.callee.object.name === 'router' && node.callee.property.name === 'replace').length > 0)
    if (!replaces) return false
    const shows = findAll(view.template, node => node.type === 'VExpressionContainer' && node.parent?.type === 'VElement'
        || node.type === 'VText' && node.value.trim() !== '')
    if (shows.length > 0) return false
    return elementsOf(view.template).every(element => WRAPPERS.has(element.rawName) || STRUCTURAL_HTML.has(element.rawName))
}

/**
 * The names an expression reads, member names and object keys left out.
 *
 * @param node the expression
 * @param names where the names are collected
 * @returns the collected names
 */
function namesRead(node, names = new Set()) {
    if (!node || typeof node.type !== 'string') return names
    if (node.type === 'Identifier') return names.add(node.name)
    for (const [key, value] of Object.entries(node)) {
        if (key === 'parent' || key === 'loc' || key === 'range') continue
        if (node.type === 'MemberExpression' && key === 'property' && !node.computed) continue
        if (node.type === 'Property' && key === 'key' && !node.computed && !node.shorthand) continue
        if (Array.isArray(value)) value.forEach(child => namesRead(child, names))
        else if (value && typeof value.type === 'string') namesRead(value, names)
    }
    return names
}

/**
 * The props a component declares through `defineProps<{…}>()`.
 *
 * @param program the component's script program
 * @returns their names
 */
function declaredProps(program) {
    const type = callOf(program, 'defineProps')?.typeArguments?.params[0]
    if (type?.type !== 'TSTypeLiteral') return new Set()
    return new Set(type.members
        .filter(member => member.type === 'TSPropertySignature' && member.key.type === 'Identifier')
        .map(member => member.key.name))
}

/**
 * The props a caller passes on a component's tag. An object `v-bind` passes an unknown set, and
 * every prop is then taken as passed.
 *
 * @param element the component's tag
 * @returns `{names, spread}`
 */
function passedProps(element) {
    const names = new Set()
    let spread = false
    for (const attribute of element.startTag.attributes) {
        if (!attribute.directive) {
            names.add(camelize(attribute.key.name))
        } else if (attribute.key.name.name === 'bind') {
            if (attribute.key.argument?.type === 'VIdentifier') names.add(camelize(attribute.key.argument.name))
            else if (!attribute.key.argument) spread = true
        }
    }
    return {names, spread}
}

/**
 * A kebab-cased attribute name as the camel-cased prop it sets.
 *
 * @param name the attribute name
 * @returns the prop name
 */
function camelize(name) {
    return name.split('-').map((part, index) => (index === 0 ? part : part.charAt(0).toUpperCase() + part.slice(1))).join('')
}

/**
 * The components a file imports by default, by the name it gives them.
 *
 * @param program the file's script program
 * @param file the file
 * @param srcDir the source directory
 * @returns name to absolute path, for the imports that are existing `.vue` files
 */
function componentImports(program, file, srcDir) {
    const imports = new Map()
    for (const statement of program.body) {
        if (statement.type !== 'ImportDeclaration') continue
        const specifier = statement.specifiers.find(candidate => candidate.type === 'ImportDefaultSpecifier')
        const target = specifier && resolveComponent(statement.source.value, file, srcDir)
        if (target && existsSync(target)) imports.set(specifier.local.name, target)
    }
    return imports
}

/**
 * Whether a view reaches a titled `ViewContent`, itself or through the components it delegates to.
 *
 * @param file the component file
 * @param passed the props its caller passes
 * @param srcDir the source directory
 * @param depth how many components deep the search already is
 * @param seen the files already visited
 * @returns `{found}`, and with a `ViewContent` found also `titled`, `delegate` and `missing`
 */
function resolveViewContent(file, passed, srcDir, depth, seen) {
    if (depth > MAX_DELEGATION_DEPTH || seen.has(file)) return {found: false}
    seen.add(file)
    const component = readComponent(file)
    if (!component?.template) return {found: false}

    const viewContent = firstViewContent(component.template)
    if (viewContent) {
        const title = attributeOf(viewContent, 'title')
        if (!title) return {found: true, titled: false, missing: []}
        const expression = title.bound ? boundExpression(title.attribute) : null
        if (!expression) return {found: true, titled: true, missing: []}
        const own = declaredProps(component.script)
        const missing = [...namesRead(expression)].filter(name => own.has(name) && !passed.names.has(name))
        return {found: true, titled: passed.spread || missing.length === 0, missing}
    }

    for (const element of childElements(component.template)) {
        if (WRAPPERS.has(element.rawName) || !/^[A-Z]/.test(element.rawName)) continue
        const target = componentImports(component.script, file, srcDir).get(element.rawName)
            ?? componentByName(srcDir, element.rawName)
        if (!target) continue
        const result = resolveViewContent(target, passedProps(element), srcDir, depth + 1, seen)
        if (result.found) return {...result, delegate: element.rawName}
    }
    return {found: false}
}

export default {
    meta: {
        type: 'problem',
        docs: {description: 'A page under header chrome renders a view that reaches a titled ViewContent.'},
        schema: [{
            type: 'object',
            properties: {layouts: {type: 'array', items: {type: 'string'}}},
            additionalProperties: false,
        }],
        messages: {
            noViewContent: 'Route "{{name}}" ({{path}}) does not wrap its body in <ViewContent>. The header title cannot be set.',
            untitled: 'Route "{{name}}" ({{path}}) delegates to <{{delegate}}>, whose <ViewContent> title comes from the prop(s) {{missing}} that this view does not pass. The header title cannot be set.',
        },
    },
    create(context) {
        const layouts = new Set(context.options[0]?.layouts ?? TITLED_LAYOUTS)
        return {
            'Program:exit'(program) {
                const page = pageOf(program, context.filename)
                if (!page || page.redirect || !page.view || !existsSync(page.view)) return
                if (!layouts.has(page.layout)) return
                const view = readComponent(page.view)
                if (!view?.template || isRedirectStub(view)) return

                const srcDir = sourceDirOf(context.filename)
                const result = resolveViewContent(page.view, {names: new Set(), spread: false}, srcDir, 0, new Set())
                const data = {name: page.name, path: page.path}
                if (!result.found) {
                    context.report({node: page.viewImport, messageId: 'noViewContent', data})
                } else if (!result.titled && result.delegate) {
                    const missing = result.missing.map(prop => `\`${prop}\``).join(', ')
                    context.report({node: page.viewImport, messageId: 'untitled', data: {...data, delegate: result.delegate, missing}})
                }
            },
        }
    },
}
