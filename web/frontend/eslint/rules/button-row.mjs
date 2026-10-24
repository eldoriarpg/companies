/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {anyClasses, directive, isAlternative, staticClasses, walkElements} from './vue-template.mjs'

const BUTTON = /^(Primary|Secondary|Error)Button$/
const RESPONSIVE = /\b(?:sm|md|lg|xl):|flex-col|\bgrid\b/
const SCREEN_READER_ONLY = /\bsr-only\b/

/**
 * Whether the element lays its children out in a row. The class has to be `flex` itself: `flex-1`
 * sizes a child inside somebody else's row and lays out nothing of its own.
 *
 * @param element a `div`
 * @returns true for a flex row
 */
function laysOutARow(element) {
    return staticClasses(element).split(/\s+/).includes('flex')
}

/**
 * Whether an element shows anything a reader could read: text or a mustache anywhere inside it,
 * leaving out words put there only for a screen reader.
 *
 * @param element the element to read
 * @param sourceCode the source, for bound classes
 * @returns true when it carries a visible label
 */
function carriesLabel(element, sourceCode) {
    return element.children.some(child => {
        if (child.type === 'VText') return child.value.trim() !== ''
        if (child.type === 'VExpressionContainer') return true
        if (child.type !== 'VElement') return false
        if (child.rawName === 'span' && SCREEN_READER_ONLY.test(anyClasses(child, sourceCode))) return false
        return carriesLabel(child, sourceCode)
    })
}

/**
 * How many labelled buttons a row lays out itself. Nested `div`s lay out their own buttons, and
 * those handed to a `ButtonRow` are already settled, so neither is looked into. A row whose buttons
 * are repeated by `v-for` is a list rather than a row and counts none; a button standing as the
 * alternative of another never shares the line with it and is not counted.
 *
 * @param row the flex row
 * @param sourceCode the source, for bound classes
 * @returns the number of labelled buttons
 */
function labelledButtons(row, sourceCode) {
    let count = 0
    let repeated = false
    walkElements(row, element => {
        if (element === row) return
        if (element.rawName === 'div' || element.rawName === 'ButtonRow') return false
        if (!BUTTON.test(element.rawName)) return
        if (directive(element, 'for')) repeated = true
        else if (!isAlternative(element) && carriesLabel(element, sourceCode)) count++
        return false
    })
    return repeated ? 0 : count
}

/**
 * Two or more labelled buttons in a hand written flex row that says nothing about a narrow width.
 *
 * <p>On a phone such a row runs out of room and each button breaks its own label, leaving buttons
 * of two heights. `ButtonRow` stacks them below the breakpoint instead. A button holding only an
 * icon is a toolbar rather than an action row and has no label to break, so it is not counted.
 */
export default {
    meta: {
        type: 'suggestion',
        docs: {description: 'Require ButtonRow for two or more labelled buttons on one line'},
        schema: [],
        messages: {
            handWritten: '{{count}} buttons share a hand written row. Use ButtonRow so they stack on a phone.',
        },
    },
    create(context) {
        const sourceCode = context.sourceCode
        return {
            Program() {
                const template = sourceCode.ast.templateBody
                if (!template) return
                walkElements(template, element => {
                    if (element.rawName !== 'div' || !laysOutARow(element)) return
                    if (RESPONSIVE.test(anyClasses(element, sourceCode))) return
                    const count = labelledButtons(element, sourceCode)
                    if (count < 2) return
                    context.report({loc: element.startTag.loc, messageId: 'handWritten', data: {count}})
                })
            },
        }
    },
}
