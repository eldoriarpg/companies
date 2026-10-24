/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {anyClasses, isInsideComponentDir, staticAttribute, walkElements} from './vue-template.mjs'

const ROUNDED_FULL = /\brounded-full\b/
const HORIZONTAL_PADDING = /\bpx-/

/**
 * The raw elements that have a styled component, the directories allowed to use them raw because
 * that is where the styled components are built, and what to use instead.
 */
const RESTRICTED = [
    {tags: ['button'], home: ['button', 'input'], messageId: 'button'},
    {tags: ['input'], home: ['input'], messageId: 'input'},
    {tags: ['select'], home: ['input'], messageId: 'select'},
    {tags: ['textarea'], home: ['input'], messageId: 'textarea'},
    {tags: ['h1', 'h2', 'h3'], home: ['typography'], messageId: 'heading'},
]

/**
 * Whether a raw element is one the rule leaves alone: a file input has no styled counterpart, and
 * an input with no attribute at all is not the shape written in practice.
 *
 * @param element the raw element
 * @param tag its lower-case name
 * @returns true when it is exempt
 */
function isExempt(element, tag) {
    if (tag !== 'input') return false
    if (element.startTag.attributes.length === 0) return true
    return staticAttribute(element, 'type')?.value?.value?.toLowerCase() === 'file'
}

/**
 * Raw form controls, headings and badge spans written where a styled component exists.
 *
 * <p>Buttons, inputs, selects, text areas and headings come from `src/components/button`,
 * `src/components/input` and `src/components/typography`, which are the only places they are
 * written raw. A `span` that is round with horizontal padding is a text badge and comes from
 * `src/components/badge`.
 */
export default {
    meta: {
        type: 'suggestion',
        docs: {description: 'Require the styled components instead of raw controls, headings and badges'},
        schema: [],
        messages: {
            button: 'Raw <button> usage. Use a styled button component (PrimaryButton, SecondaryButton, IconButton, ChipButton, RowButton, LinkButton).',
            input: 'Raw <input> usage. Use SearchInput or TextField, or add a styled field to src/components/input.',
            select: 'Raw <select> usage. Use SelectInput.',
            textarea: 'Raw <textarea> usage. Add a styled text area to src/components/input and use it.',
            heading: 'Raw <{{tag}}> usage. Use PageHeader or SectionHeader.',
            badge: '<span> with rounded-full + padding is a round badge, and corners are square here: drop rounded-full or add a badge component to src/components/badge.',
        },
    },
    create(context) {
        const file = context.filename
        const restricted = RESTRICTED.filter(entry => !entry.home.some(dir => isInsideComponentDir(file, dir)))
        const badgesAllowed = isInsideComponentDir(file, 'badge')
        return {
            Program() {
                const template = context.sourceCode.ast.templateBody
                if (!template) return
                walkElements(template, element => {
                    const tag = element.rawName.toLowerCase()
                    const entry = restricted.find(candidate => candidate.tags.includes(tag))
                    if (entry && !isExempt(element, tag)) {
                        context.report({loc: element.startTag.loc, messageId: entry.messageId, data: {tag}})
                    }
                    if (!badgesAllowed && element.rawName === 'span') {
                        const classes = anyClasses(element, context.sourceCode)
                        if (ROUNDED_FULL.test(classes) && HORIZONTAL_PADDING.test(classes)) {
                            context.report({loc: element.startTag.loc, messageId: 'badge'})
                        }
                    }
                })
            },
        }
    },
}
