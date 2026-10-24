/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {basename} from 'node:path'
import {attributeOf, elementsOf} from './sfc.mjs'
import {WRAPPERS, firstViewContent} from './view-content.mjs'

/**
 * A view that hosts its own `ViewContent` titles the page through it and nowhere else.
 *
 * <p>The header of every page with header chrome is written by `usePageHeader()`, which
 * `ViewContent` fills from its `title` prop, and the layouts forward it to their header. So the
 * prop is required, and a view does not open with a `PageHeader` or `SectionHeader` of its own:
 * that would be a second title above the first. A section header further down, introducing a part
 * of the page, is fine; only the first thing with content is looked at.
 *
 * <p>Components rendered inside another view's `ViewContent`, and the views that host a sidebar
 * layout for the pages below them, are not what this is about, and neither carries a
 * `ViewContent` of its own.
 */

/** Views that host their own sidebar layout, whose child routes carry the `ViewContent`; Companies has none. */
export const LAYOUT_ROOTS = new Set([])

/** Plain elements that only arrange what is in them. */
const STRUCTURE = new Set(['div', 'section', 'main'])


/**
 * The first element with content of its own, when it is a page or section header.
 *
 * @param template the root template element
 * @returns the header element, or undefined
 */
function topOfPageTitle(template) {
    const first = elementsOf(template).find(element => !WRAPPERS.has(element.rawName) && !STRUCTURE.has(element.rawName))
    return first && (first.rawName === 'PageHeader' || first.rawName === 'SectionHeader') ? first : undefined
}

export default {
    meta: {
        type: 'problem',
        docs: {description: 'A view titles its page through the title prop of its ViewContent.'},
        schema: [],
        messages: {
            missingTitle: 'ViewContent is missing the required `title` prop. Add `:title="..."` (e.g. from an i18n key).',
            ownTitle: 'View renders its own top-of-page title (PageHeader / SectionHeader). Remove it: the header is set through the ViewContent title prop.',
        },
    },
    create(context) {
        return {
            'Program:exit'(program) {
                if (LAYOUT_ROOTS.has(basename(context.filename))) return
                const template = program.templateBody
                const viewContent = firstViewContent(template)
                if (!viewContent) return
                if (!attributeOf(viewContent, 'title')) {
                    context.report({node: viewContent.startTag, messageId: 'missingTitle'})
                }
                const header = topOfPageTitle(template)
                if (header) context.report({node: header.startTag, messageId: 'ownTitle'})
            },
        }
    },
}
