/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
import {walkElements} from './vue-template.mjs'

/**
 * The wrappers that define a page's shell. Its whole body lives inside them, so their size says
 * nothing about a block that wants extracting.
 */
const ROOT_WRAPPERS = new Set([
    'template', 'ViewContent', 'NuxtPage', 'NuxtLayout', 'NuxtRoot', 'Modal', 'SidePanel',
])

/**
 * A single template block of a view spanning so many lines that its inner content wants a
 * component of its own.
 *
 * <p>The root template and the elements directly below it are the page itself, and the shell
 * wrappers hold everything, so neither is measured. A block is measured from the line its opening
 * tag starts on to the line its closing tag starts on; an element that closes itself has no body.
 */
export default {
    meta: {
        type: 'suggestion',
        docs: {description: 'Limit how many lines one template block of a view may span'},
        schema: [{type: 'object', properties: {max: {type: 'integer', minimum: 1}}, additionalProperties: false}],
        messages: {
            tooLong: '<{{tag}}> block spans {{span}} lines (>= {{max}}). Extract to a component.',
        },
    },
    create(context) {
        const max = context.options[0]?.max ?? 50
        return {
            Program() {
                const template = context.sourceCode.ast.templateBody
                if (!template) return
                walkElements(template, (element, depth) => {
                    if (!element.endTag || depth <= 2 || ROOT_WRAPPERS.has(element.rawName)) return
                    const span = element.endTag.loc.start.line - element.loc.start.line + 1
                    if (span < max) return
                    context.report({
                        loc: element.startTag.loc,
                        messageId: 'tooLong',
                        data: {tag: element.rawName, span, max},
                    })
                })
            },
        }
    },
}
