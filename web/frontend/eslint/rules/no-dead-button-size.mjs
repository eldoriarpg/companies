/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
import {basename, join} from 'node:path'
import {parseVueFile, SRC, staticAttribute, vueFilesIn, walkElements} from './vue-template.mjs'

const BUTTON_DIR = join(SRC, 'components', 'button')
const BUTTON = /^\w*Button$/

let sizeAware = null

/**
 * The button components that declare a `size` prop, read once from `src/components/button`.
 *
 * @returns their names
 */
function sizeAwareButtons() {
    if (sizeAware) return sizeAware
    sizeAware = new Set(vueFilesIn(BUTTON_DIR)
        .filter(file => declaresSize(parseVueFile(file).ast))
        .map(file => basename(file, '.vue')))
    return sizeAware
}

/**
 * Whether a component's `defineProps` type names a `size` prop.
 *
 * @param ast the parsed component
 * @returns true when it does
 */
function declaresSize(ast) {
    const pending = [...ast.body]
    while (pending.length > 0) {
        const node = pending.pop()
        if (!node || typeof node.type !== 'string') continue
        if (node.type === 'CallExpression' && node.callee.name === 'defineProps') {
            const members = node.typeArguments?.params[0]?.members ?? []
            if (members.some(member => member.key?.name === 'size')) return true
        }
        for (const [key, value] of Object.entries(node)) {
            if (key === 'parent') continue
            if (Array.isArray(value)) pending.push(...value)
            else if (value && typeof value === 'object') pending.push(value)
        }
    }
    return false
}

/**
 * A `size` attribute on a button component that declares no such prop.
 *
 * <p>Vue passes an undeclared attribute through to the root element, where `size` means nothing
 * to a button and silently becomes a dead DOM attribute. The buttons that have a smaller form take
 * `compact` instead.
 */
export default {
    meta: {
        type: 'problem',
        docs: {description: 'Disallow size on button components that declare no size prop'},
        schema: [{
            type: 'object',
            properties: {sizeAware: {type: 'array', items: {type: 'string'}}},
            additionalProperties: false,
        }],
        messages: {
            dead: '<{{tag}}> does not declare a size prop - size="…" silently becomes a dead DOM attribute. Use the compact prop.',
        },
    },
    create(context) {
        return {
            Program() {
                const template = context.sourceCode.ast.templateBody
                if (!template) return
                const configured = context.options[0]?.sizeAware
                const aware = configured ? new Set(configured) : sizeAwareButtons()
                walkElements(template, element => {
                    if (!BUTTON.test(element.rawName) || aware.has(element.rawName)) return
                    if (!staticAttribute(element, 'size')) return
                    context.report({loc: element.startTag.loc, messageId: 'dead', data: {tag: element.rawName}})
                })
            },
        }
    },
}
