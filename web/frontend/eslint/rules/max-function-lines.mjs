/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {basename, sep} from 'node:path'

/**
 * Functions that span too many lines, the composables excepted.
 *
 * <p>The same measure as ESLint's own `max-lines-per-function`, first line to last, with one
 * exception it cannot express: a composable. `useSomething` in a file of its own, or in the
 * composables folder, is a wrapper whose body is the whole feature it provides, a scope for its
 * state and the functions it returns, so its length says how much it offers rather than that one
 * function does too much. The functions inside it are measured like any other.
 */
export default {
    meta: {
        type: 'suggestion',
        docs: {description: 'Enforce a maximum number of lines per function, composable wrappers excepted'},
        schema: [{
            type: 'object',
            properties: {max: {type: 'integer', minimum: 1}},
            additionalProperties: false,
        }],
        messages: {
            tooLong: "Function '{{name}}' spans {{lines}} lines (at most {{max}}). Extract helpers.",
        },
    },
    create(context) {
        const max = context.options[0]?.max ?? 79

        /**
         * Reports a function longer than the maximum unless it is a composable wrapper.
         *
         * @param node the function
         */
        function check(node) {
            const lines = node.loc.end.line - node.loc.start.line + 1
            if (lines <= max) return
            const name = functionName(node)
            if (isComposableWrapper(context.filename, name)) return
            context.report({node, messageId: 'tooLong', data: {name: name ?? 'anonymous', lines, max}})
        }

        return {
            FunctionDeclaration: check,
            FunctionExpression: check,
            ArrowFunctionExpression: check,
        }
    },
}

/**
 * The name a function is known by: its own, or that of the variable or property it is assigned to.
 *
 * @param node the function
 * @returns the name, or null for an anonymous callback
 */
function functionName(node) {
    if (node.id) return node.id.name
    const parent = node.parent
    if (parent?.type === 'VariableDeclarator' && parent.id.type === 'Identifier') return parent.id.name
    if (parent?.type === 'Property' && parent.key.type === 'Identifier') return parent.key.name
    return null
}

/**
 * Whether a function is the wrapper of a composable: named `use` and a capital, in the composables
 * folder or in a file named after a composable.
 *
 * @param filename the file the function is in
 * @param name the function's name
 * @returns true for a composable wrapper
 */
function isComposableWrapper(filename, name) {
    if (!name || !/^use[A-Z]/.test(name)) return false
    if (filename.includes(`${sep}composables${sep}`)) return true
    return /^use[A-Z]\w*\.(?:ts|vue)$/.test(basename(filename))
}
