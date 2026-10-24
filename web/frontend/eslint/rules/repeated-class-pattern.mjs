/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {parseVueFile, sourcePath, SRC, staticAttribute, vueFilesIn, walkElements} from './vue-template.mjs'

const MIN_CLASS_TEXT = 15
const MIN_CLASSES = 3

/** Classes that only lay things out, size or space them: a pattern of nothing else is not a component. */
const LAYOUT_PREFIX = /^(flex|inline-flex|grid|gap-|items-|justify-|self-|place-|col-span|row-span|sm:|md:|lg:|xl:)/
const STACKING_PREFIX = /^(space-[xy]-|order-|grow|shrink|basis-)/
const TEXT_SIZE = /^(text-xs|text-sm|text-base|text-lg|text-xl|text-center|text-right|text-left)$/
const BOX_PREFIX = /^(m[trblxy]?-|p[trblxy]?-|w-|h-|min-w-|max-w-|overflow-)/
const LAYOUT_WORD = /^(flex-wrap|flex-col|flex-row|flex-1|relative|absolute)$/

let projectCounts = null

/**
 * Whether a class only places or sizes an element.
 *
 * @param name one class
 * @returns true for a layout, spacing or text size utility
 */
function isLayout(name) {
    return LAYOUT_PREFIX.test(name) || STACKING_PREFIX.test(name) || TEXT_SIZE.test(name)
        || BOX_PREFIX.test(name) || LAYOUT_WORD.test(name)
}

/**
 * The pattern an element stands for: its tag with its sorted classes, where the element opens its
 * line and carries a plain class list on that line long and specific enough to be worth a name.
 *
 * @param element a template element
 * @param lines the file's lines
 * @returns the pattern, or null where the element is not one
 */
function patternOf(element, lines) {
    const start = element.loc.start
    if (lines[start.line - 1].slice(0, start.column).trim() !== '') return null
    const attribute = staticAttribute(element, 'class')
    const text = attribute?.value?.value
    if (!text || text.length < MIN_CLASS_TEXT || attribute.loc.start.line !== start.line) return null
    const classes = text.trim().split(/\s+/).filter(name => name.length > 0)
    if (classes.length < MIN_CLASSES || classes.every(isLayout)) return null
    return `<${element.rawName} class="${[...classes].sort().join(' ')}">`
}

/**
 * Every pattern-carrying element of a template.
 *
 * @param template the template root
 * @param lines the file's lines
 * @returns the elements with their patterns
 */
function patternsIn(template, lines) {
    const found = []
    walkElements(template, element => {
        const pattern = patternOf(element, lines)
        if (pattern) found.push({element, pattern})
    })
    return found
}

/**
 * Whether a file is one the rule reads: the components are where patterns are extracted to, so
 * repeating there is expected.
 *
 * @param file an absolute path
 * @returns true outside `src/components`
 */
function isScanned(file) {
    return !sourcePath(file).startsWith('components/')
}

/**
 * How often each pattern occurs across the views and pages, read once per process.
 *
 * @returns counts by pattern
 */
function countsAcrossProject() {
    if (projectCounts) return projectCounts
    projectCounts = new Map()
    for (const file of vueFilesIn(SRC).filter(isScanned)) {
        const {ast, lines} = parseVueFile(file)
        if (!ast.templateBody) continue
        for (const {pattern} of patternsIn(ast.templateBody, lines)) {
            projectCounts.set(pattern, (projectCounts.get(pattern) ?? 0) + 1)
        }
    }
    return projectCounts
}

/**
 * An element whose tag and class list repeat so often across the views that they want a component.
 *
 * <p>Counted over every template outside `src/components`, and reported at each occurrence once the
 * total reaches the threshold. With `scanProject` off only the file itself is counted, which is how
 * the rule is put to a test.
 */
export default {
    meta: {
        type: 'suggestion',
        docs: {description: 'Require a component for an element pattern repeated across the views'},
        schema: [{
            type: 'object',
            properties: {
                threshold: {type: 'integer', minimum: 2},
                scanProject: {type: 'boolean'},
            },
            additionalProperties: false,
        }],
        messages: {
            repeated: 'Repeated pattern ({{count}}x across the views): {{pattern}} - extract to a component.',
        },
    },
    create(context) {
        const threshold = context.options[0]?.threshold ?? 10
        const scanProject = context.options[0]?.scanProject ?? true
        const file = context.filename
        return {
            Program() {
                const template = context.sourceCode.ast.templateBody
                if (!template || !isScanned(file)) return
                const found = patternsIn(template, context.sourceCode.lines)
                const local = new Map()
                for (const {pattern} of found) local.set(pattern, (local.get(pattern) ?? 0) + 1)
                const counts = scanProject ? countsAcrossProject() : local
                for (const {element, pattern} of found) {
                    const count = Math.max(counts.get(pattern) ?? 0, local.get(pattern))
                    if (count < threshold) continue
                    context.report({loc: element.startTag.loc, messageId: 'repeated', data: {count, pattern}})
                }
            },
        }
    },
}
