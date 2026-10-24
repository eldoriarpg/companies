/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
import {attributeOf, boundExpression, callOf, elementsOf, findAll, pageOf, readComponent} from './sfc.mjs'

/**
 * Finds pages whose address names one particular thing while their title names only the kind.
 *
 * <p>An address with a parameter in it points at one catalogue, one ticket, one member, and the
 * title of such a page should say which: `ViewContent` writes it into the shared header, the
 * browser tab and every bookmark. Titled after the kind, a dozen open tabs all read
 * "Catalogue" and none of them says which catalogue.
 *
 * <p>What counts as naming the thing is that the title depends on something the page loaded. A
 * title that is nothing but a translated constant cannot; one falling back to that constant while
 * the record is on its way still reads the record and passes. Only a required parameter counts: an
 * optional one marks a page reached with and without it. A view that writes its own document title
 * through `useHead`, where heading and tab are different strings, is left alone.
 *
 * <p>It runs on the page file, which is what knows the address, and reads the view it renders.
 */

/**
 * The first `ViewContent` of a template and the title handed to it.
 *
 * @param template the root template element
 * @returns `{element, expression, written}` where `expression` is the bound expression node or the
 *          written text, or null where the template hands no title
 */
export function viewContentTitle(template) {
    const element = elementsOf(template).find(candidate => candidate.rawName === 'ViewContent')
    if (!element) return null
    const title = attributeOf(element, 'title')
    if (!title) return null
    if (!title.bound) return {element, expression: title.attribute.value?.value ?? '', written: true}
    const expression = boundExpression(title.attribute)
    return expression ? {element, expression, written: false} : null
}

/**
 * Whether a title can only ever read the same.
 *
 * <p>A title written out is a constant by construction. A bound one is a constant when every
 * name in it is the `t` of a translation call with nothing but a string in it: whatever a page
 * loaded reaches the title as a name, so a name left over says the title can change.
 *
 * @param title the title as {@link viewContentTitle} found it
 * @returns true for a constant title
 */
export function isConstantTitle(title) {
    if (title.written) return true
    const translations = new Set(findAll(title.expression, node => node.type === 'CallExpression'
        && node.callee.type === 'Identifier' && node.callee.name === 't'
        && node.arguments.length === 1 && node.arguments[0].type === 'Literal'
        && typeof node.arguments[0].value === 'string').map(call => call.callee))
    const names = findAll(title.expression, node => node.type === 'Identifier' || node.type === 'ThisExpression')
    return names.every(name => translations.has(name))
}

/**
 * Whether a view names its own tab through `useHead`.
 *
 * @param program the view's script program
 * @returns true where it does
 */
export function writesItsOwnHead(program) {
    return callOf(program, 'useHead') !== null
}

/**
 * Whether a route path carries a required parameter, which makes it point at one thing.
 *
 * @param path the route path
 * @returns true for such a path
 */
function namesOneThing(path) {
    return path.split('/').some(segment => segment.startsWith(':') && !segment.endsWith('?'))
}

export default {
    meta: {
        type: 'problem',
        docs: {description: 'A page whose address points at one thing is titled after that thing.'},
        schema: [],
        messages: {
            kind: '{{path}} points at one thing, {{shown}} names a kind. Name the thing the address points at, falling back to the constant while it loads.',
        },
    },
    create(context) {
        return {
            'Program:exit'(program) {
                const page = pageOf(program, context.filename)
                if (!page || page.redirect || !page.view || !namesOneThing(page.path)) return
                const view = readComponent(page.view)
                if (!view || writesItsOwnHead(view.script)) return
                const title = viewContentTitle(view.template)
                if (!title || !isConstantTitle(title)) return
                const shown = title.written
                    ? `"${title.expression}"`
                    : `:title="${view.text.slice(...title.expression.range)}"`
                context.report({node: page.viewImport, messageId: 'kind', data: {path: page.path, shown}})
            },
        }
    },
}
