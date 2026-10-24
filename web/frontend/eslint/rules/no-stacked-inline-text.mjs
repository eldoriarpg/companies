/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {basename, join} from 'node:path'
import {
    childElements, directive, isAlternative, parseVueFile, SRC, staticAttribute, staticClasses, vueFilesIn, walkElements,
} from './vue-template.mjs'

const TYPOGRAPHY_DIR = join(SRC, 'components', 'typography')
const DISPLAY_CLASS = /(^|[\s:])(block|flow-root|flex|grid|table|contents)(\s|$)/
const SIDEWAYS_SPACING_CLASS = /(^|[\s:])[mp][lsx]-/
const LAYOUT_CLASS = /(^|[\s:])(inline-)?(flex|grid)(\s|$)/
const SIDEWAYS_STACK_CLASS = /(^|[\s:])space-x-/

let typography = null

/**
 * The typography components that render an inline `span` carrying slotted text, with the property
 * that can turn one into something else, read once from `src/components/typography`.
 *
 * <p>A component qualifies when its template holds a slot and its root is a `span`, or a dynamic
 * `component` whose `:is` names a property defaulting to `'span'`.
 *
 * @returns the component table, by name
 */
function inlineTypography() {
    if (typography) return typography
    typography = {}
    for (const file of vueFilesIn(TYPOGRAPHY_DIR)) {
        const {ast} = parseVueFile(file)
        const entry = inlineEntry(ast)
        if (entry) typography[basename(file, '.vue')] = entry
    }
    return typography
}

/**
 * How one typography component renders, or nothing where it is not inline text.
 *
 * @param ast the parsed component
 * @returns `{tagProperty}` for an inline component
 */
function inlineEntry(ast) {
    const template = ast.templateBody
    if (!template || !carriesSlot(template)) return null
    const root = childElements(template)[0]
    if (!root) return null
    if (root.rawName === 'span') return {tagProperty: null}
    const bound = directive(root, 'bind', 'is')
    const expression = bound?.value?.expression
    if (root.rawName !== 'component' || expression?.type !== 'Identifier') return null
    return defaultsToSpan(ast, expression.name) ? {tagProperty: expression.name} : null
}

/**
 * Whether a template renders a slot anywhere.
 *
 * @param template the template root
 * @returns true when a `slot` element is present
 */
function carriesSlot(template) {
    let found = false
    walkElements(template, element => {
        if (element.rawName === 'slot') found = true
    })
    return found
}

/**
 * Whether the script gives a property the literal default `'span'`.
 *
 * @param ast the parsed component
 * @param name the property name
 * @returns true when some object property of that name is the string `span`
 */
function defaultsToSpan(ast, name) {
    const pending = [...ast.body]
    while (pending.length > 0) {
        const node = pending.pop()
        if (!node || typeof node.type !== 'string') continue
        if (node.type === 'Property' && (node.key.name ?? node.key.value) === name
            && node.value.type === 'Literal' && node.value.value === 'span') return true
        for (const [key, value] of Object.entries(node)) {
            if (key === 'parent') continue
            if (Array.isArray(value)) pending.push(...value)
            else if (value && typeof value === 'object') pending.push(value)
        }
    }
    return false
}

/**
 * Whether a usage of a typography component reaches the page as an inline box.
 *
 * @param element the usage
 * @param components the inline component table
 * @returns true when it renders inline
 */
function rendersInline(element, components) {
    const component = components[element.rawName]
    if (!component) return false
    if (DISPLAY_CLASS.test(staticClasses(element))) return false
    if (!component.tagProperty) return true
    if (directive(element, 'bind', component.tagProperty) || directive(element, 'on', component.tagProperty)) return false
    const literal = staticAttribute(element, component.tagProperty)?.value?.value
    return literal === undefined || literal === 'span'
}

/**
 * Whether a parent places its children itself, so an inline child is blockified or separated.
 *
 * @param element the parent
 * @returns true for a flex or grid container and a sideways stack
 */
function laysOutChildren(element) {
    const classes = staticClasses(element)
    return LAYOUT_CLASS.test(classes) || SIDEWAYS_STACK_CLASS.test(classes)
}

/**
 * Whether only a line break stands between two siblings: nothing but whitespace text holding a
 * newline, and no comment, which is exactly the gap Vue removes.
 *
 * @param between the nodes between the two elements
 * @param comments the template's comments
 * @param from where the gap starts
 * @param to where the gap ends
 * @returns true for a bare line break
 */
function onlyLineBreak(between, comments, from, to) {
    if (between.some(node => node.type !== 'VText' || node.value.trim() !== '')) return false
    if (comments.some(comment => comment.range[0] >= from && comment.range[1] <= to)) return false
    return between.some(node => node.value.includes('\n'))
}

/**
 * Two inline typography components written one under the other, which Vue glues into one run-on
 * line by dropping the line break between them.
 *
 * <p>Deliberately narrow: only immediate siblings with nothing but a line break between them, in a
 * parent that does not lay out its children, where neither carries a display class, the second is
 * not the other branch of a condition and brings no sideways spacing of its own.
 */
export default {
    meta: {
        type: 'problem',
        docs: {description: 'Disallow two inline typography components stacked with only a line break between them'},
        schema: [{
            type: 'object',
            properties: {components: {type: 'object'}},
            additionalProperties: false,
        }],
        messages: {
            stacked: '<{{current}}> follows <{{previous}}> as an inline sibling, so their texts run together. Give both of them tag="p".',
        },
    },
    create(context) {
        return {
            Program() {
                const template = context.sourceCode.ast.templateBody
                if (!template) return
                const components = context.options[0]?.components ?? inlineTypography()
                const comments = template.comments ?? []
                walkElements(template, parent => {
                    if (laysOutChildren(parent)) return
                    let previous = null
                    let between = []
                    for (const child of parent.children) {
                        if (child.type !== 'VElement') {
                            between.push(child)
                            continue
                        }
                        if (previous
                            && onlyLineBreak(between, comments, previous.range[1], child.range[0])
                            && !isAlternative(child)
                            && !SIDEWAYS_SPACING_CLASS.test(staticClasses(child))
                            && rendersInline(previous, components)
                            && rendersInline(child, components)) {
                            context.report({
                                loc: child.startTag.loc,
                                messageId: 'stacked',
                                data: {current: child.rawName, previous: previous.rawName},
                            })
                        }
                        previous = child
                        between = []
                    }
                })
            },
        }
    },
}
