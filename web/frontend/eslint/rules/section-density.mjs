/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
import {childElements, walkElements} from './vue-template.mjs'

/** The elements that open a section: plain containers, semantic ones, and the container components. */
const SECTION_TAGS = new Set([
    'div', 'section', 'article', 'header', 'footer', 'main', 'nav', 'aside',
    'PanelBox',
])

/**
 * Whether an element opens a section with a body of its own. An element closing itself, or a void
 * one, holds nothing and is not counted.
 *
 * @param element a template element
 * @returns true for a section with content
 */
function isSection(element) {
    if (!element.endTag) return false
    return SECTION_TAGS.has(element.rawName) || SECTION_TAGS.has(element.rawName.toLowerCase())
}

/**
 * An element near the top of a view's template stacking so many sections directly below it that
 * each of them wants a component of its own.
 *
 * <p>Only the second and third level are looked at, the root template being the first: that is
 * where a page lays out its parts, while deeper stacks are the inside of one part.
 */
export default {
    meta: {
        type: 'suggestion',
        docs: {description: 'Limit how many sections one element near the top of a view may stack'},
        schema: [{type: 'object', properties: {max: {type: 'integer', minimum: 1}}, additionalProperties: false}],
        messages: {
            dense: '<{{tag}}> at level {{depth}} has {{count}} direct section children (div / PanelBox / section / …) - each section likely wants its own component.',
        },
    },
    create(context) {
        const max = context.options[0]?.max ?? 6
        return {
            Program() {
                const template = context.sourceCode.ast.templateBody
                if (!template) return
                walkElements(template, (element, depth) => {
                    if (depth > 3) return false
                    if (depth < 2 || !element.endTag) return
                    const count = childElements(element).filter(isSection).length
                    if (count < max) return
                    context.report({
                        loc: element.startTag.loc,
                        messageId: 'dense',
                        data: {tag: element.rawName, depth, count},
                    })
                })
            },
        }
    },
}
